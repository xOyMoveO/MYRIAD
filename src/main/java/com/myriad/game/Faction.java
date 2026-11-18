package com.myriad.game;

import net.minecraft.network.chat.Component;

/** Symmetric teams. The numeric/wire order remains 1=Guardian, 2=Destruction. */
public enum Faction {
    @com.google.gson.annotations.SerializedName(value="guardian",alternate={"GUARDIAN","XUANXIAO","xuanxiao","renjie"})
    GUARDIAN("guardian",0x59C7FF,net.minecraft.ChatFormatting.BLUE),
    @com.google.gson.annotations.SerializedName(value="destruction",alternate={"DESTRUCTION","YOUMING","youming","xianjie"})
    DESTRUCTION("destruction",0xFF625C,net.minecraft.ChatFormatting.RED);

    private final String name;
    private final int rgb;
    private final net.minecraft.ChatFormatting chatColor;

    Faction(String name,int rgb,net.minecraft.ChatFormatting chatColor) {
        this.name = name;this.rgb=rgb;this.chatColor=chatColor;
    }

    public String getName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("faction.myriad." + name).withStyle(style->style.withColor(rgb));
    }
    public int rgb(){return rgb;}
    public int argb(){return 0xFF000000|rgb;}
    public net.minecraft.ChatFormatting chatColor(){return chatColor;}

    /** Old spellings are read aliases only; all output and new saves use current IDs. */
    public static Faction byId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "guardian", "守护", "xuanxiao", "renjie", "玄霄", "玄霄宗" -> GUARDIAN;
            case "destruction", "毁灭", "youming", "xianjie", "幽冥", "幽冥宗" -> DESTRUCTION;
            default -> null;
        };
    }
}
