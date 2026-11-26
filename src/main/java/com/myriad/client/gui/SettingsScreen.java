package com.myriad.client.gui;

import com.myriad.game.GameRules;
import com.myriad.network.NetworkSetup;
import com.myriad.network.Packets;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class SettingsScreen extends Screen {
    private Map<String, Integer> settings;
    private List<FormattedCharSequence> ruleLines = List.of();
    private int durationY;

    public SettingsScreen() {
        super(Component.translatable("screen.myriad.settings.title"));
    }

    public static void receiveSettings(Map<String, Integer> settings) {
        if (Minecraft.getInstance().gui.screen() instanceof SettingsScreen screen) {
            screen.settings = settings;
            screen.layout();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        layout();
        Packets.sendToServer(new NetworkSetup.RequestGameSettingsPayload());
    }

    private void layout() {
        clearWidgets();
        int w = Math.max(1, Math.min(340, width - 16));
        int x = (width - w) / 2;
        Integer duration = settings == null ? null : settings.get("matchDuration");
        Component rule = settings == null ? Component.translatable("lobby.myriad.sync")
                : Component.translatable(duration == null ? "screen.myriad.settings.crystal_rule" : "screen.myriad.settings.timed_rule");
        ruleLines = font.split(rule, w);
        int y = 26 + ruleLines.size() * (font.lineHeight + 1) + 5;
        durationY = y;
        if (duration != null) {
            boolean editable = settings.getOrDefault("editable", 0) != 0;
            int resetW = Math.max(40, font.width(Component.translatable("controls.reset")) + 10);
            int controlsX = x + w - resetW - 52;
            durationButton(controlsX, y, 23, Component.literal("−"), duration - 60, editable && duration > 60);
            durationButton(controlsX + 26, y, 23, Component.literal("+"), duration + 60, editable && duration < 7200);
            durationButton(controlsX + 52, y, resetW, Component.translatable("controls.reset"),
                    GameRules.DEFAULT_MATCH_DURATION_SECONDS, editable && duration != GameRules.DEFAULT_MATCH_DURATION_SECONDS);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.minimap_position.open"), b ->
                Minecraft.getInstance().setScreenAndShow(new MinimapPositionScreen(this))).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.hud_editor.open"), b ->
                Minecraft.getInstance().setScreenAndShow(new HudEditorScreen(this))).bounds(x, y + 24, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.open"), b ->
                Packets.sendToServer(new NetworkSetup.RequestMapFacilityEditorPayload())).bounds(x, y + 48, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("lobby.myriad.arena.title"), b ->
                Minecraft.getInstance().setScreenAndShow(new TeamManageScreen())).bounds(x, y + 72, w, 20).build());
    }

    private void durationButton(int x, int y, int w, Component label, int seconds, boolean enabled) {
        var button = addRenderableWidget(Button.builder(label, b ->
                Packets.sendToServer(new NetworkSetup.UpdateSettingPayload("matchDuration", seconds)))
                .bounds(x, y, w, 18).build());
        button.active = enabled;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        gui.centeredText(font, title, width / 2, 8, 0xFF55FFFF);
        int w = Math.max(1, Math.min(340, width - 16));
        int x = (width - w) / 2;
        int y = 26;
        for (var line : ruleLines) {
            gui.text(font, line, x, y, 0xFFE0E0E0);
            y += font.lineHeight + 1;
        }
        if (settings != null && settings.containsKey("matchDuration")) {
            gui.text(font, Component.translatable("screen.myriad.settings.duration", settings.get("matchDuration")),
                    x, durationY + 5, 0xFFE0E0E0);
        }
        gui.centeredText(font, Component.translatable("screen.myriad.settings.hint"), width / 2, height - 18, 0xFFAAAAAA);
    }
}
