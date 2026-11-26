package com.myriad.combat;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Short-lived action-bar explanations. Higher priority may replace a recent lower priority. */
public enum InputFailure {
    RECOVERING("input.myriad.recovering", 1),
    COOLDOWN("input.myriad.cooldown", 2),
    MANA("message.myriad.not_enough_mana", 2),
    UNLEARNED("message.myriad.skill_not_learned", 2),
    POINT("message.myriad.g1.invalid_point", 2),
    ATTACK_TARGET("input.myriad.attack_target", 2),
    TARGET("message.myriad.no_skill_target", 2),
    LINK("input.myriad.link", 2),
    CHANNEL("input.myriad.channel", 3),
    STUN("input.myriad.stun", 4),
    ROOT("input.myriad.root", 4),
    SILENCE("message.myriad.silenced", 4);

    private final String key;
    private final int priority;
    private static final Map<ServerPlayer, Window> windows = new WeakHashMap<>();

    InputFailure(String key, int priority) {
        this.key = key;
        this.priority = priority;
    }

    public Component text() { return Component.translatable(key); }

    public static InputFailure forKey(String key) {
        for (var reason : values()) if (reason.key.equals(key)) return reason;
        return null;
    }

    public void send(ServerPlayer player) {
        if (player.connection != null && windows.computeIfAbsent(player, ignored -> new Window())
                .allow(this, player.tickCount)) player.sendSystemMessage(text(), true);
    }

    public static final class Window {
        private static final int INTERVAL = 12;
        private final long[] shown = new long[values().length];
        private long replaceAfter;
        private int priority;
        public Window() { Arrays.fill(shown, Long.MIN_VALUE); }
        public boolean allow(InputFailure reason, long now) {
            if (now < shown[reason.ordinal()] || now < replaceAfter && reason.priority < priority) return false;
            shown[reason.ordinal()] = replaceAfter = now + INTERVAL;
            priority = reason.priority;
            return true;
        }
    }
}
