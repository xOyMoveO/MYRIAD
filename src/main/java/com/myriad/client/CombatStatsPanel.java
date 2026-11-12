package com.myriad.client;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 本局个人承伤与受治疗统计；服务端只发送已经实际结算的增量。 */
public final class CombatStatsPanel {
    private static final long WINDOW_MILLIS = 30_000L;
    private static final ArrayDeque<Entry> EVENTS = new ArrayDeque<>();

    private CombatStatsPanel() { }

    public static void record(int kind, int amount, String source) {
        if (amount <= 0) return;
        prune(System.currentTimeMillis());
        EVENTS.addLast(new Entry(System.currentTimeMillis(), kind, amount,
                source == null || source.isBlank() ? "other" : source));
    }

    public static void reset() {
        EVENTS.clear();
    }

    public static void render(GuiGraphicsExtractor gui, Font font) {
        long now = System.currentTimeMillis();
        prune(now);
        int playerDamage = 0;
        int nonPlayerDamage = 0;
        Map<String, Integer> healing = new LinkedHashMap<>();
        for (Entry entry : EVENTS) {
            if (entry.kind == com.myriad.combat.CombatTelemetry.DAMAGE_PLAYER) playerDamage += entry.amount;
            else if (entry.kind == com.myriad.combat.CombatTelemetry.DAMAGE_NON_PLAYER) nonPlayerDamage += entry.amount;
            else if (entry.kind == com.myriad.combat.CombatTelemetry.HEAL)
                healing.merge(entry.source, entry.amount, Integer::sum);
        }
        Minecraft mc = Minecraft.getInstance();
        int width = 154;
        int x = mc.getWindow().getGuiScaledWidth() - width - 8;
        int y = 88;
        int healingTotal = healing.values().stream().mapToInt(Integer::intValue).sum();
        int rows = 5 + Math.min(6, healing.size());
        int height = 10 + rows * 11;
        gui.fill(x, y, x + width, y + height, 0xC20A1019);
        gui.fill(x, y, x + 2, y + height, 0xFF66D7E5);
        gui.text(font, Component.translatable("hud.myriad.combat_stats"), x + 7, y + 5, 0xFFFFE8A8);
        int line = y + 17;
        gui.text(font, Component.translatable("hud.myriad.damage_from_players", playerDamage),
                x + 7, line, 0xFFFF7A72);
        line += 11;
        gui.text(font, Component.translatable("hud.myriad.damage_from_non_players", nonPlayerDamage),
                x + 7, line, 0xFFFFB46B);
        line += 11;
        gui.text(font, Component.translatable("hud.myriad.healing_received", healingTotal),
                x + 7, line, 0xFF70E29A);
        line += 12;
        int shown = 0;
        for (Map.Entry<String, Integer> entry : healing.entrySet()) {
            if (shown++ >= 6) break;
            Component source = Component.translatable("hud.myriad.heal_source." + entry.getKey());
            gui.text(font, Component.literal("  ").append(source).append(": +" + entry.getValue()),
                    x + 7, line, 0xFFB8F3C8);
            line += 11;
        }
        if (healing.isEmpty()) {
            gui.text(font, Component.translatable("hud.myriad.heal_source.none"),
                    x + 9, line, 0xFF7F929A);
        }
    }

    private static void prune(long now) {
        while (!EVENTS.isEmpty() && now - EVENTS.peekFirst().createdAt > WINDOW_MILLIS) EVENTS.removeFirst();
    }

    private record Entry(long createdAt, int kind, int amount, String source) { }
}
