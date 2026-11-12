package com.myriad.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 当前观察者的玩家可见列表；仅客户端渲染层读取。 */
public final class FogOfWarClient {
    private FogOfWarClient() {}

    private static boolean enabled;
    private static Set<Integer> visiblePlayerIds = Set.of();

    public static void update(boolean fogEnabled, List<Integer> visibleIds) {
        enabled = fogEnabled;
        visiblePlayerIds = fogEnabled ? Set.copyOf(new HashSet<>(visibleIds)) : Set.of();
    }

    public static boolean shouldRenderPlayer(int entityId) {
        return !ClientDataHolder.modEnabled() || !enabled || visiblePlayerIds.contains(entityId);
    }
}
