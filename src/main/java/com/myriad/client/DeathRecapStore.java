package com.myriad.client;

import com.myriad.network.DeathRecapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Connection-local last death only. Does not subscribe to, reset, or reuse the 30-second stats panel. */
public final class DeathRecapStore {
    private DeathRecapStore() {}
    private static DeathRecapPayload latest;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    public static DeathRecapPayload latest() { return world == Minecraft.getInstance().level ? latest : null; }
    public static void receive(DeathRecapPayload payload) {
        if (!payload.present()) clear();
        else { world = Minecraft.getInstance().level; latest = payload; }
    }
    public static void clear() {
        latest = null;
        world = null;
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof com.myriad.client.gui.DeathRecapScreen) mc.setScreenAndShow(null);
    }
    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ClientDataHolder.modEnabled()) return;
        var recap = latest();
        if (recap == null) {
            mc.gui.chatListener().handleSystemMessage(Component.translatable("recap.myriad.unavailable"), false); return;
        }
        mc.setScreenAndShow(new com.myriad.client.gui.DeathRecapScreen(recap));
    }
}
