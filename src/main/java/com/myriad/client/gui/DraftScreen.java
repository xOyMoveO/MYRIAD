package com.myriad.client.gui;

import com.myriad.character.CharacterType;
import com.myriad.client.CharacterPortrait;
import com.myriad.client.LobbyClient;
import com.myriad.game.Faction;
import com.myriad.network.LobbyNetworking.Seat;
import com.myriad.network.LobbyNetworking.Snapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Draft intents are issued for the round displayed by the server. */
public final class DraftScreen extends Screen {
    private static final int PAPER = 0xFFEDE4CF, GOLD = 0xFFFFDA88, MUTED = 0xFFACC3C4;
    private static final int TOP = 48;
    private final CharacterType[] roles = CharacterType.playableValues();
    private final Set<String> knownLocks = new HashSet<>();
    private final Map<String, Long> flashes = new HashMap<>();
    private int rowOffset, columns, visibleRows, cellWidth, cellHeight, left, centerWidth;
    private Button confirm, guide, previous, next;

    public DraftScreen() { super(Component.translatable("screen.myriad.pick.title")); }
    @Override public boolean isPauseScreen() { return false; }

    private long clock() { return minecraft.level == null ? 0 : minecraft.level.getGameTime(); }
    private int sideWidth() { return Math.clamp(width / 5, 44, 148); }
    private int bottom() { return height - 52; }
    private int maxOffset() { return Math.max(0, (roles.length + columns - 1) / columns - visibleRows); }

    @Override protected void init() {
        left = sideWidth() + 10;
        centerWidth = Math.max(35, width - 2 * left);
        columns = Math.clamp(centerWidth / 57, 1, Math.min(8, roles.length));
        cellWidth = centerWidth / columns;
        visibleRows = Math.max(1, (bottom() - TOP - 6) / 42);
        cellHeight = Math.clamp((bottom() - TOP - 6) / visibleRows, 20, 54);
        rowOffset = Math.clamp(rowOffset, 0, maxOffset());
        for (int i = rowOffset * columns; i < Math.min(roles.length, (rowOffset + visibleRows) * columns); i++) {
            int col = i % columns, row = i / columns - rowOffset;
            addRenderableWidget(new RoleButton(roles[i], left + col * cellWidth, TOP + row * cellHeight));
        }
        int lockWidth = Math.min(110, Math.max(56, width / 5));
        int guideWidth = Math.min(92, lockWidth);
        confirm = action(width - lockWidth - 6, height - 25, lockWidth, "lobby.myriad.draft.lock", b -> {
            Snapshot s = LobbyClient.snapshot();
            if (canLock(s)) LobbyClient.send("lock", s.preselected());
        });
        guide = action(width - lockWidth - guideWidth - 10, height - 25, guideWidth, "lobby.myriad.draft.guide", b -> {
            var s = LobbyClient.snapshot();
            var role = s == null ? null : CharacterType.byId(s.preselected());
            if (role != null) minecraft.setScreenAndShow(new CharacterGuideScreen(this, role));
        });
        action(6, height - 25, Math.min(80, width / 5), "gui.back", b -> onClose());
        if (maxOffset() > 0) {
            previous = addRenderableWidget(Button.builder(Component.literal("▲"), b -> scroll(-1))
                    .bounds(width / 2 - 22, bottom() - 2, 20, 13).build());
            next = addRenderableWidget(Button.builder(Component.literal("▼"), b -> scroll(1))
                    .bounds(width / 2 + 2, bottom() - 2, 20, 13).build());
        } else previous = next = null;
        updateButtons(LobbyClient.snapshot());
    }

    private Button action(int x, int y, int w, String key, Button.OnPress press) {
        return addRenderableWidget(LobbyTheme.button(x, y, w, 20, Component.translatable(key), press));
    }

    private void scroll(int offset) {
        int nextRow = Math.clamp(rowOffset + offset, 0, maxOffset());
        if (nextRow != rowOffset) { rowOffset = nextRow; rebuildWidgets(); }
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x >= left && x < left + centerWidth && y >= TOP && y < bottom() && dy != 0) {
            scroll(dy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    private Seat me(Snapshot s) {
        return s == null || minecraft.player == null ? null : s.seats().stream()
                .filter(p -> p.id().equals(minecraft.player.getUUID())).findFirst().orElse(null);
    }
    private boolean taken(Snapshot s, String role) {
        return s.seats().stream().anyMatch(seat -> seat.role().equals(role));
    }
    private boolean canLock(Snapshot s) {
        var self = me(s);
        return self != null && self.id().toString().equals(s.picker()) && self.role().isEmpty()
                && !s.preselected().isEmpty() && !taken(s, s.preselected());
    }
    private void updateButtons(Snapshot s) {
        confirm.active = canLock(s);
        guide.active = s != null && !s.preselected().isEmpty();
        if (previous != null) { previous.active = rowOffset > 0; next.active = rowOffset < maxOffset(); }
    }

    @Override public void tick() {
        var s = LobbyClient.snapshot();
        if (s == null || !s.participant() || s.picker().isEmpty()) { onClose(); return; }
        updateButtons(s);
        for (var seat : s.seats()) {
            if (!seat.role().isEmpty() && knownLocks.add(seat.role())) flashes.put(seat.role(), clock());
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        LobbyTheme.background(g, true, width, height);
        Snapshot s = LobbyClient.snapshot();
        if (s == null) return;
        Seat picker = s.seats().stream().filter(p -> p.id().toString().equals(s.picker())).findFirst().orElse(null);
        LobbyTheme.panel(g, true, 4, 3, width - 8, 38);
        if (picker != null) {
            String round = Component.translatable("lobby.myriad.draft.round", s.seats().indexOf(picker) + 1, s.seats().size()).getString();
            center(g, round, width / 2, 8, GOLD);
            String turn = minecraft.player != null && picker.id().equals(minecraft.player.getUUID())
                    ? Component.translatable("screen.myriad.pick.your_turn", s.pickSeconds()).getString()
                    : Component.translatable("screen.myriad.pick.turn", picker.name(), Faction.byId(picker.faction()).displayName(), s.pickSeconds()).getString();
            center(g, font.plainSubstrByWidth(turn, width - 20), width / 2, 23, PAPER);
        }
        LobbyTheme.panel(g, true, left - 3, TOP - 3, centerWidth + 6, bottom() - TOP + 15);
        drawTeam(g, s, Faction.GUARDIAN, 3);
        drawTeam(g, s, Faction.DESTRUCTION, width - sideWidth() - 3);
        var preview = CharacterType.byId(s.preselected());
        if (preview != null) {
            CharacterPortrait.draw(g, preview, 8, height - 47, 17, 1);
            String label = Component.translatable("lobby.myriad.draft.preview", Component.translatable(preview.nameKey())).getString();
            g.text(font, font.plainSubstrByWidth(label, Math.max(30, width / 2 - 56)), 28, height - 40, GOLD);
        }
        super.extractRenderState(g, mx, my, partial);
    }

    private void drawTeam(GuiGraphicsExtractor g, Snapshot s, Faction team, int x) {
        var seats = s.seats().stream().filter(p -> p.faction().equals(team.getName())).toList();
        if (seats.isEmpty()) return;
        int w = sideWidth();
        int h = Math.clamp((bottom() - TOP - 12) / seats.size(), 14, 40);
        sprite(g, "match/" + team.getName(), x + 2, TOP - 3, 14, 14);
        g.text(font, font.plainSubstrByWidth(team.displayName().getString(), w - 17), x + 17, TOP, team.argb());
        int y = TOP + 13;
        for (Seat seat : seats) {
            boolean current = seat.id().toString().equals(s.picker());
            boolean locked = !seat.role().isEmpty();
            sprite(g, "draft/card", x, y, w, h - 1);
            if (locked || current) {
                String crest = "match/" + team.getName();
                sprite(g, crest + "_frame", x, y, w, h - 1);
                if (current) sprite(g, crest + "_corner", x + w - 10, y, 10, 10);
                if (!LobbyClient.reducedMotion()) {
                    if (current) highlight(g, crest + "_light", x, y, w, h - 1);
                    else if (clock() - flashes.getOrDefault(seat.role(), -20L) < 6)
                        border(g, x, y, w, h - 1, PAPER);
                }
            }
            int size = Math.min(23, h - 5);
            if (locked) CharacterPortrait.draw(g, CharacterType.byId(seat.role()), x + 3, y + 2, size, 1);
            int tx = locked ? x + size + 5 : x + 4;
            g.text(font, font.plainSubstrByWidth(seat.name(), Math.max(1, x + w - tx - 3)), tx, y + 3, current ? GOLD : PAPER);
            if (h >= 25) {
                var role = CharacterType.byId(seat.role());
                String status = role != null ? Component.translatable(role.nameKey()).getString()
                        : Component.translatable(current ? "screen.myriad.pick.selecting" : "screen.myriad.pick.unselected").getString();
                g.text(font, font.plainSubstrByWidth(status, x + w - tx - 2), tx, y + 14, MUTED);
            }
            if (locked) sprite(g,"match/lock", x + w - 9, y + h - 10, 8, 8);
            else if (current) g.fill(x, y + 2, x + 3, y + h - 3, GOLD);
            y += h;
        }
    }

    private void center(GuiGraphicsExtractor g,String text,int x,int y,int color) {
        g.text(font, text, x - font.width(text) / 2, y, color);
    }
    private static void sprite(GuiGraphicsExtractor g,String name,int x,int y,int w,int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("myriad", name), x, y, w, h);
    }
    private static void border(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
    /** Move a short glint around four edges once per forty game ticks. */
    private void highlight(GuiGraphicsExtractor g, String light, int x, int y, int w, int h) {
        int perimeter = (w + h) * 2;
        int first = (int) ((clock() % 40) * perimeter / 40);
        int remaining = Math.min(w, 24);
        while (remaining > 0) {
            int edge, along, length, ox, oy;
            if (first < w) {
                edge = 0; along = first; length = w; ox = x; oy = y;
            } else if (first < w + h) {
                edge = 1; along = first - w; length = h; ox = x + w; oy = y;
            } else if (first < w * 2 + h) {
                edge = 2; along = first - w - h; length = w; ox = x + w; oy = y + h;
            } else {
                edge = 3; along = first - w * 2 - h; length = h; ox = x; oy = y + h;
            }
            int strip = Math.min(remaining, length - along);
            g.pose().pushMatrix();
            g.pose().translate(ox, oy);
            g.pose().rotate((float) (edge * Math.PI / 2));
            sprite(g, light, along, 0, strip, 2);
            g.pose().popMatrix();
            remaining -= strip;
            first = (first + strip) % perimeter;
        }
    }

    private final class RoleButton extends Button {
        private final CharacterType role;
        RoleButton(CharacterType role, int x, int y) {
            super(x, y, cellWidth - 2, cellHeight - 2, Component.translatable(role.nameKey()),
                    b -> LobbyClient.send("preselect", role.getId()), DEFAULT_NARRATION);
            this.role = role;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            Snapshot s = LobbyClient.snapshot();
            if (s == null) return;
            boolean occupied = taken(s, role.getId());
            Seat self = me(s);
            active = !occupied && self != null && self.role().isEmpty();
            boolean selected = role.getId().equals(s.preselected());
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            sprite(g, "draft/card", x, y, w, h);
            int size = Math.max(8, Math.min(h - 15, w - 5));
            CharacterPortrait.draw(g, role, x + (w - size) / 2, y + 2, size, occupied ? 0.4f : 1f);
            String name = font.plainSubstrByWidth(getMessage().getString(), w - 5);
            center(g, name, x + w / 2, y + h - 10, occupied ? MUTED : PAPER);
            if (selected || isHoveredOrFocused()) border(g, x, y, w, h, selected ? GOLD : MUTED);
            if (selected) g.text(font, "◆", x + 3, y + 2, GOLD);
            if (occupied) sprite(g, "match/lock", x + w - 12, y + 2, 10, 10);
        }
    }
}
