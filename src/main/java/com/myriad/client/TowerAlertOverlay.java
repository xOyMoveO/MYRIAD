package com.myriad.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 屏幕上方的己方防御塔失守提示；持续三秒，末段渐隐。 */
public final class TowerAlertOverlay {
    private static final int DURATION_TICKS = 60;
    private static String laneId = "mid";
    private static int tier = 1;
    private static long startTick = Long.MIN_VALUE;

    private TowerAlertOverlay() { }

    public static void show(String newLaneId, int newTier) {
        Minecraft minecraft = Minecraft.getInstance();
        laneId = newLaneId == null || newLaneId.isBlank() ? "mid" : newLaneId;
        tier = Math.max(1, Math.min(3, newTier));
        startTick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    public static void clear() {
        startTick = Long.MIN_VALUE;
    }

    public static void render(GuiGraphicsExtractor gui, Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || startTick == Long.MIN_VALUE) return;
        long age = minecraft.level.getGameTime() - startTick;
        if (age < 0 || age >= DURATION_TICKS) {
            clear();
            return;
        }

        float fade = age < 45 ? 1.0F : Math.max(0.0F, (DURATION_TICKS - age) / 15.0F);
        int alpha = Math.round(220 * fade);
        int textAlpha = Math.round(255 * fade);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int y = Math.max(20, Math.round(screenHeight * .12F) - 18)
                + (KillAnnouncementOverlay.isActive() ? 62 : 0);

        Component lane = Component.translatable("lane.myriad." + laneId);
        Component title = Component.translatable("hud.myriad.ally_tower_destroyed", lane, tier)
                .copy().withStyle(ChatFormatting.BOLD);
        Component subtitle = Component.translatable("hud.myriad.tower_warning");
        int width = Math.max(190, Math.max(font.width(title), font.width(subtitle)) + 34);
        int left = centerX - width / 2;
        int right = centerX + width / 2;

        gui.fill(left, y, right, y + 38, (alpha << 24) | 0x18090C);
        gui.fill(left, y, right, y + 2, (textAlpha << 24) | 0xE44C55);
        gui.fill(left, y + 36, right, y + 38, (textAlpha << 24) | 0x8C2930);
        gui.fill(left - 2, y + 7, left, y + 31, (textAlpha << 24) | 0xE5B85B);
        gui.fill(right, y + 7, right + 2, y + 31, (textAlpha << 24) | 0xE5B85B);
        gui.text(font, title, centerX - font.width(title) / 2, y + 8,
                (textAlpha << 24) | 0xFF747B);
        gui.text(font, subtitle, centerX - font.width(subtitle) / 2, y + 23,
                (textAlpha << 24) | 0xF1D6A0);
    }
}
