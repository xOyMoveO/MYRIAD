package com.myriad.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class CombatHudSkin {
    static final String PATH = "textures/gui/combat_hud.png";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("myriad", PATH);
    private static final int TEXTURE_SIZE = 1024;
    static final Slice PANEL = new Slice(16, 16, 128, 64);
    static final Slice LOW_HEALTH = new Slice(160, 16, 128, 64);
    static final Slice PORTRAIT = new Slice(16, 96, 32, 32);
    static final Slice SKILL = new Slice(64, 96, 32, 32);
    static final Slice ULTIMATE = new Slice(112, 96, 32, 32);
    static final Slice READY = new Slice(160, 96, 32, 32);
    static final Slice HEALTH = new Slice(16, 160, 16, 16);
    static final Slice MANA = new Slice(16, 192, 16, 12);
    static final Slice XP = new Slice(16, 224, 16, 8);
    static final Slice ATTACK = new Slice(16, 248, 16, 8);
    static final Slice SHIELD = new Slice(64, 160, 16, 16);
    static final Slice LOCK = new Slice(16, 288, 16, 16);
    static final Slice NO_MANA = new Slice(48, 288, 16, 16);
    static final Slice CHARGE = new Slice(80, 288, 16, 16);
    static final Slice RECAST = new Slice(112, 288, 16, 16);
    static final Slice CONTROL = new Slice(144, 288, 16, 16);
    static final Slice GOLD = new Slice(176, 288, 16, 16);

    record Slice(int u, int v, int width, int height) {
        void draw(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
            blit(gui, x, y, w, h, u, v, width, height);
        }

        void frame(GuiGraphicsExtractor gui, int w, int h) {
            drawSlices(gui, 0, 0, w, h, 8, 8, 4, 4);
        }

        void track(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
            drawSlices(gui, x, y, w, h, 4, 0, 2, 0);
        }

        private void drawSlices(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                int srcX, int srcY, int destX, int destY) {
            for (int row = 0; row < 3; row++) {
                int dstH = row == 1 ? h - destY * 2 : destY;
                if (dstH <= 0) continue;
                int partY = row == 0 ? 0 : row == 1 ? destY : h - destY;
                int sourceY = row == 0 ? 0 : row == 1 ? srcY : height - srcY;
                int sourceHeight = row == 1 ? height - srcY * 2 : srcY;
                blit(gui, x, y + partY, destX, dstH, u, v + sourceY, srcX, sourceHeight);
                blit(gui, x + destX, y + partY, w - 2 * destX, dstH,
                        u + srcX, v + sourceY, width - 2 * srcX, sourceHeight);
                blit(gui, x + w - destX, y + partY, destX, dstH,
                        u + width - srcX, v + sourceY, srcX, sourceHeight);
            }
        }

        void shield(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
            for (int offset = 0; offset < w; offset += 8) {
                int pixels = Math.min(8, w - offset);
                blit(gui, x + offset, y, pixels, h, u, v, pixels * 2, height);
            }
        }
    }

    private static void blit(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                             int u, int v, int srcW, int srcH) {
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, v, w, h,
                srcW, srcH, TEXTURE_SIZE, TEXTURE_SIZE, 0xFFFFFFFF);
    }

    private CombatHudSkin() {}
}
