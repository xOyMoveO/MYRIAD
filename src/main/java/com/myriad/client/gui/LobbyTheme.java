package com.myriad.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The two screens share controls but have independently replaceable backgrounds and panels. */
public final class LobbyTheme {
    public static final int GOLD = 0xFFF5D79D, JADE = 0xFFA5E5D6, TEXT = 0xFFF2F2E9, MUTED = 0xFFB5C9C6;
    private static final Identifier HALL = id("textures/gui/lobby/background.png");
    private static final Identifier DRAFT = id("textures/gui/draft/background.png");
    private static final Identifier HALL_PANEL = id("lobby/panel"), DRAFT_PANEL = id("draft/panel");
    private static final Identifier BUTTON = id("match/button"), HOT = id("match/button_hover"), DISABLED = id("match/button_disabled");

    private LobbyTheme() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("myriad", path); }

    public static void background(GuiGraphicsExtractor g, boolean draft, int width, int height) {
        float fill = Math.max(width / 960f, height / 540f);
        float u = Math.min(1f, width / (960f * fill)), v = Math.min(1f, height / (540f * fill));
        g.blit(draft ? DRAFT : HALL, 0, 0, width, height, (1-u)/2, (1+u)/2, (1-v)/2, (1+v)/2);
    }
    public static void panel(GuiGraphicsExtractor g, boolean draft, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, draft ? DRAFT_PANEL : HALL_PANEL, x, y, w, h);
    }

    public static Button button(int x, int y, int w, int h, Component label, Button.OnPress press) {
        class MenuButton extends Button {
            MenuButton() { super(x, y, w, h, label, press, DEFAULT_NARRATION); }
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, !active ? DISABLED : isHoveredOrFocused() ? HOT : BUTTON,
                        getX(), getY(), getWidth(), getHeight());
                var font = Minecraft.getInstance().font;
                String line = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 8));
                g.text(font, line, getX() + (getWidth()-font.width(line))/2, getY()+(getHeight()-8)/2,
                        !active ? MUTED : isHoveredOrFocused() ? GOLD : TEXT);
            }
        }
        return new MenuButton();
    }
}
