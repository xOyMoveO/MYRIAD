package com.myriad.client;

import java.util.UUID;

import com.myriad.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

/** 屏幕顶端英雄击杀播报：击杀者头像、击杀文字、阵亡者头像及多杀称号。 */
public final class KillAnnouncementOverlay {
    private static final int DURATION_TICKS = 40;
    private static UUID killerId;
    private static UUID victimId;
    private static String killerName = "";
    private static String victimName = "";
    private static int multiKill = 1;
    private static long startTick = Long.MIN_VALUE;

    private KillAnnouncementOverlay() { }

    public static void show(UUID newKillerId, String newKillerName, UUID newVictimId,
                            String newVictimName, int newMultiKill) {
        Minecraft minecraft = Minecraft.getInstance();
        killerId = newKillerId;
        victimId = newVictimId;
        killerName = newKillerName == null ? "" : newKillerName;
        victimName = newVictimName == null ? "" : newVictimName;
        multiKill = Math.max(1, Math.min(5, newMultiKill));
        startTick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        var sound = switch (multiKill) {
            case 2 -> ModSounds.KILL_DOUBLE;
            case 3 -> ModSounds.KILL_TRIPLE;
            case 4 -> ModSounds.KILL_QUADRA;
            case 5 -> ModSounds.KILL_PENTA;
            default -> ModSounds.KILL_SINGLE;
        };
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0F, 0.9F));
    }

    public static void clear() {
        startTick = Long.MIN_VALUE;
        killerId = null;
        victimId = null;
    }

    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || startTick == Long.MIN_VALUE) return false;
        long age = minecraft.level.getGameTime() - startTick;
        return age >= 0 && age < DURATION_TICKS;
    }

    public static void render(GuiGraphicsExtractor gui, Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive()) {
            clear();
            return;
        }
        long age = minecraft.level.getGameTime() - startTick;
        float fade = age < 30 ? 1.0F : Math.max(0.0F, (DURATION_TICKS - age) / 10.0F);
        int alpha = Math.round(224 * fade);
        int textAlpha = Math.round(255 * fade);
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int y = Math.max(10, Math.round(minecraft.getWindow().getGuiScaledHeight() * .10F) - 12);
        boolean showTitle = multiKill >= 2;
        int height = showTitle ? 58 : 48;
        int width = 218;
        int left = centerX - width / 2;
        int faceY = y + (showTitle ? 20 : 9);
        int killerX = centerX - 67;
        int victimX = centerX + 41;

        gui.fill(left, y, left + width, y + height, (alpha << 24) | 0x130C12);
        gui.fill(left, y, left + width, y + 2, (textAlpha << 24) | 0xD4A84D);
        gui.fill(left + 7, y + height - 2, left + width - 7, y + height, (textAlpha << 24) | 0x7A2735);
        if (showTitle) {
            Component title = Component.translatable("hud.myriad.multi_kill." + multiKill);
            gui.text(font, title, centerX - font.width(title) / 2, y + 6,
                    (textAlpha << 24) | 0xFFD76A);
        }

        drawFace(gui, killerId, killerX, faceY, 24, 0xFF62B8FF);
        drawFace(gui, victimId, victimX, faceY, 24, 0xFFFF5968);
        Component killed = Component.translatable("hud.myriad.killed");
        gui.text(font, killed, centerX - font.width(killed) / 2, faceY + 8,
                (textAlpha << 24) | 0xF4E7CF);
        drawCenteredTrimmed(gui, font, killerName, killerX + 12, faceY + 27, 82,
                (textAlpha << 24) | 0x8FD0FF);
        drawCenteredTrimmed(gui, font, victimName, victimX + 12, faceY + 27, 82,
                (textAlpha << 24) | 0xFF9098);
    }

    private static void drawFace(GuiGraphicsExtractor gui, UUID playerId, int x, int y, int size, int border) {
        gui.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xE0080B10);
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        PlayerFaceExtractor.extractRenderState(gui, skin(playerId), x, y, size);
    }

    private static PlayerSkin skin(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (playerId != null && minecraft.player != null && minecraft.player.getUUID().equals(playerId)) {
            return minecraft.player.getSkin();
        }
        if (playerId != null && minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(playerId);
            if (info != null && info.getSkin() != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(playerId == null ? new UUID(0L, 0L) : playerId);
    }

    private static void drawCenteredTrimmed(GuiGraphicsExtractor gui, Font font, String value,
                                            int centerX, int y, int maxWidth, int color) {
        String shown = font.plainSubstrByWidth(value, maxWidth);
        gui.text(font, shown, centerX - font.width(shown) / 2, y, color);
    }
}
