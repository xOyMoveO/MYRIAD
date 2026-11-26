package com.myriad.client;

import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.network.LuoxiaBowInputPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;

/** Read the rebound attack key without relying on repeated vanilla startAttack clicks. */
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
        if (sent && !available(mc)) {
            send(LuoxiaBow.CANCEL);
            stopPose(mc, true);
            sent = false;
        }
        if (down && !wasDown) press(mc);
        if (!down && sent) {
            send(LuoxiaBow.RELEASE);
            stopPose(mc, false);
            sent = false;
        }
        wasDown = down;
    }

    private static void stopPose(Minecraft mc, boolean cancel) {
        LuoxiaPoseClient.letGo(cancel);
        if (mc.player != null && mc.player.getUseItem().is(ModItems.GUIXIA_BOW.get())) mc.player.stopUsingItem();
    }

    private static void send(int action) {
        Packets.sendToServer(new LuoxiaBowInputPayload(action, gesture));
    }
}
