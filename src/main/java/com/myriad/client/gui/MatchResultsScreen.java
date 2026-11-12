package com.myriad.client.gui;

import com.myriad.network.NetworkSetup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 最近一局双方玩家结算表。双行卡片保证在较小GUI宽度下仍能完整显示统计。 */
public final class MatchResultsScreen extends Screen {
    private final NetworkSetup.MatchResultsPayload results;
    private int scroll;
    private int maximum;

    public MatchResultsScreen(NetworkSetup.MatchResultsPayload results) {
        super(Component.translatable("screen.myriad.match_results.title"));
        this.results = results;
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 55, height - 27, 110, 20).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(null); }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.clamp(scroll - (int)(dy * 28), 0, maximum);
        return true;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xED08111C);
        int panelWidth = Math.min(620, width - 20);
        int left = (width - panelWidth) / 2;
        gui.text(font, title, (width - font.width(title)) / 2, 10, 0xFFFFD77A);
        Component summary = Component.translatable("screen.myriad.match_results.summary",
                Component.translatable("faction.myriad." + results.winner()),
                results.durationSeconds() / 60, results.durationSeconds() % 60);
        gui.text(font, summary, (width - font.width(summary)) / 2, 24, 0xFFE6F1F5);

        int top = 42;
        int bottom = height - 34;
        gui.enableScissor(left, top, left + panelWidth, bottom);
        int y = top + 2 - scroll;
        List<NetworkSetup.MatchResultEntry> entries = new ArrayList<>(results.entries());
        entries.sort(Comparator.comparing(NetworkSetup.MatchResultEntry::faction)
                .thenComparing(NetworkSetup.MatchResultEntry::name));
        String currentFaction = null;
        for (NetworkSetup.MatchResultEntry entry : entries) {
            if (!entry.faction().equals(currentFaction)) {
                currentFaction = entry.faction();
                Component faction = Component.translatable("faction.myriad." + currentFaction);
                int color = "xuanxiao".equals(currentFaction) ? 0xFFFF6A6A : 0xFF65C7FF;
                gui.fill(left, y, left + panelWidth, y + 17, 0xCC152536);
                gui.text(font, faction, left + 7, y + 4, color);
                y += 20;
            }
            int rowHeight = 31;
            gui.fill(left, y, left + panelWidth, y + rowHeight - 2, 0xA8121B27);
            Component role = entry.character().isBlank() ? Component.literal("-")
                    : Component.translatable("character.myriad." + entry.character());
            Component identity = Component.literal(entry.name()).append(" · ").append(role)
                    .append(Component.translatable("screen.myriad.match_results.identity",
                            entry.level(), entry.kills(), entry.deaths(), entry.gold()));
            gui.text(font, identity, left + 7, y + 4, 0xFFF2F4F7);
            Component combat = Component.translatable("screen.myriad.match_results.combat",
                    entry.heroDamage(), entry.damageTaken(), entry.healingReceived());
            gui.text(font, combat, left + 7, y + 16, 0xFFBFD4DE);
            y += rowHeight;
        }
        maximum = Math.max(0, y + scroll - bottom + 2);
        gui.disableScissor();
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }
}
