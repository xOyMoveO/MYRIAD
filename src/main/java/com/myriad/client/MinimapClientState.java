package com.myriad.client;

import com.myriad.network.NetworkSetup;

/** 客户端最近一次服务端小地图快照。 */
public final class MinimapClientState {
    private MinimapClientState() {}

    private static volatile NetworkSetup.MinimapSnapshotPayload snapshot;

    public static void update(NetworkSetup.MinimapSnapshotPayload payload) {
        snapshot = payload;
    }

    public static NetworkSetup.MinimapSnapshotPayload snapshot() {
        return snapshot;
    }

    public static boolean configured() {
        return snapshot != null && snapshot.configured();
    }

}
