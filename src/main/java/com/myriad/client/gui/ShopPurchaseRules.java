package com.myriad.client.gui;

import com.myriad.character.CharacterType;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.game.TacticalSupplySystem;
import com.myriad.network.UiFeedbackPayload.ShopAccess;

/** Shared button availability and error messages for the two purchase screens. */
public final class ShopPurchaseRules {
    private ShopPurchaseRules() {}

    public static String attributeError(ShopAccess access, CharacterType role, String key,
            int rank, int gold, boolean free) {
        return attributeError(access, role, 1, key, rank, gold, free, 0, FirstGroupGrowth.OPENING_MOVEMENT_SPEED_GAIN);
    }

    public static String attributeError(ShopAccess access, CharacterType role, int level, String key,
            int rank, int gold, boolean free, int specialization, double movementPerPoint) {
        if (!access.attributes()) return "screen.myriad.shop.unavailable";
        return FirstGroupGrowth.purchaseError(role, level, key, rank, gold, free, specialization, movementPerPoint);
    }

    public static String supplyError(ShopAccess access, String id, int gold, boolean free) {
        if (!access.supplies()) return "message.myriad.shop_base_only";
        return !free && gold < TacticalSupplySystem.supplyPrice(id) ? "message.myriad.equip_no_gold" : null;
    }
}
