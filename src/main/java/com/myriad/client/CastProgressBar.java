package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared, compact presentation for temporary cast and recall progress. */
final class CastProgressBar {
    private static final int MIN_WIDTH = 148;
    private static final int TRACK_HEIGHT = 3;
    private static final int SURFACE = 0xC0161D26;
    private static final int TEXT = 0xFFF0F4F8;
    private static final int TRACK = 0xFF35424E;
    private static final int PROGRESS = 0xFFB3DFE5;

    private CastProgressBar() { }

    static void draw(GuiGraphicsExtractor gui, Font font, String label, float progress) {
        draw(gui, font, label, progress, null);
    }

    static void draw(GuiGraphicsExtractor gui, Font font, String label, float progress, CombatHudLayout.Bounds panel) {
        var window = Minecraft.getInstance().getWindow();
        int centerX = window.getGuiScaledWidth() / 2;
        int y = window.getGuiScaledHeight() / 2 + 42;
        int available = Math.max(1, window.getGuiScaledWidth() - 24);
        int textWidth = font.width(label);
        int width = Math.min(available, Math.max(MIN_WIDTH, textWidth + 12));
        int x = centerX - width / 2;
        if (panel != null) {
            var prompts = HudLayoutConfig.state(HudLayoutConfig.Element.PROMPTS);
            // A player who has moved or scaled the prompt group owns its placement.
            boolean automatic = prompts.x() == HudLayoutConfig.Element.PROMPTS.defaultX()
                    && prompts.y() == HudLayoutConfig.Element.PROMPTS.defaultY() && prompts.scale() == 1;
            if (automatic && y + 18 > panel.y() && y - 3 < panel.bottom()
                    && x - 4 < panel.right() && x + width + 4 > panel.x()) {
                int screenWidth = window.getGuiScaledWidth();
                boolean useRight = screenWidth - panel.right() >= panel.x();
                int left = useRight ? panel.right() + 8 : 8;
                int right = useRight ? screenWidth - 8 : panel.x() - 8;
                if (right - left >= 40) {
                    width = Math.min(width, right - left);
                    x = Math.clamp(x, left, right - width);
                    centerX = x + width / 2;
                }
            }
        }

        gui.fill(x - 4, y - 3, x + width + 4, y + 18, SURFACE);
        float textScale = Math.min(1.0F, Math.max(1, width - 8) / (float)Math.max(1, textWidth));
        gui.pose().pushMatrix();
        gui.pose().translate(centerX, y);
        gui.pose().scale(textScale);
        gui.text(font, label, -textWidth / 2, 0, TEXT);
        gui.pose().popMatrix();

        int trackY = y + 12;
        gui.fill(x, trackY, x + width, trackY + TRACK_HEIGHT, TRACK);
        int fill = Math.round(width * Math.clamp(progress, 0.0F, 1.0F));
        if (fill > 0) gui.fill(x, trackY, x + fill, trackY + TRACK_HEIGHT, PROGRESS);
    }
}
