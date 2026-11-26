package com.myriad.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.client.LobbyClient;
import com.myriad.game.Faction;
import com.myriad.network.NetworkSetup.MatchResultEntry;
import com.myriad.network.NetworkSetup.MatchResultsPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A read-only result sheet; only the team rows scroll. */
public final class MatchResultsScreen extends Screen {
    private record Row(List<FormattedCharSequence> heading, List<FormattedCharSequence> detail,
            int textColor, int background) {
        int height(int lineStep) { return 7 + (heading.size() + detail.size()) * lineStep; }
    }

    private final MatchResultsPayload results;
    private List<Row> rows = List.of();
    private List<FormattedCharSequence> summary = List.of();
    private int left, panelWidth, top, bottom, lineStep, scroll, maximum;

    public MatchResultsScreen(MatchResultsPayload results) {
        super(Component.translatable("screen.myriad.match_results.title"));
        this.results = results;
    }

    @Override protected void init() {
        panelWidth = Math.max(1, Math.min(620, width - 20));
        left = (width - panelWidth) / 2;
        int wrap = Math.max(1, panelWidth - 16);
        lineStep = font.lineHeight + 3;
        int minutes = results.durationSeconds() / 60;
        int seconds = results.durationSeconds() % 60;
        var winner = Faction.byId(results.winner());
        Component summaryText = winner == null ? Component.translatable("screen.myriad.match_results.draw", minutes, seconds)
                : Component.translatable("screen.myriad.match_results.summary", winner.displayName(), minutes, seconds);
        summary = font.split(summaryText, panelWidth);
        top = 30 + summary.size() * lineStep;
        bottom = Math.max(top + 1, height - 34);

        rows = wrapRows(results.entries(), font, wrap);
        int contentHeight = 4 + rows.stream().mapToInt(row -> row.height(lineStep)).sum();
        maximum = Math.max(0, contentHeight - (bottom - top));
        scroll = Math.clamp(scroll, 0, maximum);

        int w = Math.min(112, (width - 18) / 2);
        addRenderableWidget(LobbyTheme.button(width / 2 - w - 2, height - 27, w, 20,
                Component.translatable("lobby.myriad.results.lobby"), b -> LobbyClient.openLobby()));
        addRenderableWidget(LobbyTheme.button(width / 2 + 2, height - 27, w, 20,
                Component.translatable("lobby.myriad.results.requeue"), b -> {
                    LobbyClient.send("requeue", "");
                    LobbyClient.openLobby();
                }));
    }

    private static List<Row> wrapRows(List<MatchResultEntry> entries, Font font, int wrap) {
        var rows = new ArrayList<Row>();
        String currentFaction = null;
        for (var entry : entries.stream()
                .sorted(Comparator.comparing(MatchResultEntry::faction).thenComparing(MatchResultEntry::name)).toList()) {
            if (!entry.faction().equals(currentFaction)) {
                currentFaction = entry.faction();
                var team = Faction.byId(currentFaction);
                var teamName = team == null ? Component.literal("—") : team.displayName();
                rows.add(new Row(font.split(teamName, wrap), List.of(),
                        team == null ? 0xFFAAAAAA : team.argb(), 0xCC152536));
            }
            Component role = entry.character().isBlank() ? Component.literal("-")
                    : Component.translatable("character.myriad." + entry.character());
            Component identity = Component.literal(entry.name()).append(" · ").append(role)
                    .append(Component.translatable("screen.myriad.match_results.identity",
                            entry.level(), entry.kills(), entry.deaths(), entry.assists(), entry.gold()));
            Component combat = Component.translatable("screen.myriad.match_results.combat",
                    entry.heroDamage(), entry.damageTaken(), entry.healingReceived());
            rows.add(new Row(font.split(identity, wrap), font.split(combat, wrap), 0xFFF2F4F7, 0xA8121B27));
        }
        return rows;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(null); }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy == 0 || maximum == 0) return super.mouseScrolled(x, y, dx, dy);
        scroll = Math.clamp(scroll - (int) Math.round(dy * 28), 0, maximum);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            int page = Math.max(1, bottom - top - lineStep);
            scroll = Math.clamp(scroll + (event.key() == InputConstants.KEY_PAGEUP ? -page : page), 0, maximum);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xED08111C);
        gui.centeredText(font, title, width / 2, 10, 0xFFFFD77A);
        int y = 24;
        for (var line : summary) {
            gui.centeredText(font, line, width / 2, y, 0xFFE6F1F5);
            y += lineStep;
        }
        gui.enableScissor(left, top, left + panelWidth, bottom);
        y = top + 2 - scroll;
        for (Row row : rows) {
            int rowHeight = row.height(lineStep);
            if (y >= bottom) break;
            if (y + rowHeight > top) {
                gui.fill(left, y, left + panelWidth, y + rowHeight - 2, row.background());
                int lineY = y + 4;
                for (var line : row.heading()) {
                    gui.text(font, line, left + 7, lineY, row.textColor());
                    lineY += lineStep;
                }
                for (var line : row.detail()) {
                    gui.text(font, line, left + 7, lineY, 0xFFBFD4DE);
                    lineY += lineStep;
                }
            }
            y += rowHeight;
        }
        gui.disableScissor();
        if (maximum > 0) {
            int h = bottom - top;
            int thumb = Math.clamp(h * h / (h + maximum), Math.min(8, h), h);
            int thumbY = top + (h - thumb) * scroll / maximum;
            gui.fill(left + panelWidth - 4, thumbY, left + panelWidth - 2, thumbY + thumb, 0xFF789EAF);
        }
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }
}
