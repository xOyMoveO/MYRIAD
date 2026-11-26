package com.myriad.client;

import com.myriad.MyriadClient;
import com.myriad.character.CharacterType;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.CanYueSkill;
import com.myriad.network.CrescentInputPayload;
import com.myriad.network.Packets;
import com.myriad.network.CrescentInputPayload.Edge;
import net.minecraft.client.Minecraft;

public final class CrescentInput {
    private static int gesture;
    private static Object connection;
    private static boolean down, holdSent;
    private static int pressedTick;
    private CrescentInput() {}

    public static boolean press(Minecraft mc, int slot) {
        var data = ClientDataHolder.currentPlayerSnapshot(mc);
        if (slot != CharacterType.SLOT_R || data == null || !"shuo".equals(data.characterId())
                || !(FirstGroupSkills.resolve(CharacterType.SHUO, slot, data.skillLevels()) instanceof CanYueSkill)) return false;
        if (!down) {
            connection = mc.getConnection();
            down = true;
            holdSent = false;
            pressedTick = mc.player.tickCount;
            Packets.sendToServer(new CrescentInputPayload(++gesture, Edge.PRESS));
        }
        return true;
    }

    public static void release() {
        var mc = Minecraft.getInstance();
        if (down && !holdSent && mc.player != null && connection == mc.getConnection()) {
            Edge choice = mc.player.tickCount - pressedTick >= 3 ? Edge.HOLD : Edge.TAP;
            Packets.sendToServer(new CrescentInputPayload(gesture, choice));
        }
        down = false;
    }

    public static void tick(Minecraft mc) {
        if (!down) return;
        if (mc.player == null || mc.getConnection() != connection || mc.gui.screen() != null
                || !MyriadClient.KEY_SKILL_R.isDown() || !"shuo".equals(ClientDataHolder.characterId())) { release(); return; }
        if (!holdSent && mc.player.tickCount-pressedTick >= 3) {
            holdSent = true;
            Packets.sendToServer(new CrescentInputPayload(gesture, Edge.HOLD));
        }
    }
}
