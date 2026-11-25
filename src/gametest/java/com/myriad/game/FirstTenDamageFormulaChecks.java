package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

import static com.myriad.combat.DamageCalc.Kind.MAGIC;
import static com.myriad.combat.DamageCalc.Kind.PHYSICAL;
import static com.myriad.combat.DamageCalc.Kind.TRUE;

/** Expected zero-purchase damage from the old server and replacement purchase deltas. */
final class FirstTenDamageFormulaChecks {
    private record Expected(String id, Kind kind, int hits, double zero, double maxZero,
                            double strength, double wisdom, double maxWisdom) {}

    private record Build(int strength, int wisdom) {}
    private static final List<Build> BUILDS = List.of(new Build(0, 0), new Build(1, 0), new Build(0, 1), new Build(12, 12));
    private static final List<String> ROLES = List.of("yingxiao", "tianxing", "fenjue", "xuanchen", "sulan",
            "lingyuan", "hanze", "qingluo", "chixiao", "yunsheng");

    private static final List<Expected> SEGMENTS = List.of(
            new Expected("g1_yingxiao_pb", PHYSICAL, 1, 37.5, 85.5, 3.75, 0, 0),
            new Expected("g1_yingxiao_n1", PHYSICAL, 1, 61.5, 121.5, 7.5, 0, 0),
            new Expected("g1_yingxiao_ma", PHYSICAL, 1, 94, 214, 7.5, 0, 0),
            new Expected("g1_yingxiao_mb", PHYSICAL, 1, 74.5, 182.5, 7.5, 0, 0),
            new Expected("g1_yingxiao_ua", PHYSICAL, 1, 219, 359, 12, 0, 0),
            new Expected("g1_tianxing_pa", TRUE, 1, 7.2, 16.8, 0, 4.8, 11.2),
            new Expected("g1_tianxing_pa", MAGIC, 1, 26, 58, 0, 4, 4),
            new Expected("g1_tianxing_pb", MAGIC, 1, 44, 92, 0, 6, 6),
            new Expected("g1_tianxing_pb", MAGIC, 1, 22, 46, 0, 3, 3),
            new Expected("g1_tianxing_n1", PHYSICAL, 1, 53.5, 101.5, 7.5, 0, 0),
            new Expected("g1_tianxing_n2", PHYSICAL, 1, 79.5, 151.5, 7.5, 0, 0),
            new Expected("g1_tianxing_n2", PHYSICAL, 1, 27.825, 53.025, 2.625, 0, 0),
            new Expected("g1_tianxing_ma", MAGIC, 4, 24.5, 72.5, 0, 3, 3),
            new Expected("g1_tianxing_mb", MAGIC, 1, 123.5, 267.5, 0, 19, 19),
            new Expected("g1_tianxing_ua", MAGIC, 6, 52.5, 88.5, 0, 5, 5),
            new Expected("g1_tianxing_ub", PHYSICAL, 1, 245, 405, 12, 0, 0),
            new Expected("g1_fenjue_pa", TRUE, 3, 1.8, 3, 0, 1.2, 2),
            new Expected("g1_fenjue_n1", PHYSICAL, 2, 43, 79, 3.75, 0, 0),
            new Expected("g1_fenjue_n2", PHYSICAL, 1, 47, 95, 7.5, 0, 0),
            new Expected("g1_fenjue_ma", PHYSICAL, 4, 31, 85, 1.875, 0, 0),
            new Expected("g1_fenjue_mb", PHYSICAL, 1, 143, 293, 7.5, 0, 0),
            new Expected("g1_fenjue_ua", PHYSICAL, 1, 170, 290, 6.66667, 0, 0),
            new Expected("g1_fenjue_ua", PHYSICAL, 4, 36, 60, 1.33333, 0, 0),
            new Expected("g1_fenjue_ub", PHYSICAL, 4, 36, 60, 3, 0, 0),
            new Expected("g1_xuanchen_pb", MAGIC, 1, 44, 92, 0, 6, 6),
            new Expected("g1_xuanchen_n1", MAGIC, 1, 61.5, 121.5, 0, 11, 11),
            new Expected("g1_xuanchen_ua", MAGIC, 4, 54, 94, 0, 6, 6),
            new Expected("g1_xuanchen_ub", MAGIC, 1, 203, 333, 0, 22, 22),
            new Expected("g1_sulan_pa", PHYSICAL, 1, 45.5, 93.5, 3.75, 0, 0),
            new Expected("g1_sulan_pb", PHYSICAL, 1, 34, 74, 3.75, 0, 0),
            new Expected("g1_sulan_n1", PHYSICAL, 1, 68, 128, 7.5, 0, 0),
            new Expected("g1_sulan_n2", PHYSICAL, 1, 32.5, 72.5, 7.5, 0, 0),
            new Expected("g1_sulan_mb", PHYSICAL, 2, 58.5, 148.5, 3.75, 0, 0),
            new Expected("g1_sulan_ua", PHYSICAL, 1, 196, 326, 12, 0, 0),
            new Expected("g1_sulan_ub", PHYSICAL, 1, 109.5, 179.5, 12, 0, 0),
            new Expected("g2_lingyuan_pa", MAGIC, 4, 13.6, 25.6, 0, 2.4, 2.4),
            new Expected("g2_lingyuan_pb", MAGIC, 1, 45.5, 93.5, 0, 7, 7),
            new Expected("g2_lingyuan_n1", PHYSICAL, 1, 81.5, 153.5, 7.5, 0, 0),
            new Expected("g2_lingyuan_ma", MAGIC, 3, 42.5, 102.5, 0, 5, 5),
            new Expected("g2_lingyuan_mb", MAGIC, 4, 42.5, 102.5, 0, 5, 5),
            new Expected("g2_lingyuan_ua", MAGIC, 6, 62.5, 122.5, 0, 5, 5),
            new Expected("g2_lingyuan_ub", MAGIC, 1, 336, 536, 0, 24, 24),
            new Expected("g2_hanze_pa", MAGIC, 1, 31, 71, 0, 4, 4),
            new Expected("g2_hanze_n1", MAGIC, 3, 31, 63, 0, 4, 4),
            new Expected("g2_hanze_ma", MAGIC, 1, 136, 286, 0, 14, 14),
            new Expected("g2_hanze_mb", MAGIC, 1, 101.5, 233.5, 0, 11, 11),
            new Expected("g2_hanze_ua", MAGIC, 6, 51.6, 101.6, 0, 4.4, 4.4),
            new Expected("g2_hanze_ub", MAGIC, 1, 102, 162, 0, 8, 8),
            new Expected("g2_qingluo_n2", MAGIC, 1, 63.5, 127.5, 0, 9, 9),
            new Expected("g2_chixiao_pa", TRUE, 1, 32.5, 72.5, 0, 5, 5),
            new Expected("g2_chixiao_n1", PHYSICAL, 1, 93, 181, 7.5, 0, 0),
            new Expected("g2_chixiao_n2", PHYSICAL, 1, 62, 122, 7.5, 0, 0),
            new Expected("g2_chixiao_ma", PHYSICAL, 1, 132.5, 282.5, 7.5, 0, 0),
            new Expected("g2_chixiao_mb", PHYSICAL, 1, 101.5, 233.5, 7.5, 0, 0),
            new Expected("g2_chixiao_ua", PHYSICAL, 1, 195.5, 325.5, 12, 0, 0),
            new Expected("g2_chixiao_ub", PHYSICAL, 3, 54, 94, 4, 0, 0),
            new Expected("g2_yunsheng_n1", MAGIC, 1, 68.5, 140.5, 0, 9, 9),
            new Expected("g2_yunsheng_n2", MAGIC, 2, 44, 92, 0, 6, 6),
            new Expected("g2_yunsheng_ma", MAGIC, 1, 78.5, 198.5, 0, 9, 9),
            new Expected("g2_yunsheng_ua", MAGIC, 4, 52.5, 92.5, 0, 5, 5)
    );

    static void verify(GameTestHelper helper) {
        var player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        var data = PlayerDataProvider.get(player);
        try {
            for (String role : ROLES) {
                data.setCharacterId(role);
                var type = CharacterType.byId(role);
                var candidates = FirstGroupSkills.candidates(role);
                helper.assertTrue(candidates.size() == 8, "Eight registered candidates for " + role);
                candidates.forEach(skill -> verifyLearning(helper, type, skill));
                for (int characterLevel : new int[]{1, 20}) {
                    data.setLevel(characterLevel);
                    FirstGroupGrowth.initialize(player, data);
                    for (Build build : BUILDS) {
                        data.setFirstGroupAttribute("strength", build.strength());
                        data.setFirstGroupAttribute("wisdom", build.wisdom());
                        FirstGroupGrowth.apply(player, data);
                        double attack = player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                        double spellPower = player.getAttributeValue(ModAttributes.SPELL_POWER);
                        verifyBasic(helper, type, build, attack, spellPower);
                        for (AbilitySkill skill : candidates) {
                            var expected = SEGMENTS.stream().filter(segment -> segment.id().equals(skill.getId())).toList();
                            var formulas = skill.damageFormulas();
                            helper.assertTrue(formulas.size() == expected.size(), "Exact segment count for " + skill.getId());
                            for (int rank = 1; rank <= skill.maxRank(); rank++) {
                                data.setSkillLevel(skill.getId(), rank);
                                var cast = AbilityRuntime.context(player, skill);
                                helper.assertTrue(cast.rank == rank, "Server uses learned rank for " + skill.getId());
                                for (int i = 0; i < expected.size(); i++) {
                                    var segment = expected.get(i);
                                    var formula = formulas.get(i);
                                    String label = skill.getId() + " segment " + (i + 1) + " rank " + rank + " " + build;
                                    helper.assertTrue(formula.kind() == segment.kind() && formula.maxHits() == segment.hits(),
                                            label + " preserves damage type and hit count");
                                    double t = (rank - 1.0) / (skill.maxRank() - 1);
                                    double zero = segment.zero() + t * (segment.maxZero() - segment.zero());
                                    double wisdom = segment.wisdom() + t * (segment.maxWisdom() - segment.wisdom());
                                    double damage = zero + segment.strength() * build.strength() + wisdom * build.wisdom();
                                    near(helper, formula.value(cast), damage, label + " authoritative Cast formula");
                                    near(helper, formula.value(rank, attack, spellPower, build.strength(), build.wisdom()), damage,
                                            label + " preview matches authoritative Cast formula");
                                    near(helper, formula.perPoint(rank), segment.strength() + wisdom, label + " per point");
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            AbilityRuntime.clear(player);
            player.discard();
        }
        System.out.println("[MYRIAD-DAMAGE-FIRST-TEN] PASS: 80 candidates, " + SEGMENTS.size()
                + " damage segments, 10 basic attacks, ranks and locked branches, two character levels and four builds");
    }

    private static void verifyLearning(GameTestHelper helper, CharacterType role, AbilitySkill skill) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int rank = 1; rank <= skill.maxRank(); rank++) {
            int level = skill.isUltimate() ? 4 * rank : rank;
            helper.assertTrue(FirstGroupSkills.learnError(role.getId(), ranks, 20, level, skill.getId()) == null,
                    "Can learn " + skill.getId() + " at rank " + rank + " / level " + level);
            if (level > 1) {
                helper.assertTrue("message.myriad.g1.level_gate".equals(
                        FirstGroupSkills.learnError(role.getId(), ranks, 20, level - 1, skill.getId())),
                        "Rejects early rank " + rank + " for " + skill.getId());
            }
            ranks.put(skill.getId(), rank);
        }
        helper.assertTrue("message.myriad.skill_max".equals(FirstGroupSkills.learnError(role.getId(), ranks, 20, 20, skill.getId())),
                "Rank cap for " + skill.getId());
        int opposite = FirstGroupSkills.opposite(skill.index());
        if (opposite >= 0) {
            var choice = FirstGroupSkills.node(role.getId(), opposite);
            helper.assertTrue("message.myriad.g1.branch_locked".equals(
                    FirstGroupSkills.learnError(role.getId(), ranks, 20, 20, choice.getId())),
                    "Opposite branch remains locked after learning " + skill.getId());
        }
        if (!skill.isPassive()) helper.assertTrue(FirstGroupSkills.resolve(role, skill.slot(), ranks) == skill,
                "Actual cast slot resolves " + skill.getId());
    }

    private static void verifyBasic(GameTestHelper helper, CharacterType role, Build build, double attack, double spellPower) {
        if (VanillaMeleeAttack.usesVanilla(role)) {
            near(helper, attack * VanillaMeleeAttack.damageScale(1), 65 + 15 * build.strength(),
                    role.getId() + " full-strength melee before random and jump modifiers " + build);
        } else {
            double expected = role == CharacterType.HANZE ? 78 + 12 * build.wisdom() : 65 + 10 * build.wisdom();
            near(helper, MagicBullet.rawDamage(role, spellPower), expected, role.getId() + " raw magic projectile " + build);
            near(helper, MagicBullet.rawDamage(role, spellPower + 40), expected + (role == CharacterType.HANZE ? 24 : 20),
                    role.getId() + " magic projectile retains legal spell-power buffs " + build);
        }
    }

    private static void near(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < .002, label + " expected " + expected + " got " + actual);
    }
}
