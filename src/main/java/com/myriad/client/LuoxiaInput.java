package com.myriad.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.client.platform.KeyInput;
import com.myriad.network.LuoxiaBowInputPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;

/** Observe both attack edges in order; tick sampling also catches a lost or cancelled binding. */
public final class LuoxiaInput {
    private static boolean wasDown, sent;
    private static Object connection;
    private static int gesture;
    private LuoxiaInput() {}

    private static boolean available(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.gui.screen() == null && !RespawnSpectatorView.isActive()
                && ClientDataHolder.modEnabled() && "luoxia".equals(ClientDataHolder.characterId())
                && ClientDataHolder.placementType() == 0
                && mc.player.getMainHandItem().is(ModItems.GUIXIA_BOW.get());
    }

    public static void press(Minecraft mc) {
        if (connection != mc.getConnection()) return;
        if (!mc.options.keyAttack.isDown() || wasDown || !available(mc)) return;
        wasDown = true;
        sent = true;
        gesture++;
        boolean predict = !LuoxiaPoseClient.exclusive(mc.player) && !ClientDataHolder.isChanneling()
                && ClientDataHolder.buffTicks("g1_stun") == 0 && ClientDataHolder.canFight();
        LuoxiaPoseClient.begin(gesture, predict);
        send(LuoxiaBow.PRESS);
        if (predict) mc.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    public static void binding(Minecraft mc, KeyInput input) {
        if (connection == null || connection != mc.getConnection() || !input.matches(mc.options.keyAttack)) return;
        if (input.getAction() == InputConstants.PRESS) press(mc);
        if (input.getAction() == InputConstants.RELEASE) {
            finish(mc, !available(mc) || !mc.isWindowActive());
            wasDown = false;
        }
    }

    public static void tick(Minecraft mc) {
        LuoxiaPoseClient.tick(mc);
        if (connection != mc.getConnection()) {
            gesture = 0;
            connection = mc.getConnection();
            wasDown = false;
            sent = false;
        }
        if (connection == null) return;
        boolean down = mc.options.keyAttack.isDown();
        if (sent && !available(mc)) finish(mc, true);
        if (down && !wasDown) press(mc);
        if (!down) finish(mc, !mc.isWindowActive());
        wasDown = down;
    }

    private static void finish(Minecraft mc, boolean cancel) {
        if (!sent) return;
        sent = false;
        send(cancel ? LuoxiaBow.CANCEL : LuoxiaBow.RELEASE);
        LuoxiaPoseClient.letGo(cancel);
        if (mc.player != null && mc.player.getUseItem().is(ModItems.GUIXIA_BOW.get())) mc.player.stopUsingItem();
    }

    private static void send(int action) {
        Packets.sendToServer(new LuoxiaBowInputPayload(action, gesture));
    }
}
