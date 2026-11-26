package com.myriad.client;

import com.myriad.network.NetworkSetup;

/** 客户端最近一次服务端小地图快照。 */
public final class MinimapClientState {
    private MinimapClientState() {}

    private static volatile NetworkSetup.MinimapSnapshotPayload snapshot;

    private static net.minecraft.client.multiplayer.ClientLevel level;

    public static void update(NetworkSetup.MinimapSnapshotPayload payload) {
        level = payload == null ? null : net.minecraft.client.Minecraft.getInstance().level;
        snapshot = payload;
    }

    public static NetworkSetup.MinimapSnapshotPayload snapshot() {
        return level == net.minecraft.client.Minecraft.getInstance().level ? snapshot : null;
    }

    public static boolean configured() {
        var current = snapshot();
        return current != null && current.configured();
    }

}
