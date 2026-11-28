package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import com.myriad.registry.ModSounds;
import com.myriad.network.UiFeedbackPayload.ShopAccess;

public final class UiFeedback {
    private static ShopAccess shopAccess = ShopAccess.NONE;
    public static ShopAccess shopAccess() { return shopAccess; }
    public static void resetShopAccess() { shopAccess = ShopAccess.NONE; }
    public static void receive(int kind) {
        var mc = Minecraft.getInstance();
        if(com.myriad.combat.CombatImpactProfile.isPayload(kind)){
            CombatImpactAudio.receive(kind);return;
        }
        if (kind == 5 || kind == 6) {
            if(!ClientDataHolder.modEnabled())return;
            HitFeedback.hit(kind == 6);
            return;
        }
        var access = ShopAccess.fromKind(kind);
        if (access != null) { shopAccess = access; return; }
        if ((kind == 1 || kind == 2) && mc.gui.screen() instanceof com.myriad.client.gui.ShopScreen shop)
            shop.purchaseComplete();
        if (kind == 0 || kind == 1) mc.getSoundManager().play(SimpleSoundInstance.forUI(
                kind == 0 ? ModSounds.UI_LEVEL_UP.get() : ModSounds.UI_PURCHASE.get(), kind == 0 ? 1.15f : 1f, .5f));
    }
}
