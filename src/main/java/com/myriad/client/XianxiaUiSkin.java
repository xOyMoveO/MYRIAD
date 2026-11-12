package com.myriad.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Static atlas artwork: all labels, player rows and interaction stay in the live UI. */
public final class XianxiaUiSkin {
    private static final Identifier PICK = Identifier.fromNamespaceAndPath("myriad", "xianxia/pick_background");
    private static final Identifier TAB = Identifier.fromNamespaceAndPath("myriad", "xianxia/tab_panel");

    private XianxiaUiSkin() {}

    public static void pickBackground(GuiGraphicsExtractor gui, int width, int height) {
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, PICK, 0, 0, width, height);
    }

    public static void tabPanel(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, TAB, x, y, width, height);
    }
}
