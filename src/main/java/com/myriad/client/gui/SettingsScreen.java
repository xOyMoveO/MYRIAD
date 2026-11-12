package com.myriad.client.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.network.NetworkSetup;

/**
 * 游戏设置界面（/myriad settings 打开，开局前可改）：
 * 对局时长 / 宗门胜利积分，每行显示当前值 + 步进按钮。
 */
public class SettingsScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int SECTION_COLOR = 0xFF55FFFF;

    /** 设置项定义：key -> (显示名, 步长, 是否秒单位) */
    private static final String[][] SETTINGS = {
            { "matchDuration", "对局时长（秒）", "60", "true" },
            { "targetScore", "宗门胜利积分", "50", "false" },
    };

    private static Map<String, Integer> values = new LinkedHashMap<>();

    public SettingsScreen() {
        super(Component.translatable("screen.myriad.settings.title"));
        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestGameSettingsPayload());
    }

    /** 网络包回调（主线程）：刷新设置值 */
    public static void receiveSettings(Map<String, Integer> incoming) {
        values = new LinkedHashMap<>(incoming);
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof SettingsScreen screen) {
            screen.rebuild();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int listW = 340;
        int listX = centerX - listW / 2;
        int startY = 30;
        for (int i = 0; i < SETTINGS.length; i++) {
            String key = SETTINGS[i][0];
            int step = Integer.parseInt(SETTINGS[i][2]);
            boolean seconds = Boolean.parseBoolean(SETTINGS[i][3]);
            int y = startY + i * ROW_H;
            int current = values.getOrDefault(key, 0);
            String unit = seconds ? " 秒" : "";
            addRenderableWidget(Button.builder(Component.literal(SETTINGS[i][1] + "：" + current + unit),
                            btn -> {
                            })
                    .bounds(listX, y, 200, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
                        int v = values.getOrDefault(key, 0) - step;
                        ClientPacketDistributor.sendToServer(new NetworkSetup.UpdateSettingPayload(key, v));
                    })
                    .bounds(listX + 210, y, 40, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
                        int v = values.getOrDefault(key, 0) + step;
                        ClientPacketDistributor.sendToServer(new NetworkSetup.UpdateSettingPayload(key, v));
                    })
                    .bounds(listX + 254, y, 40, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("重置"), btn -> {
                        int def = key.equals("matchDuration") ? 1800
                                : key.equals("targetScore") ? 500 : 60;
                        ClientPacketDistributor.sendToServer(new NetworkSetup.UpdateSettingPayload(key, def));
                    })
                    .bounds(listX + 298, y, 42, 18)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.minimap_position.open"), btn ->
                        net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                                new MinimapPositionScreen(this)))
                .bounds(listX, startY + SETTINGS.length * ROW_H + 10, listW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.hud_editor.open"), btn ->
                        net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                                new HudEditorScreen(this)))
                .bounds(listX, startY + SETTINGS.length * ROW_H + 34, listW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.open"), btn ->
                        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestMapFacilityEditorPayload()))
                .bounds(listX, startY + SETTINGS.length * ROW_H + 58, listW, 20)
                .build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gui,
            int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        String title = Component.translatable("screen.myriad.settings.title").getString();
        gui.text(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(title) / 2, 8, SECTION_COLOR);
        String hint = Component.translatable("screen.myriad.settings.hint").getString();
        gui.text(font, Component.literal(hint),
                this.width / 2 - font.width(hint) / 2, this.height - 18, 0xFF808080);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
