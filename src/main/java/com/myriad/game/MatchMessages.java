package com.myriad.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 统一局内系统消息的颜色与字重，避免不同系统各自使用难以辨认的默认白字。 */
public final class MatchMessages {
    public enum Tone {
        INFO(ChatFormatting.AQUA, false),
        SUCCESS(ChatFormatting.GREEN, true),
        WARNING(ChatFormatting.RED, true),
        OBJECTIVE(ChatFormatting.GOLD, true),
        COMBAT(ChatFormatting.LIGHT_PURPLE, true),
        NEUTRAL(ChatFormatting.GRAY, false);

        private final ChatFormatting color;
        private final boolean bold;

        Tone(ChatFormatting color, boolean bold) {
            this.color = color;
            this.bold = bold;
        }
    }

    private MatchMessages() { }

    public static MutableComponent style(Tone tone, Component message) {
        Style style = Style.EMPTY.withColor(tone.color).withBold(tone.bold);
        return Component.empty().append(message.copy().withStyle(style));
    }

    public static void send(ServerPlayer player, Tone tone, Component message) {
        player.sendSystemMessage(style(tone, message));
    }

    public static void broadcast(MinecraftServer server, Tone tone, Component message) {
        if (server != null) server.getPlayerList().broadcastSystemMessage(style(tone, message), false);
    }
}
