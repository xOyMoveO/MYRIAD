package com.myriad.game;

import net.minecraft.server.MinecraftServer;

/**
 * 仙凡劫模组全局开关（/myriad mod on|off|status）。
 * 关闭后模组所有功能（技能、伤害重写、汲取、等级、命数、角色等）均不生效，
 * 恢复原版 Minecraft 行为；HUD 等模组界面一并隐藏。
 */
public final class ModState {
    private ModState() {}

    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** 开启新一轮模组玩法前，先回收上一轮遗留的临时实体与服务端技能状态。 */
    public static void setEnabled(boolean value, MinecraftServer server) {
        if (value && !enabled) {
            for (var p : server.getPlayerList().getPlayers()) VanillaSession.capture(p);
            com.myriad.player.PlayerDataProvider.clearModGeneratedState(server);
            ModEntityTracker.clearGenerated(server);
        }
        enabled = value;
    }
}
