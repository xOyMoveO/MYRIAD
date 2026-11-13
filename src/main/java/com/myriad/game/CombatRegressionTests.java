package com.myriad.game;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.myriad.combat.NpcCombat;
import com.myriad.combat.DamageCalc;

/** Explicit /test or runGameTestServer only; never runs as part of a match. */
public final class CombatRegressionTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "myriad");
    static {
        if (Boolean.getBoolean("myriad.arenaTests")) FUNCTIONS.register("arena_switch", () -> ArenaSwitchRegression::run);
        FUNCTIONS.register("combat_regression", () -> CombatRegressionTests::run);
        FUNCTIONS.register("yike", () -> YikeChecks::run);
        FUNCTIONS.register("first_group", () -> FirstGroupRegressionChecks::run);
        FUNCTIONS.register("melee_arc", () -> helper -> {
            verifyMeleeArc(helper);
            helper.succeed();
        });
    }

    private static Object field(Class<?> owner, Object instance, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    @SuppressWarnings("unchecked")
    private static void run(GameTestHelper helper) {
        ModState.setEnabled(true);
        helper.runAfterDelay(3, () -> {
            try {
                SharedControlChecks.verifyCombatContracts(helper);
                SharedControlChecks.verifyGeometry(helper);
                verifyMeleeArc(helper);
                var level = helper.getLevel();
                BlockPos a = helper.absolutePos(new BlockPos(3, 2, 3));
                MonsterManager.spawn(level, a, MonsterType.FROST_WEB_MATRIARCH);
                MonsterManager.spawn(level, a.offset(3, 0, 0), MonsterType.SPRING_REEF_GUARDIAN);
                java.util.ArrayList<Mob> monsters = new java.util.ArrayList<>();
                for (Object state : ((Map<?, ?>) field(MonsterManager.class, null, "STATES")).values()) {
                    monsters.add((Mob) field(state.getClass(), state, "entity"));
                }
                helper.assertTrue(monsters.size() == 2, "Both medium monsters must be created");
                for (Mob mob : monsters) {
                    mob.checkDespawn();
                    helper.assertTrue(!mob.isRemoved(), "Managed monsters must survive peaceful despawn checks");
                }
                var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
                player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                        net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                    // The GameTest exercises server rules without a negotiated client transport.
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
                };
                player.connection.markClientLoaded();
                player.setPos(a.getX(), a.getY(), a.getZ());
                float before = player.getHealth();
                NpcCombat.hit(player, monsters.getFirst(), 9, DamageCalc.Kind.PHYSICAL);
                helper.assertTrue(player.getHealth() < before, "NPC attack must reduce survival player health on peaceful");
                player.invulnerableTime = 0;
                var tower = com.myriad.registry.ModEntities.DEFENSE_TOWER.get().create(level,
                        net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                before = player.getHealth();
                NpcCombat.hit(player, tower, 9, DamageCalc.Kind.PHYSICAL);
                helper.assertTrue(player.getHealth() < before, "Tower attack must reduce survival player health on peaceful");
                float towerHp = tower.getHealth();
                for (DamageCalc.Kind kind : DamageCalc.Kind.values()) {
                    DamageCalc.Roll result = DamageCalc.dealDamage(player, tower, 100, kind);
                    helper.assertTrue(result.damage() == 0 && tower.getHealth() == towerHp,
                            "All skill damage kinds must ignore towers");
                }
                tower.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 3));
                tower.setDeltaMovement(new net.minecraft.world.phys.Vec3(2, 1, 2));
                helper.assertTrue(!tower.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)
                        && tower.getDeltaMovement().lengthSqr() == 0
                        && !com.myriad.combat.KnockbackBuffer.allow(tower),
                        "Towers must reject skill statuses and forced motion");

                Map<LaneSystem.Lane, Map<Integer, BlockPos>> paths = (Map<LaneSystem.Lane, Map<Integer, BlockPos>>) field(LaneSystem.class, null, "PATH_POINTS");
                Map<Faction, BlockPos> bases = (Map<Faction, BlockPos>) field(LaneSystem.class, null, "FOUNTAIN_POINTS");
                bases.put(Faction.XUANXIAO, a);
                bases.put(Faction.YOUMING, a.offset(30, 0, 0));
                for (var lane : LaneSystem.Lane.values()) {
                    paths.get(lane).clear();
                    paths.get(lane).put(1, a.offset(0, 0, lane.ordinal()*5));
                    paths.get(lane).put(2, a.offset(30, 0, lane.ordinal()*5));
                    paths.get(lane).put(3, a.offset(0, 0, lane.ordinal()*5));
                }
                var ordered = LaneSystem.class.getDeclaredMethod("orderedPath", LaneSystem.Lane.class, Faction.class);
                ordered.setAccessible(true);
                var reversed = (List<BlockPos>) ordered.invoke(null, LaneSystem.Lane.TOP, Faction.YOUMING);
                helper.assertTrue(reversed.size() == 2 && reversed.getFirst().equals(a.offset(30, 0, 0)), "Looped route must spawn from opposite bases");
                var saved = new java.util.HashMap<>(paths.get(LaneSystem.Lane.TOP));
                paths.get(LaneSystem.Lane.TOP).clear();
                helper.assertTrue(((List<?>) ordered.invoke(null, LaneSystem.Lane.TOP, Faction.YOUMING)).size() == 4, "Fallback route must reverse without immutable-list crash");
                paths.get(LaneSystem.Lane.TOP).putAll(saved);
                helper.assertTrue(((Long) field(LaneSystem.class, null, "FIRST_WAVE_DELAY")) == 1400L, "First wave delay must remain 70 seconds");
                helper.assertTrue(Math.abs(MatchBalance.towerScale(0, 1) - 1.30) < .0001
                        && Math.abs(MatchBalance.towerScale(0, 4) - 2.20) < .0001,
                        "Consecutive tower hits against heroes must grow by 30 percent and cap at four stacks");
                helper.assertTrue(Math.abs(MatchBalance.underdogScale(2) - 1.25) < .0001
                                && Math.abs(MatchBalance.underdogScale(99) - 2.0) < .0001,
                        "Underdog kill rewards must start at a two-level gap and cap at double");
                for (var equipment : com.myriad.registry.EquipmentRegistry.all()) {
                    if (equipment.tier() != com.myriad.registry.EquipmentRegistry.TIER_LEGEND) continue;
                    helper.assertTrue(equipment.totalPrice() >= 2000 && equipment.totalPrice() <= 3000,
                            "Legend price must be in the 2000-3000-gold range: " + equipment.id());
                    int components = 0;
                    for (String ingredient : equipment.ingredients()) {
                        var component = com.myriad.registry.EquipmentRegistry.get(ingredient);
                        if (component != null) components += component.totalPrice();
                    }
                    helper.assertTrue(equipment.totalPrice() - components
                                    >= com.myriad.registry.EquipmentRegistry.LEGEND_MINIMUM_COMBINE_COST,
                            "Legend combine cost must respect the 1200-gold floor: " + equipment.id());
                }
                helper.assertTrue(com.myriad.registry.EquipmentRegistry.get("xiaoxuelian").totalPrice() == 450,
                        "Lesser Blood Lotus must cost exactly 450 gold");
                helper.assertTrue(com.myriad.player.PlayerDataProvider.healthRegenPulseAmount(.8) == 4
                        && com.myriad.player.PlayerDataProvider.healthRegenPulseTicks(.8) == 100,
                        "Health regeneration must floor its five-second integer pulse");
                helper.assertTrue(com.myriad.player.PlayerDataProvider.healthRegenPulseAmount(.04) == 1
                        && com.myriad.player.PlayerDataProvider.healthRegenPulseTicks(.04) == 500,
                        "Sub-one five-second regeneration must convert to the seconds needed to heal one health");
                var wave = LaneSystem.class.getDeclaredField("waveNumber"); wave.setAccessible(true);
                var spawn = LaneSystem.class.getDeclaredMethod("spawnWave", net.minecraft.server.level.ServerLevel.class);
                spawn.setAccessible(true);
                Map<?, ?> units = (Map<?, ?>) field(LaneSystem.class, null, "UNITS");
                for (int i = 1; i <= 3; i++) {
                    wave.setInt(null, i); spawn.invoke(null, level);
                    helper.assertTrue(units.size() == i*24, "Each wave must spawn 4 minions for each of 6 team-lanes");
                }
                long magic = 0, melee = 0;
                for (Object state : units.values()) {
                    String kind = field(state.getClass(), state, "kind").toString();
                    if (kind.equals("MAGIC")) magic++;
                    if (kind.equals("MELEE")) melee++;
                }
                helper.assertTrue(magic == 6 && melee == 36, "Wave 3 replaces exactly one ranged per team-lane");
                helper.runAfterDelay(25, () -> {
                    try {
                    for (Mob monster : monsters) helper.assertTrue(monster.isAlive() && !monster.isRemoved(), "Monster disappeared after ticking");
                    helper.assertTrue(units.size() == 72, "Minions disappeared after ticking");
                    for (Object id : units.keySet()) {
                        Entity entity = level.getEntity((java.util.UUID) id);
                        helper.assertTrue(entity instanceof LaneMinionEntity, "Custom minion missing (must not be iron golem)");
                    }
                    Object own = units.values().stream().filter(s -> {
                        try { return field(s.getClass(), s, "faction") == Faction.XUANXIAO; }
                        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    }).findFirst().orElseThrow();
                    Object other = units.values().stream().filter(s -> {
                        try { return field(s.getClass(), s, "faction") == Faction.YOUMING
                                && field(s.getClass(), s, "lane") == field(own.getClass(), own, "lane"); }
                        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    }).findFirst().orElseThrow();
                    Mob ownMob = (Mob) field(own.getClass(), own, "entity");
                    Mob enemy = (Mob) field(other.getClass(), other, "entity");
                    ownMob.setPos(a.getX()+.5, a.getY(), a.getZ()+.5);
                    enemy.setPos(a.getX()+4.5, a.getY(), a.getZ()+.5);
                    var tick = LaneSystem.class.getDeclaredMethod("tickMinion", net.minecraft.server.level.ServerLevel.class, Mob.class, own.getClass(), long.class);
                    tick.setAccessible(true);
                    tick.invoke(null, level, ownMob, own, level.getGameTime()+100);
                    helper.assertTrue(field(own.getClass(), own, "chaseTarget") != null, "Minion must acquire a nearby enemy");
                    enemy.setPos(a.getX()+40, a.getY(), a.getZ());
                    tick.invoke(null, level, ownMob, own, level.getGameTime()+101);
                    helper.assertTrue(!(Boolean) field(own.getClass(), own, "returning"), "Lost target must not force a retreat");
                    helper.assertTrue(!enemy.getUUID().equals(field(own.getClass(), own, "chaseTarget")), "Escaped target must be dropped");
                    helper.assertTrue(((Long) field(LaneSystem.class, null, "WAVE_INTERVAL")) == 800L, "Wave interval must be 40 seconds");
                    verifyPersistence(helper, player);
                    verifyTowerAdvance(helper, a);
                    RuneRemovalChecks.run(helper, player);
                    verifyDashHitbox(helper, player, a);
                    verifySiegeAndGrowth(helper,a);
                    verifyProgressionAndTraining(helper, player);
                    verifyRetiredCoreEffects(helper, player);

                    verifyBaseAndCrystal(helper,player,a);
                    ModState.setEnabled(false);
                    var tabEvent = new net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat(player);
                    com.myriad.player.PlayerDataProvider.onTabListNameFormat(tabEvent);
                    helper.assertTrue(tabEvent.getDisplayName() == null,
                            "Disabled mod must leave tab names to vanilla scoreboard formatting");
                    helper.succeed();
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                });
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verifyMeleeArc(GameTestHelper helper) {
        var origin = net.minecraft.world.phys.Vec3.ZERO;
        var facing = new net.minecraft.world.phys.Vec3(0, 0, 1);
        double angle = com.myriad.combat.BasicAttackSystem.MELEE_HALF_ANGLE;
        helper.assertTrue(com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(1, 0, 1.5, 1.6, 1.8, 2.1), 3, angle),
                "Melee must hit targets beside the crosshair inside the forward sector");
        helper.assertTrue(!com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(-.3, 0, -2.5, .3, 1.8, -1.9), 3, angle),
                "Melee must not hit targets behind the attacker");
        helper.assertTrue(!com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(-.3, 0, 3.1, .3, 1.8, 3.7), 3, angle),
                "Melee must not exceed its reach");
        helper.assertTrue(!com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(-.3, 3, 1, .3, 4.8, 1.6), 3, angle),
                "Melee must not damage targets on another floor");
        helper.assertTrue(com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(-1.6, 0, 1.5, -1, 1.8, 2.1), 3, angle),
                "Melee sector must be symmetric on the left and right");
        helper.assertTrue(!com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(2.4, 0, -.3, 3, 1.8, .3), 3, angle),
                "Melee must not damage a target outside the sector even when it is within reach");
        helper.assertTrue(!com.myriad.combat.BasicAttackSystem.inMeleeArc(origin, facing,
                new net.minecraft.world.phys.AABB(1.9, 0, 1.25, 2.1, 1.8, 1.45), 3, angle),
                "Melee must reject a small target just outside the new 80 degree sector");
    }

    private static void verifyRetiredCoreEffects(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        var data = com.myriad.player.PlayerDataProvider.get(player);
        String oldRole = data.getCharacterId();
        float oldHp = player.getHealth();
        data.setCharacterId("fenjue"); player.setHealth(player.getMaxHealth() * .2F);
        com.myriad.character.skills.fenjue.FenJuePassiveSkill.tick(player, data);
        var ad = player.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_ATTACK);
        var fury = net.minecraft.resources.Identifier.parse("myriad:fenjue_passive_ad");
        helper.assertTrue(ad.hasModifier(fury) && Math.abs(ad.getModifier(fury).amount() - .35) < .0001,
                "Fenjue passive grants 35% AD at twenty percent health");
        player.setHealth(player.getMaxHealth() * .21F);
        com.myriad.character.skills.fenjue.FenJuePassiveSkill.tick(player, data);
        helper.assertTrue(!ad.hasModifier(fury), "Fenjue passive is inactive above twenty percent health");
        data.setCharacterId(oldRole);
        com.myriad.character.skills.fenjue.FenJuePassiveSkill.tick(player, data);
        helper.assertTrue(!ad.hasModifier(fury), "Other heroes cannot inherit Fenjue passive");
        data.setLastCombatGameTime(-1); player.setHealth(oldHp);
    }

    private static void verifyProgressionAndTraining(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        var data = com.myriad.player.PlayerDataProvider.get(player);
        data.setCharacterId("yingxiao"); data.setFaction(Faction.XUANXIAO);
        data.setLevel(1); data.setXp(0);
        com.myriad.player.PlayerHealthSystem.fill(player);
        player.setHealth(70); data.setMana(17);
        com.myriad.player.LevelSystem.addXp(player, data, com.myriad.player.LevelSystem.xpToNext(1));
        helper.assertTrue(data.getLevel() == 2 && player.getHealth() == 70 && data.getMana() == 17,
                "Level-up must preserve current HP and mana, not refill them");
        data.setXp(com.myriad.player.LevelSystem.xpToNext(2));
        com.myriad.player.LevelSystem.addXp(player, data, 0);
        helper.assertTrue(data.getLevel() == 3, "Stored full XP must reconcile even with zero new XP");
        var position = player.position();
        TrainingMode.setEnabled(true);
        var death = new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic());
        com.myriad.player.PlayerDataProvider.onLivingDeath(death);
        TrainingMode.setEnabled(false);
        helper.assertTrue(death.isCanceled() && player.position().equals(position)
                && player.getHealth() == player.getMaxHealth() && !HeroRespawnSystem.isWaiting(player),
                "Training death must instantly revive in place without spectator countdown");
    }

    private static void verifyDashHitbox(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, BlockPos a) {
        var level = helper.getLevel();
        var from = new net.minecraft.world.phys.Vec3(a.getX(), a.getY(), a.getZ());
        var to = from.add(8,0,0);
        player.setPos(to.x,to.y,to.z);
        var one = EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        var two = EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        one.setPos(from.x+3,from.y,from.z+.8); two.setPos(from.x+5,from.y,from.z+.8);
        level.addFreshEntity(one);level.addFreshEntity(two);
        var hits = com.myriad.combat.DashHitbox.targets(player,from,to,1.2,java.util.Set.of());
        helper.assertTrue(hits.contains(one) && hits.contains(two), "Swept dash detects both victims between endpoint ticks");
        hits = com.myriad.combat.DashHitbox.targets(player,from,to,1.2,java.util.Set.of(one.getUUID()));
        helper.assertTrue(!hits.contains(one) && hits.contains(two), "Already-hit enemy cannot hide a new dash victim");
        one.discard();two.discard();
    }

    @SuppressWarnings("unchecked")
    private static void verifyPersistence(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player)
            throws ReflectiveOperationException {
        var data = com.myriad.player.PlayerDataProvider.get(player);
        data.setCharacterId("yingxiao"); data.setLevel(9); data.setGold(1234); data.resetMatchGoldEarned(2468); data.setXp(87);
        data.setFaction(Faction.XUANXIAO); data.setSkillLevel("test_skill", 4); data.setCooldown("test_skill", 83);
        data.setEquipApplied("test_attribute", 15);
        var codec = com.myriad.player.PlayerData.CODEC;
        var encoded = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
        var restored = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        var cloned = new com.myriad.player.PlayerData(); cloned.copyStateFrom(restored);
        helper.assertTrue("yingxiao".equals(restored.getCharacterId()) && restored.getLevel() == 9
                && restored.getGold() == 1234 && restored.getMatchGoldEarned() == 2468
                && restored.getXp() == 87 && restored.getFaction() == Faction.XUANXIAO,
                "Player progression must survive persistent attachment codec roundtrip");
        helper.assertTrue(cloned.getMatchGoldEarned() == 2468,
                "Gross match gold must survive the death-clone copy path");
        helper.assertTrue(restored.getSkillLevels().get("test_skill") == 4 && restored.getCooldown("test_skill") == 83
                && restored.getEquipApplied("test_attribute") == 0,
                "Skills must persist; temporary modifier bookkeeping must rebuild");
        var manager = GameManager.get();
        var phase = GameManager.class.getDeclaredField("phase"); phase.setAccessible(true);
        phase.set(manager, GameManager.Phase.PLAYING);
        ((Map<java.util.UUID,Faction>) field(GameManager.class,manager,"factions")).put(player.getUUID(),Faction.XUANXIAO);
        manager.onPlayerQuit(player);
        GameManager.restore(com.google.gson.JsonParser.parseString(MatchProgress.JSON.toJson(GameManager.snapshot())),helper.getLevel().getServer());
        helper.assertTrue(GameManager.get().getPhase() == GameManager.Phase.PLAYING
                && GameManager.get().factionOf(player) == Faction.XUANXIAO, "Disconnect and world reload must preserve match and faction");
        GameManager.get().onPlayerJoin(player);
        helper.assertTrue(data.getGold() == 1234 && "yingxiao".equals(data.getCharacterId()), "Reconnect must not reset character progression");
        var lanes = com.google.gson.JsonParser.parseString(MatchProgress.JSON.toJson(LaneSystem.snapshot())).getAsJsonObject();
        lanes.getAsJsonArray("units").get(0).getAsJsonObject().addProperty("health", 17);
        LaneSystem.restore(helper.getLevel(),lanes);
        var units = (Map<?,?>) field(LaneSystem.class,null,"UNITS");
        helper.assertTrue(units.size() == 72 && (Integer) field(LaneSystem.class,null,"waveNumber") == 3,
                "World reload must restore all minions and wave number without duplicate spawning");
        helper.assertTrue(units.values().stream().anyMatch(s -> {
            try { return ((Number) field(s.getClass(),s,"health")).doubleValue() == 17; }
            catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }), "Damaged minion health must survive world reload");
        var monsters = com.google.gson.JsonParser.parseString(MatchProgress.JSON.toJson(MonsterManager.snapshot())).getAsJsonObject();
        MonsterManager.restore(helper.getLevel(),monsters);
        helper.assertTrue(((Map<?,?>)field(MonsterManager.class,null,"STATES")).size() == 2,
                "World reload must restore monsters without losing or duplicating spawns");
        player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        player.experienceLevel = 12;
        com.myriad.player.PlayerDataProvider.resetForHostSession(player);
        var fresh = com.myriad.player.PlayerDataProvider.get(player);
        helper.assertTrue(fresh != data && fresh.getCharacterId() == null && fresh.getFaction() == null
                && fresh.getLevel() == 1 && fresh.getXp() == 0 && fresh.getGold() == 0
                && fresh.getSkillLevels().isEmpty() && fresh.getCooldown("test_skill") == 0
, "New host session must discard all persisted player progression");
        helper.assertTrue(player.getInventory().isEmpty() && player.experienceLevel == 0
                && player.getHealth() == player.getMaxHealth(), "Host reset must clear inventory and restore baseline vitals");
        phase.set(GameManager.get(),GameManager.Phase.WAITING);
        helper.assertTrue(LaneSystem.minionReward(12, false) == 3 && LaneSystem.minionReward(18, false) == 4
                && LaneSystem.minionReward(65, true) == 65 && LaneSystem.minionReward(65, false) == 16,
                "Magic minion starts at 65 gold and non-last-hit gold rounds down at 25 percent");
        helper.assertTrue(LaneSystem.minionReward(18, false, true) == 14
                && LaneSystem.minionReward(24, false, true) == 19,
                "Non-last-hit experience rounds down at 80 percent");
        helper.assertTrue(LaneSystem.minionReward(12, true) == 12 && LaneSystem.minionReward(24, true) == 24,
                "Player last hits retain full rewards");
        var queued = new java.util.ArrayDeque<Long>();
        com.myriad.character.SequentialChargeTimers.enqueue(queued, 100, 20);
        com.myriad.character.SequentialChargeTimers.enqueue(queued, 105, 20);
        helper.assertTrue(queued.removeFirst() == 120 && queued.peekFirst() == 140,
                "Missing charges must recover one at a time");
        var reduced = new java.util.ArrayList<Long>();
        com.myriad.character.SequentialChargeTimers.enqueue(reduced, 100, 20);
        com.myriad.character.SequentialChargeTimers.enqueue(reduced, 105, 20);
        com.myriad.character.SequentialChargeTimers.moveFirstTo(reduced, 110);
        helper.assertTrue(reduced.get(0) == 110 && reduced.get(1) == 130,
                "Reducing the active recharge must shift later charges without parallel recovery");
    }

    @SuppressWarnings("unchecked")
    private static void verifyBaseAndCrystal(GameTestHelper helper,net.minecraft.server.level.ServerPlayer player,BlockPos a) throws ReflectiveOperationException {
        var level=helper.getLevel();var manager=GameManager.get();
        var towerPoints=configureTowerFixture(a);
        BaseRules.logout(player); // Independent fixture: earlier damage tests marked this mock player in combat.
        var phase=GameManager.class.getDeclaredField("phase");phase.setAccessible(true);phase.set(manager,GameManager.Phase.PLAYING);
        var bases=(Map<Faction,BlockPos>)field(LaneSystem.class,null,"FOUNTAIN_POINTS");bases.put(Faction.XUANXIAO,a);
        player.setPos(a.getX()+.5,a.getY(),a.getZ()+.5);
        helper.assertTrue(BaseRules.canShop(player),"Own base permits equipment buying");
        BaseRules.tick(player);
        player.setPos(a.getX()+12,a.getY(),a.getZ()+.5);
        helper.assertTrue(!BaseRules.canShop(player),"Equipment buying must be rejected outside 10 blocks");
        var data=com.myriad.player.PlayerDataProvider.get(player);int gold=data.getGold();
        helper.assertTrue(!com.myriad.player.EquipmentSystem.buy(player,data,"not_an_equipment")&&data.getGold()==gold,
                "Rejected purchase cannot spend gold");
        BaseRules.tick(player);
        var haste=net.minecraft.resources.Identifier.parse("myriad:base_haste");
        helper.assertTrue(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).hasModifier(haste),"Leaving base grants haste");
        BaseRules.combat(player,null);
        helper.assertTrue(!player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).hasModifier(haste),"Taking damage immediately removes haste");
        BaseRules.logout(player);
        player.setPos(a.getX()+.5,a.getY(),a.getZ()+.5);BaseRules.tick(player);
        player.setPos(a.getX()+12,a.getY(),a.getZ()+.5);BaseRules.tick(player);
        BaseRules.combat(null,player);
        helper.assertTrue(!player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).hasModifier(haste),"Dealing damage immediately removes haste");
        var matchStart=GameManager.class.getDeclaredField("matchStartTime");matchStart.setAccessible(true);
        var overworld=level.getServer().overworld();
        var clock=(net.minecraft.world.level.storage.ServerLevelData)overworld.getLevelData();
        long savedTime=overworld.getGameTime(),savedStart=matchStart.getLong(manager);
        try {
            // A fresh test world is younger than four minutes. Negative match
            // starts mean "not started", so use a valid clock for these checks.
            clock.setGameTime(Math.max(savedTime,4800));
            long now=overworld.getGameTime();
            matchStart.setLong(manager,now);helper.assertTrue(LaneSystem.minionReward(100,true)==100,"Opening minions keep base rewards");
            matchStart.setLong(manager,now-2400);helper.assertTrue(LaneSystem.minionReward(100,true)==110
                    &&LaneSystem.minionReward(100,false)==27&&LaneSystem.minionReward(100,false,true)==88,
                    "Two minutes increases last-hit, shared gold, and shared experience rewards");
            matchStart.setLong(manager,now-4800);helper.assertTrue(LaneSystem.minionReward(100,true)==120,"Four minutes adds another ten percent");
        } finally {
            clock.setGameTime(savedTime);matchStart.setLong(manager,savedStart);
        }
        var spawn=LaneSystem.class.getDeclaredMethod("spawnCrystal",net.minecraft.server.level.ServerLevel.class,Faction.class,BlockPos.class);spawn.setAccessible(true);
        spawn.invoke(null,level,Faction.YOUMING,a.offset(20,0,0));
        var units=(Map<?,?>)field(LaneSystem.class,null,"UNITS");Object state=units.values().iterator().next();
        var crystal=(Mob)field(state.getClass(),state,"entity");
        NpcCombat.hit(crystal,player,50,DamageCalc.Kind.PHYSICAL);
        helper.assertTrue(LaneSystem.health(crystal)==21000,"Protected crystal must reject damage");
        var destroyed=(java.util.Set<String>)field(LaneSystem.class,null,"DESTROYED_TOWERS");
        destroyed.add("youming/top/1");destroyed.add("youming/top/2");
        NpcCombat.hit(crystal,player,50,DamageCalc.Kind.PHYSICAL);
        helper.assertTrue(LaneSystem.health(crystal)==21000,"Two towers are insufficient to unlock crystal");
        destroyed.add("youming/top/3");crystal.invulnerableTime=0;
        NpcCombat.hit(crystal,player,50,DamageCalc.Kind.PHYSICAL);
        helper.assertTrue(LaneSystem.health(crystal)<21000,"Three destroyed towers allow crystal damage");
        var hp=state.getClass().getDeclaredField("health");hp.setAccessible(true);hp.setFloat(state,1);crystal.invulnerableTime=0;
        NpcCombat.hit(crystal,player,50,DamageCalc.Kind.PHYSICAL);
        helper.assertTrue(manager.getPhase()==GameManager.Phase.ENDED&&manager.getWinner()==Faction.XUANXIAO,"Destroying the crystal must end the match for its enemy");
        BaseRules.logout(player);phase.set(manager,GameManager.Phase.WAITING);
        var clear=LaneSystem.class.getDeclaredMethod("clearRuntime",net.minecraft.server.level.ServerLevel.class);clear.setAccessible(true);clear.invoke(null,level);
        towerPoints.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object,BlockPos> configureTowerFixture(BlockPos a) throws ReflectiveOperationException {
        // The preceding tower-navigation fixture clears its placements. Configure
        // both factions and every lane so locked lanes fail for intact towers,
        // rather than for missing layout data.
        Class<?> keyType=Class.forName("com.myriad.game.LaneSystem$TowerKey");
        var key=keyType.getDeclaredConstructor(Faction.class,LaneSystem.Lane.class,int.class);key.setAccessible(true);
        var points=(Map<Object,BlockPos>)field(LaneSystem.class,null,"TOWER_POINTS");points.clear();
        for(var faction:Faction.values())for(var lane:LaneSystem.Lane.values())for(int tier=1;tier<=3;tier++)
            points.put(key.newInstance(faction,lane,tier),a.offset(10+faction.ordinal()*50+tier*8,0,lane.ordinal()*10));
        return points;
    }

    @SuppressWarnings("unchecked")
    private static void verifySiegeAndGrowth(GameTestHelper helper,BlockPos a) throws ReflectiveOperationException {
        var level=helper.getLevel();
        var towerPoints=configureTowerFixture(a);
        var wave=LaneSystem.class.getDeclaredField("waveNumber");wave.setAccessible(true);wave.setInt(null,1);
        var start=LaneSystem.class.getDeclaredField("activeMatchStart");start.setAccessible(true);start.setLong(null,0);
        var spawn=LaneSystem.class.getDeclaredMethod("spawnWave",net.minecraft.server.level.ServerLevel.class);spawn.setAccessible(true);
        var unlocked=LaneSystem.class.getDeclaredMethod("siegeUnlocked",Faction.class,LaneSystem.Lane.class);unlocked.setAccessible(true);
        var destroyed=(java.util.Set<String>)field(LaneSystem.class,null,"DESTROYED_TOWERS");
        destroyed.clear();
        destroyed.add("youming/top/1");destroyed.add("youming/top/2");
        helper.assertTrue(!(Boolean)unlocked.invoke(null,Faction.XUANXIAO,LaneSystem.Lane.TOP),"Two towers must not unlock siege units");
        destroyed.add("youming/top/3");
        helper.assertTrue((Boolean)unlocked.invoke(null,Faction.XUANXIAO,LaneSystem.Lane.TOP)
                && !(Boolean)unlocked.invoke(null,Faction.YOUMING,LaneSystem.Lane.TOP)
                && !(Boolean)unlocked.invoke(null,Faction.XUANXIAO,LaneSystem.Lane.MID),"Siege unlock must be faction and lane scoped");
        spawn.invoke(null,level);
        var units=(Map<?,?>)field(LaneSystem.class,null,"UNITS");
        var siege=units.values().stream().filter(s->{try{return field(s.getClass(),s,"kind").toString().equals("SIEGE");}catch(Exception e){throw new IllegalStateException(e);}}).toList();
        helper.assertTrue(siege.size()==1,"An unlocked lane must replace exactly one melee minion");
        Object state=siege.getFirst();LaneMinionEntity mob=(LaneMinionEntity)field(state.getClass(),state,"entity");
        helper.assertTrue(mob.getBbHeight()==2F && mob.getBbWidth()==1.5F,"Siege dimensions must be 2 high and 1.5 wide");
        var scale=LaneSystem.class.getDeclaredMethod("scaleMinion",Mob.class,state.getClass(),long.class);scale.setAccessible(true);
        scale.invoke(null,mob,state,100L);
        helper.assertTrue(((Number)field(state.getClass(),state,"maxHealth")).floatValue()==2240
                && ((Number)field(state.getClass(),state,"attackDamage")).floatValue()==63,"Siege base stats must exceed ordinary minions");
        var hp=state.getClass().getDeclaredField("health");hp.setAccessible(true);hp.setFloat(state,1120);
        wave.setInt(null,5);scale.invoke(null,mob,state,200L);
        helper.assertTrue(Math.abs(((Number)field(state.getClass(),state,"maxHealth")).floatValue()-2576)<.01
                && Math.abs(hp.getFloat(state)-1288)<.01,"Wave 5 growth must preserve current health ratio");
        scale.invoke(null,mob,state,24000L);
        helper.assertTrue(Math.abs(((Number)field(state.getClass(),state,"maxHealth")).floatValue()-5152)<.01
                && Math.abs(((Number)field(state.getClass(),state,"attackDamage")).floatValue()-110.88)<.01,"20-minute siege spike must scale health and damage");
        var second=com.myriad.registry.ModEntities.SIEGE_MINION.get().create(level,net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        level.addFreshEntity(second);
        mob.setPos(a.getX()+3,a.getY(),a.getZ());second.setPos(mob.getX(),mob.getY(),mob.getZ());
        for(int i=0;i<30;i++){NpcNavigation.separate(level,mob);NpcNavigation.separate(level,second);}
        helper.assertTrue(mob.distanceTo(second)>1.3,"Exactly overlapping siege constructs must separate");
        second.discard();
        var saved=LaneSystem.snapshot();LaneSystem.restore(level,saved);
        helper.assertTrue((Boolean)unlocked.invoke(null,Faction.XUANXIAO,LaneSystem.Lane.TOP),"Siege unlock must survive match checkpoint reload");
        helper.assertTrue(MonsterManager.growthMultiplier(0)==1 && MonsterManager.growthMultiplier(2)==1
                && Math.abs(MonsterManager.growthMultiplier(3)-1.03)<.0001 && Math.abs(MonsterManager.growthMultiplier(4)-1.06)<.0001,
                "Monster growth starts at the third respawn and adds three percent each time");
        var health=MonsterManager.class.getDeclaredMethod("initialHealth",MonsterType.class,int.class);health.setAccessible(true);
        helper.assertTrue(Math.abs((Double)health.invoke(null,MonsterType.FROST_HARE,0)-770)<.01,"Ordinary monster must use its declared effective health");
        helper.assertTrue(Math.abs((Double)health.invoke(null,MonsterType.AZURE_HORN_GUARDIAN,0)-10080)<.01,
                "Epic monster must use its declared effective health");
        helper.assertTrue(Math.abs(LaneSystem.heroDamageMultiplier(0) - .8F) < .001F,
                "Opening minions must reduce hero damage by 20 percent");
        helper.assertTrue(LaneSystem.heroDamageMultiplier(24000) < .55F
                && LaneSystem.heroDamageMultiplier(1000000) == .5F,
                "Minion hero protection must grow smoothly and cap at 50 percent");
        var clear=LaneSystem.class.getDeclaredMethod("clearRuntime",net.minecraft.server.level.ServerLevel.class);clear.setAccessible(true);clear.invoke(null,level);
        towerPoints.clear();
    }

    @SuppressWarnings("unchecked")

    private static void verifyTowerAdvance(GameTestHelper helper, BlockPos a) throws ReflectiveOperationException {
        var level = helper.getLevel();
        // The empty structure has no block entries; explicitly remove terrain
        // along the ranged attack ray and provide a walkable test floor.
        for (BlockPos pos : BlockPos.betweenClosed(a.offset(-1,0,-2), a.offset(42,12,12)))
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos pos : BlockPos.betweenClosed(a.offset(-1,-1,-2), a.offset(42,-1,12)))
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        var clear = LaneSystem.class.getDeclaredMethod("clearRuntime", net.minecraft.server.level.ServerLevel.class);
        clear.setAccessible(true); clear.invoke(null,level);
        Class<?> keyType = Class.forName("com.myriad.game.LaneSystem$TowerKey");
        var constructor = keyType.getDeclaredConstructor(Faction.class,LaneSystem.Lane.class,int.class); constructor.setAccessible(true);
        Map<Object,BlockPos> points = (Map<Object,BlockPos>) field(LaneSystem.class,null,"TOWER_POINTS");
        points.clear();
        var spawnTower = LaneSystem.class.getDeclaredMethod("spawnTower",net.minecraft.server.level.ServerLevel.class,keyType,BlockPos.class);
        spawnTower.setAccessible(true);
        for (int tier=1;tier<=3;tier++) {
            Object key=constructor.newInstance(Faction.YOUMING,LaneSystem.Lane.TOP,tier);
            BlockPos pos=a.offset(12+tier*8,0,0); points.put(key,pos); spawnTower.invoke(null,level,key,pos);
        }
        var ordered=LaneSystem.class.getDeclaredMethod("orderedPath",LaneSystem.Lane.class,Faction.class); ordered.setAccessible(true);
        var path=(List<BlockPos>)ordered.invoke(null,LaneSystem.Lane.TOP,Faction.XUANXIAO);
        helper.assertTrue(path.get(1).equals(a.offset(20,0,0)) && path.get(2).equals(a.offset(28,0,0))
                && path.get(3).equals(a.offset(36,0,0)),"Tower order must override the malformed manual route");
        var wave=LaneSystem.class.getDeclaredMethod("spawnWave",net.minecraft.server.level.ServerLevel.class);wave.setAccessible(true);wave.invoke(null,level);
        var units=(Map<?,?>)field(LaneSystem.class,null,"UNITS");
        Object minionState=null,outer=null,inner=null;
        for(Object s:units.values()) {
            if(field(s.getClass(),s,"lane")!=LaneSystem.Lane.TOP)continue;
            if(field(s.getClass(),s,"faction")==Faction.XUANXIAO && field(s.getClass(),s,"kind").toString().equals("RANGED")) minionState=s;
            if(field(s.getClass(),s,"kind").toString().equals("TOWER")){
                if((Integer)field(s.getClass(),s,"towerTier")==1)outer=s;
                if((Integer)field(s.getClass(),s,"towerTier")==2)inner=s;
            }
        }
        Mob minion=(Mob)field(minionState.getClass(),minionState,"entity");
        Mob outerMob=(Mob)field(outer.getClass(),outer,"entity");
        Mob innerMob=(Mob)field(inner.getClass(),inner,"entity");
        for(Object s:units.values())if(!field(s.getClass(),s,"kind").toString().equals("TOWER") && s!=minionState)
            ((Mob)field(s.getClass(),s,"entity")).setPos(a.getX()-60,a.getY(),a.getZ());
        // Remain outside waypoint arrival radius: ranged combat must still commit progress.
        minion.setPos(outerMob.getX()-7.5,outerMob.getY(),outerMob.getZ());
        var tick=LaneSystem.class.getDeclaredMethod("tickMinion",net.minecraft.server.level.ServerLevel.class,Mob.class,minionState.getClass(),long.class);tick.setAccessible(true);
        tick.invoke(null,level,minion,minionState,level.getGameTime()+200);
        helper.assertTrue(outerMob.getUUID().equals(field(minionState.getClass(),minionState,"chaseTarget")),"Minion must select its lane's outer tower");
        helper.assertTrue(((Number)field(outer.getClass(),outer,"health")).floatValue()<8400,"Minion must actually damage the tower; attack="+field(minionState.getClass(),minionState,"lastAttackTick")+", visible="+NpcNavigation.visible(minion,outerMob)+", distance="+minion.distanceTo(outerMob)+", width="+outerMob.getBbWidth());
        units.remove(outerMob.getUUID()); outerMob.discard();
        double before=minion.getX();
        tick.invoke(null,level,minion,minionState,level.getGameTime()+201);
        helper.assertTrue(innerMob.getUUID().equals(field(minionState.getClass(),minionState,"chaseTarget")),"After outer tower dies select inner tower on the same tick");
        helper.assertTrue(minion.getX()>=before && !(Boolean)field(minionState.getClass(),minionState,"returning"),"After tower destruction continue forward without retreat");
        Object blockerState = null;
        for (Object s : units.values()) if (field(s.getClass(),s,"lane") == LaneSystem.Lane.TOP
                && field(s.getClass(),s,"faction") == Faction.YOUMING
                && !field(s.getClass(),s,"kind").toString().equals("TOWER")) { blockerState = s; break; }
        Mob blocker = (Mob)field(blockerState.getClass(),blockerState,"entity");
        blocker.setPos(minion.getX()+3,minion.getY(),minion.getZ());
        tick.invoke(null,level,minion,minionState,level.getGameTime()+240);
        helper.assertTrue(blocker.getUUID().equals(field(minionState.getClass(),minionState,"chaseTarget")),
                "Enemy wave must interrupt an existing tower target");
        units.remove(blocker.getUUID()); blocker.discard();
        tick.invoke(null,level,minion,minionState,level.getGameTime()+241);
        helper.assertTrue(innerMob.getUUID().equals(field(minionState.getClass(),minionState,"chaseTarget")),
                "After the wave is cleared resume attacking the lane tower");
        var routeField = minionState.getClass().getDeclaredField("path"); routeField.setAccessible(true);
        routeField.set(minionState, List.of(a, a.offset(10,0,0), a.offset(10,0,10), a.offset(20,0,10)));
        var waypointField = minionState.getClass().getDeclaredField("waypointIndex"); waypointField.setAccessible(true);
        waypointField.setInt(minionState, 1);
        minion.setPos(a.getX()+9.5,a.getY(),a.getZ()+7.5);
        var advance = LaneSystem.class.getDeclaredMethod("advanceLaneProgress",Mob.class,minionState.getClass());
        advance.setAccessible(true); advance.invoke(null,minion,minionState);
        helper.assertTrue(waypointField.getInt(minionState) == 2,
                "Combat past a corner must rejoin the forward segment instead of revisiting the corner");
        waypointField.setInt(minionState, 1);
        for (BlockPos pos : BlockPos.betweenClosed(a.offset(9,0,8),a.offset(11,3,8)))
            level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        advance.invoke(null,minion,minionState);
        helper.assertTrue(waypointField.getInt(minionState) == 1,
                "A nearby forward segment behind a wall must not skip the current corner");
        Map<LaneSystem.Lane,BlockPos> corners = (Map<LaneSystem.Lane,BlockPos>)field(LaneSystem.class,null,"LANE_CORNERS");
        BlockPos corner = a.offset(10,0,10);
        corners.put(LaneSystem.Lane.TOP, corner);
        var ownRoute = (List<BlockPos>)ordered.invoke(null,LaneSystem.Lane.TOP,Faction.XUANXIAO);
        var enemyRoute = (List<BlockPos>)ordered.invoke(null,LaneSystem.Lane.TOP,Faction.YOUMING);
        helper.assertTrue(ownRoute.indexOf(corner) < ownRoute.indexOf(a.offset(20,0,0))
                && enemyRoute.indexOf(corner) > enemyRoute.indexOf(a.offset(20,0,0)),
                "Both factions must use the same bend between outer towers in opposite directions");
        routeField.set(minionState, List.of(a,corner,a.offset(20,0,10)));
        waypointField.setInt(minionState,1);
        minion.setPos(a.getX()+14.5,a.getY(),a.getZ()+10.5);
        advance.invoke(null,minion,minionState);
        helper.assertTrue(waypointField.getInt(minionState) == 1,
                "Configured bend must not be skipped by projection onto the following segment");
        // Reproduce a destroyed outer tower beyond the bend with the inner tower behind a wall.
        BlockPos deadTowerPoint = a.offset(20,0,10);
        points.put(constructor.newInstance(Faction.YOUMING,LaneSystem.Lane.TOP,1), deadTowerPoint);
        routeField.set(minionState, List.of(a, corner, deadTowerPoint, a.offset(28,0,0)));
        waypointField.setInt(minionState, 2);
        var chase = minionState.getClass().getDeclaredField("chaseTarget"); chase.setAccessible(true); chase.set(minionState,null);
        var scan = minionState.getClass().getDeclaredField("nextTargetScan"); scan.setAccessible(true); scan.setLong(minionState,0);
        minion.setPos(a.getX()+10.5,a.getY(),a.getZ()+10.5);
        for (BlockPos pos : BlockPos.betweenClosed(a.offset(13,0,-1),a.offset(13,3,8)))
            level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        var oldPosition = minion.position();
        for (int step=0;step<6;step++) tick.invoke(null,level,minion,minionState,level.getGameTime()+400+step);
        helper.assertTrue(waypointField.getInt(minionState) == 2 && chase.get(minionState) == null,
                "Destroyed tower waypoint must remain; do not latch onto a tower behind the bend wall");
        helper.assertTrue(minion.position().distanceToSqr(oldPosition) > .01,
                "After the outer tower dies the next wave must move beyond the bend");
        corners.clear();
        clear.invoke(null,level);points.clear();
    }
}
