package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.myriad.character.CharacterType;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** 旧符石存档、属性、护盾迁移及升级回归。 */
final class RuneRemovalChecks {
    private RuneRemovalChecks() { }

    static void run(GameTestHelper h, ServerPlayer player) {
        String oldJson = """
                {"characterId":"yingxiao","level":1,"gold":321,
                 "runes":["lingmai","hudungaizai","xuezuqiyue","lunhuizhixi"],
                 "pendingRuneDraws":5,"pendingRuneOffer":["yiji","juejing","quanneng"],
                 "reincarnating":true,"stolenAd":100,"ultimateBuffUntil":999999,
                 "buffs":{"rune_last_stand":60,"rune_control_seen":3,"test_skill":40,"beast_rune":90}}
                """;
        var migrated = PlayerData.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(oldJson)).getOrThrow();
        String saved = new Gson().toJson(migrated);
        h.assertTrue(!saved.contains("\"runes\"") && !saved.contains("pendingRune")
                && !saved.contains("reincarnating") && !saved.contains("stolenAd"),
                "Removed rune fields cannot survive save/load");
        h.assertTrue(migrated.getBuff("rune_last_stand") == 0 && migrated.getBuff("test_skill") == 40
                && migrated.getBuff("beast_rune") == 90 && migrated.getGold() == 321,
                "Migration removes only rune state, preserving hero and monster buffs");
        var data = PlayerDataProvider.get(player);
        data.copyStateFrom(migrated);
        data.setBuff("rune_last_stand", 60);
        CharacterType.applyInitialAttributes(player, CharacterType.YINGXIAO);
        LevelSystem.applyLevelAttributes(player, 1);
        PlayerHealthSystem.fill(player);
        player.setHealth(50);
        var ad = player.getAttribute(ModAttributes.PHYSICAL_ATTACK);
        var oldRune = Identifier.parse("myriad:rune_new_combo_as");
        var retained = Identifier.parse("myriad:test_role_modifier");
        ad.addTransientModifier(new AttributeModifier(oldRune, 100, AttributeModifier.Operation.ADD_VALUE));
        ad.addTransientModifier(new AttributeModifier(retained, 7, AttributeModifier.Operation.ADD_VALUE));
        ModShieldSystem.grant(player, "test_skill", 100, 100);
        ModShieldSystem.grant(player, "rune_zhenyue_lingke", 50, 100);
        h.assertTrue(ModShieldSystem.value(player) == 150, "No rune shield amplification remains");
        player.getPersistentData().putLong("myriad_fear_until", Long.MAX_VALUE);
        RetiredRuneCleanup.clean(player);
        h.assertTrue(!ad.hasModifier(oldRune) && ad.hasModifier(retained)
                && data.getBuff("rune_last_stand") == 0
                && player.getPersistentData().getLongOr("myriad_fear_until", 0) == 0,
                "Legacy rune modifiers/control removed without touching hero modifiers");
        h.assertTrue(ModShieldSystem.value(player) == 100 && player.getHealth() == 50,
                "Keep skill shield and never heal during migration");
        boolean modeBefore = ModState.isEnabled();
        ModState.setEnabled(false);
        try {
            ad.addTransientModifier(new AttributeModifier(oldRune, 100, AttributeModifier.Operation.ADD_VALUE));
            RetiredRuneCleanup.clean(player);
            h.assertTrue(!ad.hasModifier(oldRune) && ad.hasModifier(retained),
                    "Vanilla mode also removes only legacy rune modifiers");
        } finally {
            ModState.setEnabled(modeBefore);
        }
        player.heal(20);
        h.assertTrue(player.getHealth() == 70, "Legacy rune healing penalty cannot apply");
        data.setMana(123);
        LevelSystem.addXp(player, data, LevelSystem.xpToNext(data.getLevel()));
        h.assertTrue(data.getLevel() == 2 && player.getHealth() == 70 && data.getMana() == 123
                && !new Gson().toJson(data).contains("pendingRune"),
                "Level-up still grants progress without rune draws or resource refill");
        var payload = new com.myriad.network.NetworkSetup.PlayerDataPayload(
                9, 87, 600, 321, 123, 0, 1, 2, 3, 2, "yingxiao",
                1, 2, 3, 4, 5, true, false, false, false,
                java.util.Map.of("test_skill", 40), java.util.Map.of(), 4, 0,
                java.util.Map.of("test_skill", 1), true, 6, true, -1,
                java.util.List.of(), java.util.Map.of("test_attribute", 1.5),
                20, 2, 3, 4, 70, 2000, 100);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.myriad.network.NetworkSetup.PlayerDataPayload.STREAM_CODEC.encode(buffer, payload);
            h.assertTrue(com.myriad.network.NetworkSetup.PlayerDataPayload.STREAM_CODEC.decode(buffer).equals(payload),
                    "Rune-free player payload preserves exact field order and types");
        } finally {
            buffer.release();
        }
        System.out.println("[MYRIAD-RUNES-REMOVED] PASS: old saves, modifiers, shields, healing, level-up, wire roundtrip");
        ad.removeModifier(retained);
        ModShieldSystem.clearFor(player);
        PlayerDataProvider.resetForHostSession(player);
    }
}
