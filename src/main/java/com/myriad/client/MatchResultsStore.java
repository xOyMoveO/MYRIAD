package com.myriad.client;

import com.myriad.client.gui.MatchResultsScreen;
import com.myriad.network.NetworkSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端保存最近一局服务端权威结算；新局开始或断开连接时清除。 */
public final class MatchResultsStore {
    private static NetworkSetup.MatchResultsPayload latest;

    private MatchResultsStore() { }

    public static void receive(NetworkSetup.MatchResultsPayload payload) { latest = payload; }
    public static void clear() { latest = null; }
    public static NetworkSetup.MatchResultsPayload latest() { return latest; }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (latest == null) {
            if (mc.player != null) mc.gui.chatListener().handleSystemMessage(
                    Component.translatable("message.myriad.results_unavailable"), false);
            return;
        }
        mc.setScreenAndShow(new MatchResultsScreen(latest));
    }
}
