package com.myriad.client.gui;

import com.myriad.client.gui.SkillChoiceLayout.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Character information panel art in fixed GUI pixels. */
final class SkillChoiceTheme {
    private static final Identifier CREST = Identifier.fromNamespaceAndPath("myriad", "textures/gui/skill_choice/meridian_crest.png");
    static final int GOLD = 0xFFE8CE90;
    static final int JADE = 0xFF91D9CA;
    static final int MUTED = 0xFF869C9F;
    static final int INK = 0xF00A1C22;
    private static final int FRAME = 0xFF816544;
    private static final int INNER_FRAME = 0xFF37635F;

    private SkillChoiceTheme() {}

    static void backdrop(GuiGraphicsExtractor g, SkillChoiceLayout layout) {
        var b = layout.panel();
        g.fill(b.x() - 2, b.y() - 2, b.right() + 2, b.bottom() + 2, 0x90000000);
        g.fill(b.x(), b.y(), b.right(), b.bottom(), FRAME);
        g.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, 0xFF172A2C);
        g.fill(b.x() + 3, b.y() + 3, b.right() - 3, b.bottom() - 3, INNER_FRAME);
        g.fill(b.x() + 4, b.y() + 4, b.right() - 4, b.bottom() - 4, 0xFF10262B);
        var area = layout.body();
        g.fill(area.x() - 2, area.y() - 2, area.right() + 1, area.bottom() + 1, 0xFF0C1F25);
        for (boolean right : new boolean[]{false, true}) {
            for (boolean bottom : new boolean[]{false, true}) {
                corner(g, right ? b.right() - 17 : b.x(), bottom ? b.bottom() - 17 : b.y(), right, bottom);
            }
        }
        int h = Math.min(26, layout.titleY() - b.y() + 1);
        if (h >= 8) {
            int w = Math.round(h * 164f / 170f);
            int x = b.x() + (b.w() - w) / 2;
            g.blit(CREST, x, b.y() - 2, x + w, b.y() - 2 + h, 0, 1, 0, 1);
        }
    }

    private static void corner(GuiGraphicsExtractor g, int x, int y, boolean right, boolean bottom) {
        cornerStrip(g, x, y, right, bottom, 0, 0, 17, 2, 0xFFB9955D);
        cornerStrip(g, x, y, right, bottom, 0, 2, 2, 17, 0xFFB9955D);
        cornerStrip(g, x, y, right, bottom, 3, 3, 12, 4, 0xFF83B4A3);
        cornerStrip(g, x, y, right, bottom, 3, 4, 4, 12, 0xFF83B4A3);
        cornerStrip(g, x, y, right, bottom, 5, 5, 8, 8, 0xFFBD9D67);
    }

    private static void cornerStrip(GuiGraphicsExtractor g, int x, int y, boolean right, boolean bottom,
            int left, int top, int endX, int endY, int color) {
        g.fill(x + (right ? 17 - endX : left), y + (bottom ? 17 - endY : top),
                x + (right ? 17 - left : endX), y + (bottom ? 17 - top : endY), color);
    }

    static void divider(GuiGraphicsExtractor g, int left, int right, int y, int color) {
        if (right - left < 12) return;
        int middle = (left + right) / 2;
        g.fill(left, y, middle - 4, y + 1, color);
        g.fill(middle + 5, y, right, y + 1, color);
        diamond(g, middle, y, color);
    }

    static void diamond(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y - 3, x + 1, y - 1, color);
        g.fill(x - 2, y - 1, x, y + 1, color);
        g.fill(x + 1, y - 1, x + 3, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 3, color);
    }

    static void plate(GuiGraphicsExtractor g, Box b, int border, int fill) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), border);
        g.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, fill);
        if (b.w() > 6 && b.h() > 4) g.fill(b.x() + 3, b.y() + 3, b.right() - 3, b.y() + 4, 0x2949A99D);
    }

    static void card(GuiGraphicsExtractor g, Box b, boolean selected, boolean learned, boolean locked, boolean hover) {
        int border = selected ? GOLD : hover ? JADE : learned ? FRAME : locked ? 0xFF3F545A : INNER_FRAME;
        int fill = selected ? 0xFF233C3B : locked ? 0xFF16232A : learned ? 0xFF182F32 : INK;
        plate(g, b, border, fill);
        if (selected || learned) g.fill(b.x() + 1, b.y() + 1, b.x() + 3, b.bottom() - 1, border);
    }


    static void card(GuiGraphicsExtractor g, Box b, boolean learned, boolean locked, boolean hover) {
        int border = locked ? 0xFF34454B : learned ? GOLD : hover ? JADE : 0xFF3C716F;
        plate(g, b, border, locked ? 0xEF101B23 : learned ? 0xF0173439 : INK);
        for (int dx : new int[]{0, b.w() - 8}) {
            for (int dy : new int[]{0, b.h() - 8}) {
                int x = b.x() + dx, y = b.y() + dy;
                g.fill(x, y, x + 8, y + 1, border);
                g.fill(x, y, x + 1, y + 8, border);
            }
        }
        if (learned) g.fill(b.x() + 2, b.y() + 7, b.x() + 4, b.bottom() - 7, GOLD);
    }

    static void panelButton(GuiGraphicsExtractor g, Font font, Box b, String text, boolean enabled, boolean hover, boolean selected) {
        int edge = hover ? JADE : selected ? GOLD : enabled ? INNER_FRAME : 0xFF3F545A;
        int fill = selected ? 0xFF274541 : enabled ? 0xFF16363A : 0xFF15232B;
        plate(g, b, edge, fill);
        String label = font.plainSubstrByWidth(text, Math.max(1, b.w() - 8));
        g.text(font, label, b.x() + (b.w() - font.width(label)) / 2, b.y() + (b.h() - font.lineHeight) / 2, enabled ? GOLD : MUTED);
    }


    static void button(GuiGraphicsExtractor g, Font font, Box b, String text, boolean enabled, boolean hover, boolean selected) {
        int edge = selected ? GOLD : enabled ? (hover ? GOLD : 0xFF50877E) : 0xFF34444B;
        int fill = selected ? 0xF02C4D46 : enabled ? (hover ? 0xF02A5350 : 0xF016373B) : 0xF0122028;
        plate(g, b, edge, fill);
        if (selected) g.fill(b.x() + 8, b.bottom() - 3, b.right() - 8, b.bottom() - 2, GOLD);
        String label = font.plainSubstrByWidth(text, Math.max(1, b.w() - 8));
        g.text(font, label, b.x() + (b.w() - font.width(label)) / 2, b.y() + (b.h() - font.lineHeight) / 2, enabled ? GOLD : MUTED);
    }

    static void scrollbar(GuiGraphicsExtractor g, Box track, Box thumb) {
        g.fill(track.x(), track.y(), track.right(), track.bottom(), 0xAA071418);
        g.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), 0xBBAEBA8C);
    }
}
