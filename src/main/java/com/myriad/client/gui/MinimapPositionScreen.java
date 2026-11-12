package com.myriad.client.gui;

import com.myriad.client.MinimapConfig;
import com.myriad.client.MinimapOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 点击屏幕任意位置来自定义小地图中心，位置保存在本机客户端。 */
public final class MinimapPositionScreen extends Screen {
    private final Screen parent;

    public MinimapPositionScreen(Screen parent) {
        super(Component.translatable("screen.myriad.minimap_position.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.minimap_position.reset"),
                        button -> {
                            MinimapConfig.reset();
                        })
                .bounds(width / 2 - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 4, y, 100, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.buttonInfo().button() == 0 && event.y() < height - 34) {
            MinimapConfig.placeAt((int) event.x(), (int) event.y(), width, height, MinimapOverlay.SIZE);
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        int x = MinimapConfig.x(width, MinimapOverlay.SIZE);
        int y = MinimapConfig.y(height, MinimapOverlay.SIZE);
        MinimapOverlay.renderPreview(gui, minecraft.font, x, y);
        Component title = Component.translatable("screen.myriad.minimap_position.title");
        Component hint = Component.translatable("screen.myriad.minimap_position.hint");
        gui.text(minecraft.font, title, (width - minecraft.font.width(title)) / 2, 8, 0xFFFFE6A0);
        gui.text(minecraft.font, hint, (width - minecraft.font.width(hint)) / 2, 20, 0xFFD5E8EF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
