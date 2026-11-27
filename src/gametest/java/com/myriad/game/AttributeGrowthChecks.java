package com.myriad.game;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class AttributeGrowthChecks {
    private AttributeGrowthChecks() {}

    static void verify(GameTestHelper helper, ServerPlayer player, PlayerData data) {
        var savedState = new PlayerData();
        savedState.copyStateFrom(data);
        var position = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        boolean free = TrainingMode.isNoGoldCost(), noCooldown = TrainingMode.isNoCooldown();
        try {
            verifyPurchases(helper, player, data);
            verifyCleave(helper, player, data);
        } finally {
            TrainingMode.setNoGoldCost(free);
            TrainingMode.setNoCooldown(noCooldown);
            AbilityRuntime.clear(player);
            data.copyStateFrom(savedState);
            CharacterType.applyInitialAttributes(player, CharacterType.byId(data.getCharacterId()));
            FirstGroupGrowth.initialize(player, data);
            player.setPos(position);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
        System.out.println("[MYRIAD-ATTRIBUTE-V1] PASS: V1 prices, uncapped purchases, wallet, save/clone, free ranks, overflow, Haste and live Mountain Cleave damage");
    }

    private static void verifyPurchases(GameTestHelper helper, ServerPlayer player, PlayerData data) {
        int[] firstTen = {360, 432, 518, 622, 746, 895, 1074, 1289, 1547, 1856};
        for (int rank = 0; rank < firstTen.length; rank++) {
            helper.assertTrue(FirstGroupGrowth.price(rank) == firstTen[rank], "Exact quote at rank " + rank);
        }
        TrainingMode.setNoGoldCost(false);
        data.setGold(20000);
        for (int rank = 0; rank < 11; rank++) {
            helper.assertTrue(FirstGroupGrowth.buy(player, data, "strength"), "Paid Strength purchase at rank " + rank);
        }
        for (int rank = 0; rank < 5; rank++) {
            helper.assertTrue(FirstGroupGrowth.buy(player, data, "wisdom"), "Paid Wisdom purchase at rank " + rank);
        }
        helper.assertTrue(FirstGroupGrowth.buy(player, data, "defense") && data.getGold() == 5396,
                "Eleven Strength, five Wisdom and one Defense cost 14604 gold");
        var clone = new PlayerData();
        clone.copyStateFrom(data);
        var encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        for (var restored : new PlayerData[]{clone, decoded}) {
            helper.assertTrue(restored.firstGroupAttribute("strength") == 11 && restored.firstGroupAttribute("wisdom") == 5
                    && restored.firstGroupAttribute("defense") == 1 && restored.getGold() == 5396,
                    "Player clone and attachment round trip preserve ranks beyond the old limits and exact wallet");
        }
        var legacy = PlayerData.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(
                "{\"gold\":1234,\"firstGroupAttributes\":{\"strength\":10,\"wisdom\":5}}")).getOrThrow();
        helper.assertTrue(legacy.getGold() == 1234 && legacy.firstGroupAttribute("strength") == 10
                && legacy.firstGroupAttribute("wisdom") == 5 && legacy.firstGroupAttribute("defense") == 0
                && FirstGroupGrowth.price(legacy.firstGroupAttribute("strength")) == 2227,
                "Legacy balances and ranks load unchanged and retained ranks use V1 quotes");

        data.setGold(0);
        TrainingMode.setNoGoldCost(true);
        helper.assertTrue(FirstGroupGrowth.buy(player, data, "speed") && data.getGold() == 0
                && data.firstGroupAttribute("speed") == 1 && FirstGroupGrowth.price(data.firstGroupAttribute("speed")) == 432,
                "Free training purchases advance the same rank used for future quotes");
        data.setFirstGroupAttribute("speed", 86);
        data.setGold(Integer.MAX_VALUE);
        TrainingMode.setNoGoldCost(false);
        helper.assertTrue(FirstGroupGrowth.price(86) == 2318908332L && !FirstGroupGrowth.buy(player, data, "speed")
                && data.getGold() == Integer.MAX_VALUE && data.firstGroupAttribute("speed") == 86,
                "Prices above the wallet range retain their exact quote and never wrap into a paid purchase");
        helper.assertTrue(FirstGroupGrowth.price(207) == 8835158659165832047L
                && FirstGroupGrowth.price(208) == FirstGroupGrowth.PRICE_OVERFLOW, "Last representable quote precedes overflow");
        data.setFirstGroupAttribute("speed", 208);
        helper.assertTrue(!FirstGroupGrowth.buy(player, data, "speed") && data.getGold() == Integer.MAX_VALUE,
                "A quote above the integer price range cannot debit the wallet");
        TrainingMode.setNoGoldCost(true);
        helper.assertTrue(FirstGroupGrowth.buy(player, data, "speed") && data.firstGroupAttribute("speed") == 209
                && data.getGold() == Integer.MAX_VALUE, "A free purchase is valid even after paid prices exceed the represented range");
        data.setFirstGroupAttribute("strength", Integer.MAX_VALUE - 1);
        helper.assertTrue(FirstGroupGrowth.buy(player, data, "strength")
                && data.firstGroupAttribute("strength") == Integer.MAX_VALUE
                && player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) > 65
                && !FirstGroupGrowth.buy(player, data, "strength"), "Large stat modifiers stay positive and the stored count cannot wrap");
    }

    private static void verifyCleave(GameTestHelper helper, ServerPlayer player, PlayerData data) {
        FirstGroupGrowth.ATTRIBUTES.forEach(key -> data.setFirstGroupAttribute(key, 0));
        data.setCharacterId("chixiao");
        CharacterType.applyInitialAttributes(player, CharacterType.CHIXIAO);
        FirstGroupGrowth.initialize(player, data);
        player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        TrainingMode.setNoCooldown(false);
        var cleave = FirstGroupSkills.node("chixiao", 2);
        var level = helper.getLevel();
        var testOrigin = new BlockPos(4, 275, 4);
        var start = helper.absolutePos(testOrigin);
        var origin = Vec3.atBottomCenterOf(start);
        CombatChunks.prepare(level, start);
        CombatChunks.prepare(level, start.south(3));
        var oldBlocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int z = 0; z <= 3; z++) {
            for (int y = 0; y <= 2; y++) {
                var position = start.offset(0, y, z);
                oldBlocks.put(position, level.getBlockState(position));
                level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            }
        }
        player.setPos(origin);
        player.setYRot(0);
        player.setXRot(0);
        var target = helper.spawn(EntityTypes.PIG, testOrigin);
        target.setPos(origin.add(0, 0, 3));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        try {
            helper.assertTrue(player.hasLineOfSight(target), "The isolated Cleave target is visible in the fixture lane");
            int[][] builds = {{1, 0, 0, 0}, {1, 1, 0, 0}, {1, 0, 1, 0}, {1, 3, 0, 20}, {3, 5, 9, 21}};
            float[] damage = {98f, 105.5f, 98f, 120.5f, 191.525f};
            for (int index = 0; index < builds.length; index++) {
                int[] build = builds[index];
                AbilityRuntime.clear(player);
                data.setSkillLevel(cleave.getId(), build[0]);
                data.setFirstGroupAttribute("strength", build[1]);
                data.setFirstGroupAttribute("wisdom", build[2]);
                data.setFirstGroupAttribute("cooldown", build[3]);
                FirstGroupGrowth.apply(player, data);
                // Changing any non-purchased attack source must not affect the skill either.
                player.getAttribute(ModAttributes.PHYSICAL_ATTACK).setBaseValue(65 + 11 * index);
                data.setCooldown(cleave.getId(), 0);
                data.setMana(700);
                target.setHealth(1000);
                target.damageCooldownTime = 0;
                float before = target.getHealth();
                var cast = AbilityRuntime.request(player, data, cleave, 0);
                helper.assertTrue(cast != null && cast.committed(), "Mountain Cleave commits at build " + index);
                float actualDamage = before - target.getHealth();
                helper.assertTrue(Math.abs(actualDamage - damage[index]) < .02,
                        "Mountain Cleave build " + index + " expected " + damage[index] + " damage but got " + actualDamage
                                + " from " + before + " HP, candidates=" + cast.enemies(player.position(), 6).size()
                                + ", line of sight=" + player.hasLineOfSight(target) + ", defense="
                                + DamageCalc.defenseValue(target, DamageCalc.Kind.PHYSICAL));
                int expectedCooldown = build[3] == 0 ? 180 : 72;
                helper.assertTrue(data.getCooldown(cleave.getId()) == expectedCooldown,
                        "Cleave's actual cooldown applies the 60 percent effect limit at twenty or more Haste");
                helper.assertTrue(!target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                        "The cleave applies damage without adding control");
            }
            helper.assertTrue(cleave.evaluatedDescription(1, 10_000, 1).contains("105.5"),
                    "Presented damage uses the same purchased Strength formula");
        } finally {
            target.discard();
            oldBlocks.forEach(level::setBlockAndUpdate);
        }
    }
}
