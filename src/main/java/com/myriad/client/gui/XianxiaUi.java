package com.myriad.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 共用的东方玄幻界面底板与装饰，供角色信息与商店界面复用。 */
final class XianxiaUi {
    private static final Identifier PANEL_JADE = Identifier.fromNamespaceAndPath("myriad", "ui/panel_jade");
    private static final Identifier HEADER_ORNAMENT = Identifier.fromNamespaceAndPath("myriad", "ui/header_ornament");
    private static final Identifier BUTTON_PURCHASE = Identifier.fromNamespaceAndPath("myriad", "ui/button_purchase");
    private static final Identifier TAB_COMMON = Identifier.fromNamespaceAndPath("myriad", "ui/tab_common");
    private static final Identifier TAB_FINE = Identifier.fromNamespaceAndPath("myriad", "ui/tab_fine");
    private static final Identifier TAB_LEGEND = Identifier.fromNamespaceAndPath("myriad", "ui/tab_legend");

    private XianxiaUi() {
    }

    static void drawPanel(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xE005090D);
        gui.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xC07F9A8B);
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_JADE, x, y, width, height);
        // 略微压暗底纹，保证小字号数值仍易于阅读。
        gui.fill(x, y, x + width, y + height, 0x74070D12);
        gui.fill(x + 2, y + 2, x + width - 2, y + 3, 0xA8A8D2B4);
        gui.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0x8A325043);
        if (width >= 96 && height >= 30) {
            int ornamentWidth = Math.min(224, width - 24);
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, HEADER_ORNAMENT,
                    x + (width - ornamentWidth) / 2, y + 2, ornamentWidth, 18);
        }
    }

    static void drawSubPanel(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xBB4A695E);
        gui.fill(x, y, x + width, y + height, 0xB20A1118);
        gui.fill(x + 1, y + 1, x + width - 1, y + 2, 0x7FA0BDA9);
    }

    /** 带东方玄幻像素边框的操作按钮；文字由游戏实时绘制，保证任意 GUI 缩放下仍清晰。 */
    static void drawActionButton(GuiGraphicsExtractor gui, Font font,
            int x, int y, int width, int height, String label, boolean enabled, boolean hover) {
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_PURCHASE,
                x, y, width, height);
        if (!enabled) {
            gui.fill(x, y, x + width, y + height, 0x9A101216);
        } else if (hover) {
            gui.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x26FFFFFF);
        }
        int color = enabled ? 0xFFFFF1B8 : 0xFF8A8A8A;
        int tx = x + (width - font.width(label)) / 2;
        int ty = y + (height - font.lineHeight) / 2;
        gui.text(font, label, tx + 1, ty + 1, 0xB0000000);
        gui.text(font, label, tx, ty, color);
    }

    /** 商店品级页签，保留实时文字与选中/悬停状态。 */
    static void drawTierTab(GuiGraphicsExtractor gui, Font font, int tier,
            int x, int y, int width, int height, String label, boolean selected, boolean hover) {
        Identifier texture = switch (tier) {
            case 2 -> TAB_FINE;
            case 3 -> TAB_LEGEND;
            default -> TAB_COMMON;
        };
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height);
        if (!selected) {
            gui.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x62060A0D);
        } else {
            gui.fill(x + 3, y + height - 5, x + width - 3, y + height - 3, 0x66FFF4B0);
        }
        if (hover) {
            gui.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x1FFFFFFF);
        }
        int color = switch (tier) {
            case 2 -> 0xFFCEFAFF;
            case 3 -> 0xFFFFE2A0;
            default -> 0xFFF1F1EC;
        };
        int tx = x + (width - font.width(label)) / 2;
        int ty = y + (height - font.lineHeight) / 2;
        gui.text(font, label, tx + 1, ty + 1, 0xB0000000);
        gui.text(font, label, tx, ty, color);
    }
}
