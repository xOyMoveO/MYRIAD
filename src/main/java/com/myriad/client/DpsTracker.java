package com.myriad.client;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** 客户端 DPS 显示：累加服务端确认的实际伤害，并维护最近 20 tick 的滚动窗口。 */
public final class DpsTracker {
    private static final int WINDOW_TICKS = 20;
    private static final ArrayDeque<DamageSample> SAMPLES = new ArrayDeque<>();
    private static UUID owner;
    private static Object world;
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

    /** 数值为最近一秒滚动窗口内的实际伤害总和；默认给个人战斗面板让位。 */
    public static void render(GuiGraphicsExtractor gui, Font font, CombatHudLayout.Layout combat) {
        Minecraft mc = Minecraft.getInstance();
        var settings = HudLayoutConfig.state(HudLayoutConfig.Element.DPS);
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof ChatScreen
                || !settings.visible()) return;
        int now = prepare(mc);
        prune(now);
        int dps = 0;
        for (DamageSample sample : SAMPLES) dps += sample.amount();

        Component text = Component.translatable("hud.myriad.dps", dps);
        int width = font.width(text) + 13;
        var bounds = position(gui.guiWidth(), gui.guiHeight(), width, combat, settings);
        if (bounds == null) return;
        gui.pose().pushMatrix();
        gui.pose().translate(bounds.x(), bounds.y());
        gui.pose().scale(settings.scale());
        gui.fill(0, 0, width, 14, 0xA610131A);
        gui.fill(0, 0, 2, 14, dps > 0 ? 0xFFFF7043 : 0xFF59616D);
        gui.text(font, text, 5, 3, dps > 0 ? 0xFFFFB067 : 0xFFB8BEC8);
        gui.pose().popMatrix();
    }

    public static CombatHudLayout.Bounds position(int width, int height, int rawWidth,
            CombatHudLayout.Layout combat, HudLayoutConfig.State settings) {
        var element = HudLayoutConfig.Element.DPS;
        int drawnWidth = (int)Math.ceil(rawWidth * settings.scale());
        int drawnHeight = (int)Math.ceil(14 * settings.scale());
        int x = Math.round(settings.x() * width + (4 - element.defaultX() * width) * settings.scale());
        int y = Math.round(settings.y() * height + (height - 21 - element.defaultY() * height) * settings.scale());
        if (combat != null && settings.x() == element.defaultX() && settings.y() == element.defaultY()) {
            x = combat.bounds().x();
            y = combat.bounds().y() - CombatHudLayout.GAP - drawnHeight;
            if (y < 0) return null;
        }
        return new CombatHudLayout.Bounds(x, y, drawnWidth, drawnHeight);
    }

    private static int prepare(Minecraft mc) {
        UUID current = mc.player.getUUID();
        int now = mc.player.tickCount;
        if (!Objects.equals(owner, current) || world != mc.level || now < lastTick) {
            SAMPLES.clear();
            owner = current;
            world = mc.level;
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
