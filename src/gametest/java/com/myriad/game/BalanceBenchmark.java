package com.myriad.game;

import com.google.gson.GsonBuilder;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.DamageCalc;
import com.myriad.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.myriad.player.LevelSystem;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** Prototype-only observation in an isolated Fabric test world. Never included in the mod JAR. */
final class BalanceBenchmark {
    private static Map<String, Object> snapshot(KehengChecks.Fixture fixture) {
        var rules = new TreeMap<String, Object>();
        var roles = new TreeMap<String, Object>();
        for (String id : List.of("luoxia", "yingxiao", "keheng", "qingluo")) {
            var role = CharacterType.byId(id);
            var skills = new ArrayList<Object>();
            for (var s : FirstGroupSkills.candidates(id)) {
                var node = new TreeMap<String, Object>();
                node.put("id", s.getId());
                node.put("name", s.displayName());
                node.put("index", s.index());
                node.put("java", s.getClass().getName());
                node.put("damage", s.damageFormulas());
                node.put("support", s.supportAmounts());
                node.put("mana", IntStream.rangeClosed(1, s.maxRank()).mapToDouble(s::manaCost).toArray());
                node.put("cooldown", IntStream.rangeClosed(1, s.maxRank()).map(s::getCooldownTicks).toArray());
                node.put("prepare", s.commitWindupTicks());
                node.put("recovery", s.inputRecoveryTicks());
                skills.add(node);
            }
            roles.put(id, Map.of("skills", skills, "attack", FirstGroupGrowth.baselinePhysicalAttack(role),
                    "hp", IntStream.rangeClosed(1, 20).mapToDouble(l -> FirstGroupGrowth.health(id, l)).toArray(),
                    "mp", IntStream.rangeClosed(1, 20).mapToDouble(l -> FirstGroupGrowth.maxMana(role,l)).toArray()));
        }
        rules.put("roles", roles);
        rules.put("roster", new TreeSet<>(FirstGroupSkills.ROLES));
        rules.put("registeredSkills", FirstGroupSkills.ROLES.stream().mapToInt(r -> FirstGroupSkills.candidates(r).size()).sum());
        rules.put("price", IntStream.range(0, 24).mapToLong(FirstGroupGrowth::price).toArray());
        rules.put("xpNext", IntStream.range(1, 20).map(LevelSystem::xpToNext).toArray());
        rules.put("passiveRank", IntStream.rangeClosed(1, 20).map(FirstGroupSkills::passiveRank).toArray());
        rules.put("power", FirstGroupGrowth.baselineSpellPower());
        rules.put("basicDraw", LuoxiaBow.NORMAL_WORK);
        rules.put("quickDraw", IntStream.rangeClosed(1, 5).mapToDouble(LuoxiaBow::minimumWork).toArray());
        rules.put("mageBasic", MagicBullet.rawDamage(CharacterType.QINGLUO, FirstGroupGrowth.baselineSpellPower()));
        rules.put("mageRatio", MagicBullet.spellPowerRatio(CharacterType.QINGLUO));
        rules.put("cdrCap", ModAttributes.MAX_COOLDOWN_REDUCTION);
        var wallet = new PlayerData();
        wallet.grantOpeningPurse();
        rules.put("opening", wallet.getGold());
        rules.put("goldScale", GlobalBalance.GOLD_INCOME_SCALE);
        var tempo = MatchTempo.CHERRY_V1;
        rules.put("firstWave", tempo.firstWaveTicks() / 20);
        var camps = new TreeMap<String, Object>();
        for (var type : List.of(MonsterType.CHERRY_FROST_HARE, MonsterType.CHERRY_JADE_BEAST, MonsterType.CHERRY_RIFT_CRAB)) {
            camps.put(type.name(), Map.of("xp", type.getXp(), "gold", GlobalBalance.goldIncome(type.getGold()),
                    "first", tempo.firstSpawnTicks(type) / 20, "respawn", tempo.respawnSeconds(type)));
        }
        rules.put("camps", camps);
        rules.put("towerScale", IntStream.rangeClosed(0, 20).mapToDouble(m -> MatchBalance.towerDamageTakenScale(m*1200L)).toArray());
        var p = fixture.p;
        var d = PlayerDataProvider.get(p);
        FirstGroupGrowth.initialize(p, d);
        var watched = List.of(ModAttributes.PHYSICAL_ATTACK, ModAttributes.SPELL_POWER, ModAttributes.ATTACK_SPEED,
                ModAttributes.PHYSICAL_DEFENSE, ModAttributes.MAGIC_RESISTANCE, ModAttributes.COOLDOWN_REDUCTION);
        rules.put("baseAttributes", watched.stream().mapToDouble(p::getAttributeValue).toArray());
        for (String key : FirstGroupGrowth.ATTRIBUTES) d.setFirstGroupAttribute(key, 1);
        FirstGroupGrowth.apply(p, d);
        rules.put("oneEach", watched.stream().mapToDouble(p::getAttributeValue).toArray());
        rules.put("manaRegen", p.getAttributeValue(ModAttributes.MANA_REGEN));
        for (String key : FirstGroupGrowth.ATTRIBUTES) d.setFirstGroupAttribute(key, 0);
        FirstGroupGrowth.apply(p, d);
        return rules;
    }

    private static KehengChecks.Fixture hero(GameTestHelper h, int location, String role) {
        var f = KehengChecks.fixture(h, location);
        var d = PlayerDataProvider.get(f.p);
        d.setCharacterId(role);
        f.p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.initialWeaponItem(role)));
        FirstGroupGrowth.initialize(f.p, d);
        return f;
    }

    private static void learn(KehengChecks.Fixture f, int index) {
        var d = PlayerDataProvider.get(f.p);
        d.setSkillLevel(FirstGroupSkills.node(d.getCharacterId(), index).getId(), 1);
    }

    static void tower(GameTestHelper h) {
        var suite = new ArenaFixtureChecks.Suite(h.getLevel().getServer());
        try {
            suite.install();
            suite.begin("3v3");
            var match = GameManager.get();
            var attacker = suite.players.getFirst();
            var faction = match.factionOf(attacker);
            var defending = match.factionOf(suite.opponent(attacker));
            var building = suite.units("TOWER", defending).stream().filter(t -> t.blockPosition().equals(
                    LaneSystem.towerPoint(defending, LaneSystem.Lane.MID, 1))).findFirst().orElseThrow();
            suite.clock(match.getMatchStartTime() + GameManager.combatTempo().firstWaveTicks());
            LaneSystem.tick(suite.server.overworld());
            var minion = suite.units("MELEE", faction).getFirst();
            var distant = minion.position();
            var rows = new ArrayList<Object>();
            for (int minute : List.of(3, 8, 12)) {
                suite.clock(match.getMatchStartTime() + minute * 1200L);
                for (boolean wave : List.of(false, true)) {
                    minion.setPos(wave ? building.position().add(2, 0, 0) : distant);
                    var observed = new TreeMap<String, Object>();
                    observed.put("minute", minute);
                    observed.put("wave", wave);
                    for (var role : List.of(CharacterType.LUOXIA, CharacterType.QINGLUO)) {
                        var data = PlayerDataProvider.get(attacker);
                        data.setCharacterId(role.getId());
                        FirstGroupGrowth.initialize(attacker, data);
                        boolean mage = role == CharacterType.QINGLUO;
                        float raw = mage ? MagicBullet.rawDamage(role, attacker.getAttributeValue(ModAttributes.SPELL_POWER))
                                : (float)attacker.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                        float before = LaneSystem.health(building);
                        DamageCalc.dealBasicDamage(attacker, building, raw,
                                mage ? DamageCalc.Kind.MAGIC : DamageCalc.Kind.PHYSICAL);
                        observed.put(role.getId(), before - LaneSystem.health(building));
                    }
                    rows.add(observed);
                }
            }
            var path = Path.of("..", "build", "balance", "tower.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            System.out.println("[MYRIAD-BALANCE-TOWER] PASS: production tower pool sampled in twelve cases");
            h.succeed();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
        }
    }

    static void run(GameTestHelper h) {
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        var f = KehengChecks.fixture(h, 4);
        var rules = snapshot(f);
        var samples = new TreeMap<String, Object>();
        var bow = hero(h, 0, "luoxia");
        LuoxiaBow.input(bow.p, LuoxiaBow.PRESS);
        var night = hero(h, 1, "yingxiao");
        var buffed = hero(h, 2, "yingxiao");
        var back = hero(h, 3, "yingxiao");
        var herb = hero(h, 5, "qingluo");
        var cases = List.of(f, bow, night, buffed, back, herb);
        for (var n : List.of(night, buffed, back)) {
            learn(n, 0);
            learn(n, 2);
            learn(n, 7);
            var data = PlayerDataProvider.get(n.p);
            data.setFirstGroupAttribute("strength", 1);
            FirstGroupGrowth.apply(n.p, data);
            n.t.setYRot(n == back ? 0 : 180);
        }
        // Same strength rank. The second cast has the +15% total AD of an external buff.
        buffed.p.getAttribute(ModAttributes.PHYSICAL_ATTACK).setBaseValue(65+12);
        // Keep the three short ray tests inside the loaded test structure's chunks.
        int lane = 0;
        for (var n : List.of(night, buffed, back)) {
            var startPos = f.origin.add(4 * (++lane), 0, -8);
            n.p.setPos(startPos);
            n.t.setPos(startPos.add(0, 0, 2));
        }
        learn(herb, 2);
        learn(herb, 5);
        var herbData = PlayerDataProvider.get(herb.p);
        herbData.setFirstGroupAttribute("defense", 1);
        FirstGroupGrowth.apply(herb.p, herbData);
        herb.p.setHealth(herb.p.getMaxHealth()-50);
        float beforeHeal = herb.p.getHealth();
        SkillSystem.cast(herb.p, 0);
        samples.put("herbRealHeal", herb.p.getHealth()-beforeHeal);
        var d = PlayerDataProvider.get(f.p);
        int rank = 3;
        var dome = FirstGroupSkills.node("keheng", 6);
        d.setSkillLevel(dome.getId(), rank);
        d.setMana(700);
        KehengChecks.point(f, 2);
        SkillSystem.cast(f.p, 3);
        AbilityRuntime.confirm(f.p);
        var changes = new ArrayList<Object>();
        long start = h.getLevel().getGameTime();
        float startHp = f.t.getHealth();
        float paidMana = d.getMana();
        int startedCooldown = d.getCooldown(dome.getId());
        GameTestTicks.addListener(new Consumer<MinecraftServer>() {
            double previousRate = 1;
            float previousHp = startHp;
            public void accept(MinecraftServer ignored) {
                long now = h.getLevel().getGameTime();
                int age = (int)(now-start);
                for (var c : cases) AbilityRuntime.tick(c.p);
                if (age == 5) {
                    for (var n : List.of(night, buffed, back)) SkillSystem.cast(n.p, 0);
                }
                if (age == 11) LuoxiaBow.input(bow.p, LuoxiaBow.RELEASE);
                if (age == 20) {
                    for (var n : List.of(night, buffed, back)) SkillSystem.cast(n.p, 3);
                    SkillSystem.cast(herb.p, 2);
                    samples.put("nightFront", 10000 - night.t.getHealth());
                    samples.put("nightBuffedFront", 10000 - buffed.t.getHealth());
                    samples.put("nightBack", 10000 - back.t.getHealth());
                    samples.put("nightShield", ModShieldSystem.value(night.p));
                    samples.put("bowHit", 10000 - bow.t.getHealth());
                    samples.put("herbShield", ModShieldSystem.value(herb.p));
                    var mixed = new ArrayList<Object>();
                    for (var kind : DamageCalc.Kind.values()) {
                        float post = DamageCalc.applyMitigation(null, herb.p, 120, kind);
                        mixed.add(Map.of("kind", kind, "raw", 120, "post", post,
                                "absorbed", ModShieldSystem.consume(herb.p, post)));
                    }
                    samples.put("herbMixed", mixed);
                }
                KehengTimeFields.tick(h.getLevel());
                KehengTimeFields.refreshActor(f.t);
                double rate = ActionTime.multiplier(f.t.getUUID(), now);
                float hp = f.t.getHealth();
                if (rate != previousRate || hp != previousHp) {
                    changes.add(Map.of("tick", age, "rate", rate, "hpLost", (double)(startHp - hp)));
                    previousRate = rate;
                    previousHp = hp;
                }
                if (age < 130) return;
                GameTestTicks.removeListener(this);
                // Keep zero damage, full immunity or an unexpired field observable for the audit.
                var report = Map.of("rules", rules, "samples", samples, "dome", Map.of("rank", rank, "manaBefore", 700,
                        "manaAfter", paidMana, "cooldown", startedCooldown, "events", changes));
                try {
                    var path = Path.of("..","build","balance","engine.json");
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException e) { throw new IllegalStateException(e); }
                for (var c : cases) {
                    for (var p : c.players) {
                        AbilityRuntime.clear(p);
                        ModShieldSystem.clearFor(p);
                        h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());
                        h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
                    }
                    c.blocks.forEach((pos,state) -> h.getLevel().setBlockAndUpdate(pos,state));
                }
                System.out.println("[MYRIAD-BALANCE-BENCHMARK] PASS: cast, observed field and export");
                h.succeed();
            }
        });
    }
}
