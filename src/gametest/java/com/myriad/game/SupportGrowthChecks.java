package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.List;

final class SupportGrowthChecks {
    private record Expected(String id, String effect, double first, double last, double wisdom) {}
    private static final List<Expected> EFFECTS = List.of(
            new Expected("g2_chixiao_pb", "shield", 92, 197.6, 8),
            new Expected("g2_chixiao_ub", "shield", 251, 426, 14),
            new Expected("g1_fenjue_ub", "shield", 204, 379, 16),
            new Expected("g2_hanze_pb", "shield", 75.5, 171.5, 7),
            new Expected("g2_hanze_n2", "shield", 169.5, 369.5, 13),
            new Expected("g2_hanze_ub", "shield", 251, 426, 14),
            new Expected("keheng_hengbu", "shield", 84, 156, 6),
            new Expected("keheng_cuopaibu", "shield", 39.5, 89.5, 3),
            new Expected("g2_lingyuan_pb", "shield", 52.5, 124.5, 5),
            new Expected("g2_lingyuan_n2", "shield", 116.5, 266.5, 11),
            new Expected("g2_qingluo_pa", "heal", 25.4, 63.8, 3.6),
            new Expected("g2_qingluo_pb", "heal", 15, 39, 2),
            new Expected("g2_qingluo_n1", "heal", 85, 195, 10),
            new Expected("g2_qingluo_n2", "heal", 42.5, 102.5, 5),
            new Expected("g2_qingluo_ma", "shield", 90.5, 242.3, 7),
            new Expected("g2_qingluo_mb", "shield", 171, 433.5, 14),
            new Expected("g2_qingluo_mb", "heal", 24.5, 77, 3),
            new Expected("g2_qingluo_ua", "heal", 51.6, 97.6, 4.4),
            new Expected("g2_qingluo_ub", "heal", 112, 199.5, 8),
            new Expected("g2_qingluo_ub", "shield", 219.5, 382, 13),
            new Expected("rivet_station_armor", "shield", 80, 166.4, 8),
            new Expected("rivet_emergency_strut", "shield", 110, 275, 10),
            new Expected("rivet_hold_protocol", "shield", 160, 285, 12),
            new Expected("shuo_qiaofan", "shield", 57.5, 107.5, 5),
            new Expected("g1_xuanchen_pa", "shield", 47.5, 119.5, 5),
            new Expected("g1_xuanchen_n2", "shield", 108, 233, 12),
            new Expected("g1_xuanchen_mb", "shield", 70.5, 194.7, 7),
            new Expected("yike_steady_barrier", "shield", 72.5, 144.5, 5),
            new Expected("g1_yingxiao_ub", "shield", 351.9, 649.4, 30.6),
            new Expected("g2_yunsheng_pa", "shield", 52.5, 124.5, 5),
            new Expected("g2_yunsheng_pb", "heal", 22.5, 56.1, 3),
            new Expected("g2_yunsheng_n1", "heal", 49, 119, 6),
            new Expected("g2_yunsheng_ma", "shield", 74, 198.2, 6),
            new Expected("g2_yunsheng_mb", "shield", 125, 312.5, 10),
            new Expected("g2_yunsheng_ua", "heal", 52.5, 93.9, 5),
            new Expected("g2_yunsheng_ub", "shield", 229.5, 392, 13));

    static void verify(GameTestHelper h, ServerPlayer player) {
        int count = FirstGroupSkills.ROLES.stream().flatMap(role -> FirstGroupSkills.candidates(role).stream())
                .mapToInt(skill -> skill.supportAmounts().size()).sum();
        h.assertTrue(count == EFFECTS.size(), "Every support amount has a reference");
        for (var expected : EFFECTS) {
            var skill = FirstGroupSkills.byId(expected.id);
            var amount = skill.supportAmounts().stream().filter(candidate -> candidate.effect().equals(expected.effect))
                    .findFirst().orElseThrow();
            for (int rank = 1; rank <= skill.maxRank(); rank++) {
                double zero = expected.first + (expected.last - expected.first) * (rank - 1) / (skill.maxRank() - 1);
                for (int wisdom : new int[]{0, 1, 4}) {
                    double power = 30 + 20 * wisdom;
                    h.assertTrue(Math.abs(amount.value(rank, power) - zero - expected.wisdom * wisdom) < .002,
                            "Support amount at rank " + rank + ": " + expected.id);
                }
            }
        }
        var data = PlayerDataProvider.get(player);
        data.setFaction(Faction.GUARDIAN);
        data.setLevel(20);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30);
        data.setCharacterId("qingluo");
        var heal = FirstGroupSkills.node("qingluo", 2);
        data.setSkillLevel(heal.getId(), 5);
        player.setHealth(100);
        heal.release(AbilityRuntime.context(player, heal));
        h.assertTrue(Math.abs(player.getHealth() - 295) < .01, "Verdant Dew healed 195 actual HP at rank five");
        data.setCharacterId("xuanchen");
        var shield = FirstGroupSkills.node("xuanchen", 3);
        data.setSkillLevel(shield.getId(), 5);
        shield.release(AbilityRuntime.context(player, shield));
        h.assertTrue(Math.abs(ModShieldSystem.value(player) - 233) < .01, "Rank-five Ink Shield grants 233");
        ModShieldSystem.clearFor(player);
    }
}
