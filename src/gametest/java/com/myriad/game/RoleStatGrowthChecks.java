package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.rework.AttributeSpecializations;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class RoleStatGrowthChecks {
    // Independent issue #118 table, in the order of the synced attributes below.
    private static double[] perLevel(CharacterType role) {
        return switch (role) {
            case YINGXIAO -> new double[]{19, 0, 6, 5, .030, .35, .30};
            case SULAN -> new double[]{17, 0, 7, 6, .040, .45, .50};
            case SHUO -> new double[]{16, 0, 8, 6, .030, .50, .30};
            case TIANXING -> new double[]{6, 12, 6, 6, .035, .50, .40};
            case KEHENG -> new double[]{4, 15, 6, 5, .025, .40, .50};
            case RIVET -> new double[]{12, 0, 10, 8, .018, .75, .35};
            case CHIXIAO -> new double[]{13, 0, 10, 7, .022, .70, .35};
            case FENJUE -> new double[]{13, 0, 9, 8, .020, .65, .45};
            case YIKE -> new double[]{3, 18, 5, 4, .015, .30, .50};
            case LINGYUAN -> new double[]{4, 14, 6, 6, .025, .50, .35};
            case HANZE -> new double[]{0, 14, 8, 8, .015, .65, .45};
            case XUANCHEN -> new double[]{0, 19, 5, 6, .015, .35, .55};
            case LUOXIA -> new double[]{20, 0, 6, 4, .045, .35, .30};
            case QINGLUO -> new double[]{0, 15, 7, 8, .012, .40, .60};
            case YUNSHENG -> new double[]{0, 16, 7, 7, .015, .40, .60};
        };
    }

    private static final List<Holder<Attribute>> ATTRIBUTES = List.of(
            ModAttributes.PHYSICAL_ATTACK, ModAttributes.SPELL_POWER,
            ModAttributes.PHYSICAL_DEFENSE, ModAttributes.MAGIC_RESISTANCE,
            ModAttributes.ATTACK_SPEED, ModAttributes.HEALTH_REGEN, ModAttributes.MANA_REGEN);
    private static final Identifier TEMPORARY = Identifier.parse("myriad:test_role_growth_modifier");

    private RoleStatGrowthChecks() {}

    static double attackGrowth(CharacterType role, int level) {
        return perLevel(role)[0] * (level - 1);
    }

    static double spellGrowth(CharacterType role, int level) {
        return perLevel(role)[1] * (level - 1);
    }

    static void verify(GameTestHelper h, ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        for (var role : CharacterType.playableValues()) {
            data.setLevel(1);
            data.setXp(0);
            PlayerDataProvider.chooseCharacter(player, role);
            h.assertTrue(role.getId().equals(data.getCharacterId()), "Selected " + role.getId());
            double[] base = {role == CharacterType.LUOXIA ? 100 : 65, 30, 45, 45, .70, 2, 4};
            double[] gains = perLevel(role);
            double lastBudget = 0;
            for (int level = 1; level <= 20; level++) {
                if (level > 1) LevelSystem.addXp(player, data, LevelSystem.xpToNext(level - 1));
                h.assertTrue(data.getLevel() == level, "Reached " + role.getId() + " level " + level);
                double budget = 0;
                for (int index = 0; index < ATTRIBUTES.size(); index++) {
                    var attr = ATTRIBUTES.get(index);
                    double observed = player.getAttributeValue(attr);
                    near(h, observed, base[index] + gains[index] * (level - 1),
                            role.getId() + " level " + level + " " + attr.value().getDescriptionId());
                    if (index < 4) budget += observed;
                }
                if (level > 1) near(h, budget - lastBudget, 30, role.getId() + " combat budget");
                lastBudget = budget;
                near(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), .10, "Level-independent movement speed");
            }
        }
        verifyPurchases(h, player);
        System.out.println("[MYRIAD-ROLE-GROWTH] PASS: 15 roles through 20 levels, training level changes and capped purchases");
    }

    private static void verifyPurchases(GameTestHelper h, ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        PlayerDataProvider.chooseCharacter(player, CharacterType.LUOXIA);
        TrainingActions.apply(player, "level:1", true);
        boolean freeBefore = TrainingMode.isNoGoldCost();
        TrainingMode.setNoGoldCost(false);
        var attack = player.getAttribute(ModAttributes.PHYSICAL_ATTACK);
        try {
            data.setGold(50_000);
            h.assertTrue(FirstGroupGrowth.buy(player, data, "strength")
                    && FirstGroupGrowth.buy(player, data, "wisdom"), "Paid strength and wisdom purchases");
            for (int rank = 0; rank < 4; rank++)
                h.assertTrue(FirstGroupGrowth.buy(player, data, "speed"), "Buy opening attack speed rank " + (rank + 1));
            h.assertTrue(AttributeSpecializations.choose(player, data, AttributeSpecializations.Option.FLURRY, 4),
                    "Choose Flurry at four purchases");
            attack.addTransientModifier(new AttributeModifier(TEMPORARY, .05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

            int wallet = data.getGold();
            TrainingActions.apply(player, "level:20", true);
            near(h, player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK), (480 + 15) * 1.05, "Strength and temporary attack after level jump");
            near(h, player.getAttributeValue(ModAttributes.SPELL_POWER), 50, "Wisdom after level jump");
            near(h, player.getAttributeValue(ModAttributes.ATTACK_SPEED), 1.555 + 4 * .06 + .08, "Flurry after level jump");
            h.assertTrue(data.getGold() == wallet && data.firstGroupAttribute("strength") == 1
                    && data.firstGroupAttribute("wisdom") == 1 && data.firstGroupAttribute("speed") == 4
                    && data.specialization("speed") == AttributeSpecializations.Option.FLURRY.choice,
                    "Level changes preserve gold, purchased ranks and the chosen specialization");

            for (int rank = 4; rank < 7; rank++) {
                int before = data.getGold();
                h.assertTrue(FirstGroupGrowth.buy(player, data, "speed")
                        && data.getGold() == before - FirstGroupGrowth.price(rank), "Charge exact rank price");
            }
            near(h, player.getAttributeValue(ModAttributes.ATTACK_SPEED), 2, "Last paid rank reaches exactly 2.0");
            wallet = data.getGold();
            h.assertTrue(!FirstGroupGrowth.buy(player, data, "speed")
                    && data.getGold() == wallet && data.firstGroupAttribute("speed") == 7,
                    "Capped rank grants nothing, even when the next rank would upgrade Flurry");

            TrainingActions.apply(player, "level:1", true);
            h.assertTrue(FirstGroupGrowth.buy(player, data, "speed") && data.firstGroupAttribute("speed") == 8,
                    "Lower training level reopens the eighth purchase");
            near(h, player.getAttributeValue(ModAttributes.ATTACK_SPEED), .70 + 8 * .06 + .12, "Upgraded Flurry");
            TrainingActions.apply(player, "level:10", true);
            near(h, player.getAttributeValue(ModAttributes.ATTACK_SPEED), .70 + 9 * .045 + 8 * .06 + .12,
                    "Level ten preserves upgraded Flurry");
        } finally {
            attack.removeModifier(TEMPORARY);
            TrainingMode.setNoGoldCost(freeBefore);
        }
    }

    private static void near(GameTestHelper h, double actual, double expected, String detail) {
        h.assertTrue(Math.abs(actual - expected) < .00001, detail + ": expected " + expected + ", observed " + actual);
    }
}
