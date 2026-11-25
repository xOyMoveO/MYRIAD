package com.myriad.client;

import com.myriad.client.CombatHudLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class CombatChat {
    private static final String SKIN_PATH = "textures/gui/combat_chat.png";
    private static final Identifier SKIN = Identifier.fromNamespaceAndPath("myriad", SKIN_PATH);

    private CombatChat() {}

    public static CombatChatLayout current(Minecraft mc) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Bounds hud = CombatHudOverlay.activeBounds(width, height);
        if (hud == null) return null;
        boolean focused = mc.gui.screen() instanceof ChatScreen;
        var options = mc.options;
        int lineHeight = (int)(9.0 * (options.chatLineSpacing().get() + 1.0));
        int vanillaHeight = ChatComponent.getHeight((focused ? options.chatHeightFocused() : options.chatHeightUnfocused()).get());
        int vanillaWidth = ChatComponent.getWidth(options.chatWidth().get());
        return CombatChatLayout.create(width, height, hud, CombatHudOverlay.hotbar(width, height),
                minimap(width, height), focused, mc.gui.chatListener().queueSize() > 0L,
                focused && mc.player.chatAbilities().hasAnyRestrictions(), vanillaWidth,
                vanillaHeight / lineHeight, options.chatScale().get().floatValue(), lineHeight);
    }

    private static Bounds minimap(int width, int height) {
        var data = MinimapClientState.snapshot();
        var element = HudLayoutConfig.Element.MINIMAP;
        var config = HudLayoutConfig.state(element);
        if (data == null || !data.configured() || !config.visible()) return null;
        int size = MinimapOverlay.SIZE;
        float x = config.x() * width + config.scale() * (MinimapConfig.x(width, size) - element.defaultX() * width);
        float y = config.y() * height + config.scale() * (MinimapConfig.y(height, size) - element.defaultY() * height);
        int left = (int)Math.floor(x);
        int top = (int)Math.floor(y);
        return new Bounds(left, top, (int)Math.ceil(x + size * config.scale()) - left,
                (int)Math.ceil(y + size * config.scale()) - top);
    }

    public static void drawInput(GuiGraphicsExtractor gui, CombatChatLayout layout, boolean focused) {
        int x = layout.x();
        int y = layout.inputTop();
        int width = layout.width();
        int height = CombatChatLayout.INPUT_HEIGHT;
        if (!SkillIcon.hasResource(SKIN_PATH)) {
            gui.fill(x, y, x + width, y + height, 0xF0121D20);
            gui.outline(x, y, width, height, focused ? 0xFF729E92 : 0xFF506D65);
            return;
        }
        int v = focused ? 40 : 0;
        slice(gui, x, y, 6, height, 0, v, 6);
        slice(gui, x + 6, y, width - 12, height, 6, v, 180);
        slice(gui, x + width - 6, y, 6, height, 186, v, 6);
    }

    private static void slice(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                              int u, int v, int sourceWidth) {
        gui.blit(RenderPipelines.GUI_TEXTURED, SKIN, x, y, u, v, width, height,
                sourceWidth, 32, 192, 72, 0xFFFFFFFF);
    }
}
