package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/** Checks the applied opening stats, not just enum declarations. No balance/win-rate claim. */
final class CharacterBalanceChecks {
    private CharacterBalanceChecks() { }

    static void verify(GameTestHelper helper) {
        CharacterType[] roster = { CharacterType.YINGXIAO, CharacterType.TIANXING, CharacterType.FENJUE, CharacterType.XUANCHEN, CharacterType.SULAN, CharacterType.LINGYUAN, CharacterType.HANZE, CharacterType.QINGLUO, CharacterType.CHIXIAO, CharacterType.YUNSHENG, CharacterType.LIGE, CharacterType.GUANLAN, CharacterType.WUXIANG, CharacterType.FEIYU, CharacterType.WANYUE, CharacterType.XUANJIA, CharacterType.LUOHENG, CharacterType.SHENLI, CharacterType.ZHECHUAN };
        double[][] expected = {
            { 1260, 56, 0, 700, 56, 48, 0.58, 0.0966, 9.1, 3.5 }, // yingxiao
            { 1512, 52, 0, 910, 68, 48, 0.54, 0.092, 9.1, 4.55 }, // tianxing
            { 1554, 48, 0, 980, 72, 48, 0.5, 0.0874, 9.8, 4.2 }, // fenjue
            { 1260, 12, 48, 910, 44, 56, 0.5, 0.092, 14, 3.15 }, // xuanchen
            { 1218, 48, 0, 910, 48, 44, 0.62, 0.1012, 9.8, 2.8 }, // sulan
            { 1344, 52, 0, 700, 56, 44, 0.58, 0.09476, 0, 3.15 }, // lingyuan
            { 1260, 8, 40, 945, 48, 56, 0.5, 0.092, 14, 3.5 }, // hanze
            { 1302, 16, 40, 945, 48, 56, 0.5, 0.092, 14.7, 3.5 }, // qingluo
            { 1512, 56, 0, 910, 64, 48, 0.56, 0.09384, 9.1, 4.2 }, // chixiao
            { 1260, 12, 48, 980, 44, 56, 0.5, 0.09476, 15.4, 3.15 }, // yunsheng
            { 1218, 52, 0, 700, 40, 44, 0.66, 0.09177, 10.5, 2.8 }, // lige
            { 1134, 8, 60, 1015, 36, 52, 0.48, 0.092, 16.1, 2.8 }, // guanlan
            { 1218, 60, 0, 700, 48, 44, 0.6, 0.09752, 9.8, 2.8 }, // wuxiang
            { 1302, 56, 0, 700, 52, 48, 0.6, 0.09752, 9.8, 3.15 }, // feiyu
            { 1176, 48, 0, 700, 40, 44, 0.64, 0.09752, 11.2, 2.8 }, // wanyue
            { 1638, 36, 0, 700, 84, 60, 0.46, 0.0874, 9.8, 5.6 }, // xuanjia
            { 1159.2, 12, 56, 980, 40, 52, 0.48, 0.092, 15.4, 2.8 }, // luoheng
            { 1218, 52, 0, 700, 44, 44, 0.64, 0.090022, 11.2, 2.8 }, // shenli
            { 1218, 56, 0, 735, 48, 44, 0.6, 0.0966, 10.5, 2.8 }, // zhechuan
        };
        // Final per-level growth values after moving global multipliers into role definitions.
        double[][] growth = {
            { 472.5, 22.848, 0, 56, 6, 4, 0.028, 0.0046 }, // yingxiao
            { 604.8, 22.848, 0, 42, 8, 2, 0.028, 0.000736 }, // tianxing
            { 661.5, 26.656, 0, 56, 8, 4, 0.0224, 0.000736 }, // fenjue
            { 491.4, 3.808, 23.8, 112, 4, 6, 0.0168, 0.000828 }, // xuanchen
            { 415.8, 19.04, 0, 70, 4, 4, 0.0336, 0.001012 }, // sulan
            { 567, 26.656, 0, 0, 6, 4, 0.028, 0.00092 }, // lingyuan
            { 453.6, 7.616, 28.56, 112, 4, 6, 0.0168, 0.000736 }, // hanze
            { 453.6, 7.616, 19.04, 105, 5, 6, 0.0168, 0.000736 }, // qingluo
            { 585.9, 26.656, 0, 56, 7, 4, 0.028, 0.000736 }, // chixiao
            { 396.9, 5.712, 23.8, 119, 4, 7, 0.014, 0.00092 }, // yunsheng
            { 434.7, 22.848, 0, 70, 4, 4, 0.036, 0.000874 }, // lige
            { 189, 3.808, 33.32, 126, 3.6, 6, 0.0112, 0.000644 }, // guanlan
            { 453.6, 28.56, 0, 49, 5.4, 3.6, 0.0308, 0.00092 }, // wuxiang
            { 491.4, 25.8944, 0, 56, 5.6, 4, 0.0252, 0.00092 }, // feiyu
            { 368.55, 21.3248, 0, 84, 3.8, 4.6, 0.0336, 0.001012 }, // wanyue
            { 396.9, 15.232, 0, 56, 10, 5, 0.014, 0.00046 }, // xuanjia
            { 274.05, 3.808, 30.94, 119, 3.6, 5.4, 0.01232, 0.000736 }, // luoheng
            { 387.45, 22.0864, 0, 77, 4, 4.4, 0.032, 0.000874 }, // shenli
            { 378, 24.752, 0, 63, 4.6, 4, 0.02, 0.000828 }, // zhechuan
        };
        var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        var data = PlayerDataProvider.get(player);
        helper.assertTrue(CharacterType.values().length == roster.length + 1, "Every implemented hero needs a balance baseline");
        CharacterType.applyInitialAttributes(player, CharacterType.MINGZHU);
        helper.assertTrue(player.getAttributeValue(ModAttributes.SPELL_POWER) == 56
                && CharacterType.MINGZHU.growthHp() == 302.4, "Mingzhu uses the revised fragile-mage baseline");
        for (int i = 0; i < roster.length; i++) {
            CharacterType type = roster[i];
            // Applied first-batch growth is checked by FirstGroupRegressionChecks instead.
            if (com.myriad.character.rework.FirstGroupSkills.active(type.getId())) continue;
            data.setCharacterId(type.getId());
            data.setLevel(1);
            CharacterType.applyInitialAttributes(player, type);
            LevelSystem.applyLevelAttributes(player, 1);
            PlayerHealthSystem.fill(player);
            double[] applied = {
                data.getModMaxHealth(), player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK),
                player.getAttributeValue(ModAttributes.SPELL_POWER), player.getAttributeValue(ModAttributes.MAX_MANA),
                player.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE), player.getAttributeValue(ModAttributes.MAGIC_RESISTANCE),
                player.getAttributeValue(ModAttributes.ATTACK_SPEED), player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                player.getAttributeValue(ModAttributes.MANA_REGEN), player.getAttributeValue(ModAttributes.HEALTH_REGEN)
            };
            for (int k = 0; k < applied.length; k++) {
                double expectedApplied = expected[i][k];
                helper.assertTrue(Math.abs(applied[k] - expectedApplied) < .0001,
                        type.getId() + " opening stat " + k + " must reach player attributes");
            }
            double[] actualGrowth = { type.growthHp(), type.growthAd(), type.growthSp(), type.growthMp(),
                    type.growthPd(), type.growthMr(), type.growthAs(), type.growthMs() };
            for (int k = 0; k < actualGrowth.length; k++) {
                helper.assertTrue(Math.abs(actualGrowth[k] - growth[i][k]) < .000001,
                        type.getId() + " growth " + k + " expected " + growth[i][k] + " but was " + actualGrowth[k]);
            }
            helper.assertTrue(player.getAttributeValue(ModAttributes.CRIT_DAMAGE) == 130
                    && player.getAttributeValue(ModAttributes.CRIT_CHANCE) == 0,
                    type.getId() + " must retain global initial critical stats");
            double balancedBaseHp = CharacterType.balancedInitialHp(type);
            player.setHealth((float) (balancedBaseHp / 2));
            data.setMana((float) (expected[i][3] / 2));
            float hp = player.getHealth(), mana = data.getMana();
            data.setLevel(2);
            LevelSystem.applyLevelAttributes(player, 2);
            helper.assertTrue(Math.abs(data.getModMaxHealth() - (balancedBaseHp
                    + growth[i][0])) < .01
                    && Math.abs(player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK)
                    - (expected[i][1] + growth[i][1])) < .0001,
                    type.getId() + " must add the existing growth exactly once");
            helper.assertTrue(player.getHealth() == hp && data.getMana() == mana,
                    type.getId() + " level-up must not refill health or mana");
            data.setLevel(15);
            LevelSystem.applyLevelAttributes(player, 15);
            helper.assertTrue(Math.abs(data.getModMaxHealth() - (balancedBaseHp
                    + growth[i][0] * 14)) < .01,
                    type.getId() + " level 15 must retain uncapped mod health");
        }
    }
}
