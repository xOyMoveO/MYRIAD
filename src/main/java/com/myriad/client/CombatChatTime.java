package com.myriad.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Client-only metadata carried by the same GuiMessage that vanilla stores, filters and deletes. */
public record CombatChatTime(Instant time, boolean sent) {
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter NORMAL = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss XXX", Locale.ROOT);

    public static CombatChatTime from(Instant signedTime, Instant receivedTime) {
        return signedTime == null ? new CombatChatTime(receivedTime, false) : new CombatChatTime(signedTime, true);
    }

    public String clock(ZoneId zone, boolean shortClock) {
        return (shortClock ? SHORT : NORMAL).format(time.atZone(zone));
    }

    public String fullTime(ZoneId zone) {
        return FULL.format(time.atZone(zone));
    }

    public interface Holder {
        CombatChatTime myriad$time();
        void myriad$setTime(CombatChatTime time);
    }

    public interface Receiver {
        void myriad$addPlayerMessage(Component text, MessageSignature signature, GuiMessageTag tag, CombatChatTime time);
    }
}
