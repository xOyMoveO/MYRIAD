package com.myriad.client;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 客户端 DPS 显示：累加服务端确认的实际伤害，并维护最近 20 tick 的滚动窗口。 */
public final class DpsTracker {
    private static final int WINDOW_TICKS = 20;
    private static final ArrayDeque<DamageSample> SAMPLES = new ArrayDeque<>();
    private static UUID owner;
    private static int lastTick = Integer.MIN_VALUE;

    private record DamageSample(int tick, int amount) { }

    private DpsTracker() { }

    /** 收到实际伤害飘字包时记录；包只会发送给造成该次伤害的玩家。 */
    public static void record(int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || amount <= 0) return;
        int now = prepare(mc);
        SAMPLES.addLast(new DamageSample(now, amount));
        prune(now);
    }

    /** 固定显示在屏幕左下角，数值为最近一秒滚动窗口内的实际伤害总和。 */
    public static void render(GuiGraphicsExtractor gui, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int now = prepare(mc);
        prune(now);
        int dps = 0;
        for (DamageSample sample : SAMPLES) dps += sample.amount();

        Component text = Component.translatable("hud.myriad.dps", dps);
        int x = 7;
        int y = mc.getWindow().getGuiScaledHeight() - 18;
        int width = font.width(text) + 10;
        gui.fill(x - 3, y - 3, x + width, y + 11, 0xA610131A);
        gui.fill(x - 3, y - 3, x - 1, y + 11, dps > 0 ? 0xFFFF7043 : 0xFF59616D);
        gui.text(font, text, x + 2, y, dps > 0 ? 0xFFFFB067 : 0xFFB8BEC8);
    }

    private static int prepare(Minecraft mc) {
        UUID current = mc.player.getUUID();
        int now = mc.player.tickCount;
        if (!Objects.equals(owner, current) || now < lastTick) {
            SAMPLES.clear();
            owner = current;
        }
        lastTick = now;
        return now;
    }

    private static void prune(int now) {
        while (!SAMPLES.isEmpty() && now - SAMPLES.peekFirst().tick() >= WINDOW_TICKS) {
            SAMPLES.removeFirst();
        }
    }
}
