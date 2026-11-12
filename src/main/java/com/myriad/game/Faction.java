package com.myriad.game;

import net.minecraft.network.chat.Component;

/** 对称 PvP 的两个宗门；旧英文 ID 仅在读取入口中兼容。 */
public enum Faction {
    XUANXIAO("xuanxiao"),
    YOUMING("youming");

    private final String name;

    Faction(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("faction.myriad." + name);
    }

    /** 新 ID 为 xuanxiao/youming；旧 ID 只用于读取兼容。 */
    public static Faction byId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "xuanxiao", "renjie", "玄霄", "玄霄宗" -> XUANXIAO;
            case "youming", "xianjie", "幽冥", "幽冥宗" -> YOUMING;
            default -> null;
        };
    }
}
