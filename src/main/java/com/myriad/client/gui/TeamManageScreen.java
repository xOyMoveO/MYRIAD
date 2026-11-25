package com.myriad.client.gui;

import com.myriad.network.NetworkSetup;
import com.myriad.network.Packets;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Select the arena that accepts matches from its queue. */
public final class TeamManageScreen extends Screen {
    private String selected;
    private boolean duelAvailable;

    public TeamManageScreen() {
        super(Component.translatable("lobby.myriad.arena.title"));
    }

    @Override protected void init() {
        Packets.sendToServer(new NetworkSetup.RequestTeamListPayload());
        layout();
    }

    public static void receiveTeamList(Map<String, Integer> teams, String arena, boolean available) {
        if (Minecraft.getInstance().gui.screen() instanceof TeamManageScreen screen) {
            screen.selected = arena;
            screen.duelAvailable = available;
            screen.layout();
        }
    }

    private void layout() {
        clearWidgets();
        int w = Math.min(width - 12, 184);
        int x = (width - w) / 2;
        int y = height / 2 - 30;
        for (String id : new String[]{"3v3", "5v5"}) {
            Button button = addRenderableWidget(Button.builder(Component.translatable("arena.myriad." + id), b -> {
                selected = null;
                layout();
                Packets.sendToServer(new NetworkSetup.SelectArenaPayload(id));
            }).bounds(x, y, w, 20).build());
            button.active = selected != null && !id.equals(selected) && (duelAvailable || id.equals("5v5"));
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 5, w, 20).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float partial) {
        g.fill(0, 0, width, height, 0xED111C24);
        g.text(font, title, (width - font.width(title)) / 2, height / 2 - 48, 0xFFF1D8AB);
        super.extractRenderState(g, x, y, partial);
    }
}
