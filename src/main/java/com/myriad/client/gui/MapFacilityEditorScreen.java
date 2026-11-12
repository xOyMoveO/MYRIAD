package com.myriad.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.myriad.game.MonsterType;
import com.myriad.network.NetworkSetup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 可视化地图设施编辑器：以玩家当前脚下方块为服务端落点。 */
public final class MapFacilityEditorScreen extends Screen {
    private static final int ROW_H = 31;
    private static final int[] ROW_COLORS = {0x8A111A22, 0x8A0B131A};
    private static final List<String> MONSTER_TYPES = buildMonsterTypes();

    private enum Tab {
        MAP("map"), VEINS("veins"), LANES("lanes"), TOWERS("towers"), FOUNTAINS("fountains"),
        BRUSHES("brushes"), MONSTERS("monsters");
        final String id;
        Tab(String id) { this.id = id; }
        Component label() { return Component.translatable("screen.myriad.map_editor.tab." + id); }
    }

    private List<NetworkSetup.MapFacilityEntry> entries;
    private Tab tab = Tab.MAP;
    private int scroll;
    private EditBox nameBox;
    private String draftName = "";
    private int monsterTypeIndex;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int visibleRows;
    private boolean selectingArena;

    private MapFacilityEditorScreen(List<NetworkSetup.MapFacilityEntry> entries) {
        super(Component.translatable("screen.myriad.map_editor.title"));
        this.entries = List.copyOf(entries);
    }

    public static void receive(List<NetworkSetup.MapFacilityEntry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof MapFacilityEditorScreen screen) {
            screen.entries = List.copyOf(entries);
            screen.selectingArena = false;
            if (screen.tab == Tab.VEINS && !screen.veinsAvailable()) screen.tab = Tab.MAP;
            screen.scroll = Math.min(screen.scroll, screen.maxScroll());
            screen.rebuild();
        } else if (minecraft.player != null) {
            minecraft.setScreenAndShow(new MapFacilityEditorScreen(entries));
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(760, Math.max(296, width - 24));
        panelH = Math.max(220, height - 20);
        panelX = (width - panelW) / 2;
        panelY = 10;

        int tabGap = 3;
        int modeW = (panelW - 19) / 2;
        for (int i = 0; i < 2; i++) {
            String mode = i == 0 ? "5v5" : "3v3";
            Button modeButton = Button.builder(Component.translatable("arena.myriad." + mode),
                            button -> selectArena(mode))
                    .bounds(panelX + 8 + i * (modeW + 3), panelY + 34, modeW, 20).build();
            modeButton.active = !mode.equals(arenaId()) && (mode.equals("5v5") || duelAvailable());
            addRenderableWidget(modeButton);
        }
        int tabW = (panelW - 16 - tabGap * (Tab.values().length - 1)) / Tab.values().length;
        int tabX = panelX + 8;
        for (Tab value : Tab.values()) {
            Button tabButton = Button.builder(value.label(), button -> switchTab(value))
                    .bounds(tabX, panelY + 58, tabW, 20).build();
            tabButton.active = value != Tab.VEINS || veinsAvailable();
            addRenderableWidget(tabButton);
            tabX += tabW + tabGap;
        }

        boolean hasForm = tab == Tab.BRUSHES || tab == Tab.MONSTERS;
        listTop = panelY + (hasForm ? 112 : 90);
        visibleRows = Math.max(1, (panelY + panelH - 38 - listTop) / ROW_H);
        if (hasForm) buildCreateForm();
        buildRowButtons();

        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.refresh"),
                        button -> requestRefresh())
                .bounds(panelX + panelW - 170, panelY + panelH - 27, 78, 19).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.myriad.cancel"), button -> onClose())
                .bounds(panelX + panelW - 86, panelY + panelH - 27, 78, 19).build());
    }

    private void buildCreateForm() {
        int x = panelX + 9;
        int y = panelY + 85;
        int inputW = Math.min(150, Math.max(90, panelW / 4));
        nameBox = new EditBox(font, x, y, inputW, 18,
                Component.translatable("screen.myriad.map_editor.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(draftName);
        addRenderableWidget(nameBox);
        if (tab == Tab.BRUSHES) {
            addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.corner1"),
                            button -> sendNew("brush", "", "corner1"))
                    .bounds(x + inputW + 6, y, 76, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.corner2"),
                            button -> sendNew("brush", "", "corner2"))
                    .bounds(x + inputW + 86, y, 76, 18).build());
        } else {
            Button typeButton = Button.builder(monsterTypeLabel(), button -> {
                        monsterTypeIndex = (monsterTypeIndex + 1) % MONSTER_TYPES.size();
                        button.setMessage(monsterTypeLabel());
                    })
                    .bounds(x + inputW + 6, y, Math.min(170, panelW - inputW - 110), 18).build();
            addRenderableWidget(typeButton);
            addRenderableWidget(Button.builder(Component.translatable("screen.myriad.map_editor.add_here"),
                            button -> sendNew("monster", MONSTER_TYPES.get(monsterTypeIndex), "set"))
                    .bounds(panelX + panelW - 91, y, 82, 18).build());
        }
    }

    private void buildRowButtons() {
        List<NetworkSetup.MapFacilityEntry> shown = visibleEntries();
        int right = panelX + panelW - 9;
        for (int i = 0; i < shown.size(); i++) {
            NetworkSetup.MapFacilityEntry entry = shown.get(i);
            int y = listTop + i * ROW_H + 6;
            if (tab == Tab.BRUSHES) {
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.corner1"),
                        right - 160, y, 50, entry, "corner1"));
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.corner2"),
                        right - 106, y, 50, entry, "corner2"));
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.delete"),
                        right - 52, y, 52, entry, "remove"));
            } else if (tab == Tab.MONSTERS) {
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.move_here"),
                        right - 134, y, 78, entry, "set"));
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.delete"),
                        right - 52, y, 52, entry, "remove"));
            } else {
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.set_here"),
                        right - 134, y, 78, entry, "set"));
                addRenderableWidget(actionButton(Component.translatable("screen.myriad.map_editor.clear"),
                        right - 52, y, 52, entry, "clear"));
            }
        }
    }

    private Button actionButton(Component label, int x, int y, int w,
            NetworkSetup.MapFacilityEntry entry, String action) {
        return Button.builder(label, button -> send(entry.category(), entry.id(), entry.meta(), action))
                .bounds(x, y, w, 18).build();
    }

    private void switchTab(Tab next) {
        rememberInput();
        tab = next;
        scroll = 0;
        rebuild();
    }

    private void sendNew(String category, String meta, String action) {
        rememberInput();
        if (draftName.matches("[A-Za-z0-9_-]{1,32}")) send(category, draftName, meta, action);
    }

    private void send(String category, String id, String meta, String action) {
        if (selectingArena && !category.equals("arena")) return;
        ClientPacketDistributor.sendToServer(new NetworkSetup.MapFacilityActionPayload(category, id, meta, action));
    }

    private void selectArena(String mode) {
        selectingArena = true;
        for (var widget : children()) if (widget instanceof Button button) button.active = false;
        send("arena", mode, "", "select");
    }

    private void requestRefresh() {
        rememberInput();
        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestMapFacilityEditorPayload());
    }

    private void rememberInput() {
        if (nameBox != null) draftName = nameBox.getValue();
    }

    private void rebuild() {
        rememberInput();
        clearWidgets();
        init();
    }

    private List<NetworkSetup.MapFacilityEntry> tabEntries() {
        List<NetworkSetup.MapFacilityEntry> result = new ArrayList<>();
        for (NetworkSetup.MapFacilityEntry entry : entries) {
            boolean matches = switch (tab) {
                case MAP -> entry.category().equals("map_bound");
                case VEINS -> entry.category().equals("attribute_vein") || entry.category().equals("small_vein")
                        || entry.category().equals("large_vein");
                case LANES -> entry.category().equals("lane_path") || entry.category().equals("lane_corner");
                case TOWERS -> entry.category().equals("tower") || entry.category().equals("crystal");
                case FOUNTAINS -> entry.category().equals("fountain");
                case BRUSHES -> entry.category().equals("brush");
                case MONSTERS -> entry.category().equals("monster");
            };
            if (matches) result.add(entry);
        }
        return result;
    }

    private List<NetworkSetup.MapFacilityEntry> visibleEntries() {
        List<NetworkSetup.MapFacilityEntry> all = tabEntries();
        int from = Math.min(scroll, all.size());
        int to = Math.min(all.size(), from + visibleRows);
        return all.subList(from, to);
    }

    private int maxScroll() {
        return Math.max(0, tabEntries().size() - Math.max(1, visibleRows));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= listTop
                && mouseY <= panelY + panelH - 34) {
            int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
            if (next != scroll) {
                rememberInput();
                scroll = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        XianxiaUi.drawPanel(gui, panelX, panelY, panelW, panelH);
        List<NetworkSetup.MapFacilityEntry> shown = visibleEntries();
        for (int i = 0; i < shown.size(); i++) {
            int y = listTop + i * ROW_H;
            gui.fill(panelX + 8, y, panelX + panelW - 8, y + ROW_H - 2, ROW_COLORS[i & 1]);
            NetworkSetup.MapFacilityEntry entry = shown.get(i);
            gui.text(font, rowTitle(entry), panelX + 14, y + 4, 0xFFE8D8A8);
            gui.text(font, rowPosition(entry), panelX + 14, y + 16,
                    entry.firstSet() ? 0xFFA9D9C2 : 0xFFFF9D6A);
        }
        super.extractRenderState(gui, mouseX, mouseY, partialTick);

        String title = Component.translatable("screen.myriad.map_editor.arena_title",
                Component.translatable("arena.myriad." + arenaId())).getString();
        gui.text(font, title, width / 2 - font.width(title) / 2, panelY + 8, 0xFFB8FFF0);
        BlockPos here = Minecraft.getInstance().player == null ? BlockPos.ZERO
                : Minecraft.getInstance().player.blockPosition();
        Component current = Component.translatable("screen.myriad.map_editor.current",
                here.getX(), here.getY(), here.getZ());
        gui.text(font, current, panelX + 10, panelY + 22, 0xFF9DB0BD);
        Component summary = summary();
        gui.text(font, summary, panelX + panelW - 10 - font.width(summary), panelY + 22, 0xFFD4C47B);
        if (tab == Tab.BRUSHES || tab == Tab.MONSTERS) {
            gui.text(font, Component.translatable("screen.myriad.map_editor.name"),
                    panelX + 10, panelY + 79, 0xFF8FA3B0);
        }
        if (maxScroll() > 0) {
            Component page = Component.translatable("screen.myriad.map_editor.scroll",
                    scroll + 1, tabEntries().size());
            gui.text(font, page, panelX + 10, panelY + panelH - 22, 0xFF7E929F);
        }
        gui.text(font, Component.translatable("screen.myriad.map_editor.hint"),
                panelX + 10, panelY + panelH - 34, 0xFF72838E);
    }

    private Component summary() {
        List<NetworkSetup.MapFacilityEntry> all = tabEntries();
        long configured = all.stream().filter(NetworkSetup.MapFacilityEntry::firstSet).count();
        if (tab == Tab.BRUSHES) {
            long complete = all.stream().filter(e -> e.firstSet() && e.secondSet()).count();
            return Component.translatable("screen.myriad.map_editor.summary_brush", complete, all.size());
        }
        return Component.translatable("screen.myriad.map_editor.summary", configured, all.size());
    }

    private String arenaId() {
        return entries.stream().filter(e -> e.category().equals("arena"))
                .map(NetworkSetup.MapFacilityEntry::id).findFirst().orElse("5v5");
    }

    private boolean duelAvailable() {
        return entries.stream().anyMatch(e -> e.category().equals("arena") && e.meta().equals("3v3"));
    }

    private boolean veinsAvailable() {
        return entries.stream().anyMatch(e -> e.category().equals("attribute_vein")
                || e.category().equals("small_vein"));
    }

    private Component rowTitle(NetworkSetup.MapFacilityEntry entry) {
        return switch (entry.category()) {
            case "map_bound" -> Component.translatable(
                    "screen.myriad.map_editor.map_corner_" + entry.id());
            case "attribute_vein", "small_vein" -> {
                String affinity = switch (entry.id()) {
                    case "1" -> "moon";
                    case "2" -> "fire";
                    case "3" -> "wood";
                    default -> "unknown";
                };
                yield Component.translatable("screen.myriad.map_editor.vein_" + affinity);
            }
            case "large_vein" -> Component.translatable("screen.myriad.map_editor.large_vein", entry.id());
            case "lane_path" -> {
                String[] p = entry.id().split(":");
                yield Component.translatable("screen.myriad.map_editor.lane_point",
                        Component.translatable("lane.myriad." + p[0]), p[1]);
            }
            case "lane_corner" -> Component.translatable("screen.myriad.map_editor.lane_corner",
                    Component.translatable("lane.myriad." + entry.id()));
            case "tower" -> {
                String[] p = entry.id().split(":");
                yield Component.translatable("screen.myriad.map_editor.tower",
                        Component.translatable("faction.myriad." + p[0]),
                        Component.translatable("lane.myriad." + p[1]), p[2]);
            }
            case "fountain" -> Component.translatable("screen.myriad.map_editor.fountain",
                    Component.translatable("faction.myriad." + entry.id()));
            case "crystal" -> Component.translatable("screen.myriad.map_editor.crystal",
                    Component.translatable("faction.myriad." + entry.id()));
            case "brush" -> Component.translatable("screen.myriad.map_editor.brush", entry.id());
            case "monster" -> Component.translatable("screen.myriad.map_editor.monster", entry.id(),
                    typeLabel(entry.meta()));
            default -> Component.literal(entry.id());
        };
    }

    private Component rowPosition(NetworkSetup.MapFacilityEntry entry) {
        if (!entry.firstSet()) return Component.translatable("screen.myriad.map_editor.unset");
        Component first = Component.translatable("screen.myriad.map_editor.position",
                entry.x(), entry.y(), entry.z());
        if (!entry.category().equals("brush")) return first;
        Component second = entry.secondSet()
                ? Component.translatable("screen.myriad.map_editor.position", entry.x2(), entry.y2(), entry.z2())
                : Component.translatable("screen.myriad.map_editor.unset");
        return Component.translatable("screen.myriad.map_editor.brush_positions", first, second);
    }

    private Component monsterTypeLabel() {
        return typeLabel(MONSTER_TYPES.get(monsterTypeIndex));
    }

    private static Component typeLabel(String id) {
        if (id.equals("large_random")) {
            return Component.translatable("screen.myriad.map_editor.large_random");
        }
        MonsterType type = MonsterType.byId(id);
        return type == null ? Component.literal(id) : Component.translatable(type.getNameKey());
    }

    private static List<String> buildMonsterTypes() {
        List<String> result = new ArrayList<>();
        for (MonsterType type : MonsterType.values()) {
            if (!type.isLargeBoss()) result.add(type.getId());
        }
        result.add("large_random");
        return List.copyOf(result);
    }

    @Override public boolean isPauseScreen() { return false; }
}
