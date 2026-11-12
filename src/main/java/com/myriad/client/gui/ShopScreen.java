package com.myriad.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.client.ClientDataHolder;
import com.myriad.game.TacticalSupplySystem;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.EquipmentRegistry;
import com.myriad.registry.EquipmentRegistry.EquipDef;

/** 全装备同屏商店：左侧补给/已装备，中部属性筛选与品质分组网格，右侧详情。 */
public class ShopScreen extends Screen {
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int CELL_W = 43;
    private static final int CELL_H = 43;

    private String selectedId;
    private boolean purchasePending;
    public void purchaseComplete() { purchasePending = false; }
    private String filterId;
    private int scrollY;
    private int mainX;
    private int mainW;
    private int viewTop;
    private int viewBottom;
    private int detailScroll, detailMaxScroll, detailLeft, detailRight, detailTop, detailBottom;
    private String detailSelection;
    private final List<Object[]> recipeRects = new ArrayList<>();

    private final List<Object[]> equipRects = new ArrayList<>();
    private final List<Object[]> ownRects = new ArrayList<>();
    private final List<Object[]> supplyRects = new ArrayList<>();
    private final List<Object[]> filterRects = new ArrayList<>();

    public ShopScreen() {
        super(Component.translatable("screen.myriad.shop.title"));
    }

    @Override
    protected void init() {
        if (com.myriad.character.rework.FirstGroupSkills.active(ClientDataHolder.characterId()))
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new FirstGroupScreen(true));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<EquipDef> filtered(int tier) {
        AttributeIcons.Filter filter = AttributeIcons.FILTERS.stream()
                .filter(f -> f.id().equals(filterId)).findFirst().orElse(null);
        return EquipmentRegistry.byTier(tier).stream()
                .filter(def -> filter == null || filter.matches(def)).toList();
    }

    private int columns() {
        return Math.max(1, mainW / CELL_W);
    }

    private int contentHeight() {
        int h = 0;
        int cols = columns();
        for (int tier = EquipmentRegistry.TIER_COMMON; tier <= EquipmentRegistry.TIER_LEGEND; tier++) {
            int count = filtered(tier).size();
            if (count > 0) h += 16 + ((count + cols - 1) / cols) * CELL_H + 5;
        }
        return h;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - Math.max(1, viewBottom - viewTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= detailLeft && mouseX <= detailRight && mouseY >= detailTop && mouseY <= detailBottom) {
            detailScroll = Math.max(0, Math.min(detailMaxScroll, detailScroll - (int)Math.signum(dy) * 24));
            return true;
        }
        if (mouseX >= mainX && mouseX <= mainX + mainW) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) Math.signum(dy) * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        for (Object[] rect : recipeRects) {
            if (inside(mx, my, rect)) {
                selectedId = (String)rect[4];
                recipeRects.clear();
                return true;
            }
        }
        if (mx >= 16 && mx <= 96 && my >= 25 && my <= 48) {
            selectedId = TacticalSupplySystem.supplyIds().getFirst();
            return true;
        }
        for (Object[] rect : supplyRects) {
            if (inside(mx, my, rect)) {
                selectedId = (String) rect[4];
                return true;
            }
        }
        for (Object[] rect : ownRects) {
            if (inside(mx, my, rect)) {
                int slot=(Integer)rect[4];
                var slots=ClientDataHolder.equipment();
                String expected=slot<slots.size()?slots.get(slot):"";
                var def=EquipmentRegistry.get(expected);
                if(def==null)return true;
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            var current=ClientDataHolder.equipment();
                            if(confirmed && slot<current.size() && expected.equals(current.get(slot)))
                                ClientPacketDistributor.sendToServer(new NetworkSetup.SellEquipPayload(slot));
                            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(this);
                        }, Component.translatable("screen.myriad.shop.sell_confirm"),
                        Component.translatable("screen.myriad.shop.sell_confirm_body",Component.translatable(def.nameKey()))));
                return true;
            }
        }
        for (Object[] rect : filterRects) {
            if (inside(mx, my, rect)) {
                String clicked = (String) rect[4];
                filterId = clicked.equals(filterId) ? null : clicked;
                scrollY = 0;
                selectedId = null;
                return true;
            }
        }
        for (Object[] rect : equipRects) {
            if (inside(mx, my, rect)) {
                selectedId = (String) rect[4];
                return true;
            }
        }
        int detailW = Math.min(190, Math.max(150, this.width / 4));
        int detailX = this.width - detailW - 14;
        int btnW = detailW - 14;
        int bx = detailX + 7;
        int by = this.height - 38;
        if (selectedId != null && mx >= bx && mx <= bx + btnW && my >= by && my <= by + 27) {
            buySelected();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean inside(double mx, double my, Object[] rect) {
        int x = (Integer) rect[0], y = (Integer) rect[1];
        int w = (Integer) rect[2], h = (Integer) rect[3];
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void buySelected() {
        if (purchaseBlockReason() != null) return;
        purchasePending = true;
        ClientPacketDistributor.sendToServer(new NetworkSetup.ShopBuyPayload(selectedId));
    }

    private String purchaseBlockReason() {
        if (purchasePending) return "screen.myriad.shop.pending";
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || ClientDataHolder.get() == null || !ClientDataHolder.modEnabled()
                || (!player.isAlive() || player.isSpectator()) && !com.myriad.client.UiFeedback.shopAllowed)
            return "screen.myriad.shop.unavailable";
        if (selectedId == null) return "screen.myriad.shop.select_hint";
        if (!com.myriad.client.UiFeedback.shopAllowed) return "message.myriad.shop_base_only";
        if (TacticalSupplySystem.isSupply(selectedId)) {
            if (!canAfford(TacticalSupplySystem.supplyPrice(selectedId))) return "message.myriad.equip_no_gold";
            if (selectedId.equals("blood_pill") || selectedId.equals("mana_pill")) {
                int pills = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    var stack = player.getInventory().getItem(i);
                    if (stack.is(com.myriad.registry.ModItems.BLOOD_PILL.get())
                            || stack.is(com.myriad.registry.ModItems.MANA_PILL.get())) pills += stack.getCount();
                }
                if (pills >= 5) return "message.myriad.pill_limit";
            }
            return null;
        }
        EquipDef def = EquipmentRegistry.get(selectedId);
        if (def == null) return "screen.myriad.shop.unavailable";
        List<String> remaining = new ArrayList<>(ClientDataHolder.equipment());
        remaining.removeIf(String::isEmpty);
        for (String ing : def.ingredients()) remaining.remove(ing);
        if (EquipmentRegistry.isBoots(def.id()) && remaining.stream().anyMatch(EquipmentRegistry::isBoots))
            return "message.myriad.equip_boots_only_one";
        if (remaining.size() >= EquipmentRegistry.SLOT_COUNT) return "message.myriad.equip_full";
        return canAfford(actualPrice(def)) ? null : "message.myriad.equip_no_gold";
    }

    private boolean canAfford(int price) {
        return (ClientDataHolder.isTraining() && ClientDataHolder.noGoldCost()) || (ClientDataHolder.get() != null
                && ClientDataHolder.get().gold() >= price);
    }

    private int actualPrice(EquipDef def) {
        int discount = 0;
        List<String> slots = new ArrayList<>(ClientDataHolder.equipment());
        for (String ing : def.ingredients()) {
            if (slots.remove(ing)) {
                EquipDef owned = EquipmentRegistry.get(ing);
                if (owned != null) discount += owned.totalPrice();
            }
        }
        return Math.max(0, def.totalPrice() - discount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        gui.fill(0, 0, width, height, 0xC908101D);
        XianxiaUi.drawPanel(gui, 8, 20, this.width - 16, this.height - 40);
        int leftX = 16;
        int leftW = 80;
        int filterX = leftX + leftW + 7;
        int detailW = Math.min(190, Math.max(150, this.width / 4));
        int detailX = this.width - detailW - 14;
        viewTop = 52;
        viewBottom = this.height - 45;
        int filterRows = Math.max(1, (viewBottom - 54) / 19);
        int filterColumns = (AttributeIcons.FILTERS.size() + filterRows - 1) / filterRows;
        mainX = filterX + filterColumns * 22 + 8;
        mainW = Math.max(CELL_W, detailX - mainX - 8);

        String gold = "金币: " + (ClientDataHolder.isTraining() && ClientDataHolder.noGoldCost()
                ? "∞" : ClientDataHolder.get() == null ? "0" : ClientDataHolder.get().gold());
        gui.text(font, Component.literal(gold).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(gold) / 2, 28, GOLD_COLOR);

        String supplyLabel = Component.translatable("screen.myriad.shop.tab_supply").getString();
        boolean supplySelected = selectedId != null && TacticalSupplySystem.isSupply(selectedId);
        XianxiaUi.drawTierTab(gui, font, 4, leftX, 25, leftW, 23, supplyLabel,
                supplySelected, mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= 25 && mouseY <= 48);

        drawSupplyShelf(gui, font, leftX, 54, leftW);
        drawOwned(gui, font, leftX, 181, leftW);
        drawEquipmentGrid(gui, font, mouseX, mouseY);
        // 筛选图标和悬停文字最后绘制，使文字层始终位于装备面板之上。
        drawFilters(gui, font, filterX, 54, mouseX, mouseY);
        drawDetail(gui, font, detailX, 52, detailW, this.height - 98, mouseX, mouseY);
    }

    private void drawSupplyShelf(GuiGraphicsExtractor gui, Font font, int x, int y, int w) {
        XianxiaUi.drawSubPanel(gui, x, y, w, 120);
        gui.text(font, "消耗品", x + (w - font.width("消耗品")) / 2, y + 5, SECTION_COLOR);
        supplyRects.clear();
        List<String> ids = TacticalSupplySystem.supplyIds();
        for (int i = 0; i < ids.size(); i++) {
            int ix = x + 8 + (i % 2) * 35;
            int iy = y + 19 + (i / 2) * 32;
            String id = ids.get(i);
            if (id.equals(selectedId)) gui.fill(ix - 2, iy - 2, ix + 24, iy + 29, 0x5578E6D0);
            drawSupplyIcon(gui, ix, iy, 22, id);
            String price = String.valueOf(TacticalSupplySystem.supplyPrice(id));
            gui.text(font, price, ix + 11 - font.width(price) / 2, iy + 23, GOLD_COLOR);
            supplyRects.add(new Object[] { ix - 2, iy - 2, 26, 31, id });
        }
    }

    private void drawOwned(GuiGraphicsExtractor gui, Font font, int x, int y, int w) {
        int h = Math.max(126, this.height - y - 48);
        XianxiaUi.drawSubPanel(gui, x, y, w, h);
        gui.text(font, "已装备", x + (w - font.width("已装备")) / 2, y + 5, SECTION_COLOR);
        ownRects.clear();
        List<String> slots = ClientDataHolder.equipment();
        for (int i = 0; i < EquipmentRegistry.SLOT_COUNT; i++) {
            String id = i < slots.size() ? slots.get(i) : "";
            int ix = x + 12 + (i % 2) * 34;
            int iy = y + 20 + (i / 2) * 35;
            drawEquipIcon(gui, font, ix, iy, 24, id);
            ownRects.add(new Object[] { ix - 2, iy - 2, 28, 28, i });
        }
    }

    private void drawFilters(GuiGraphicsExtractor gui, Font font, int x, int y, int mouseX, int mouseY) {
        filterRects.clear();
        int rows = Math.max(1, (viewBottom - y) / 19);
        for (int i = 0; i < AttributeIcons.FILTERS.size(); i++) {
            AttributeIcons.Filter filter = AttributeIcons.FILTERS.get(i);
            int ix = x + (i / rows) * 22;
            int iy = y + (i % rows) * 19;
            boolean selected = filter.id().equals(filterId);
            boolean hover = mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 18;
            gui.fill(ix - 1, iy - 1, ix + 19, iy + 17,
                    selected ? 0xFF78E6D0 : hover ? 0xFF637D78 : 0xFF314947);
            gui.fill(ix, iy, ix + 18, iy + 16, 0xD0101A22);
            AttributeIcons.draw(gui, ix + 1, iy, filter.id());
            filterRects.add(new Object[] { ix - 1, iy - 1, 20, 18, filter.id() });
            if (hover) {
                String label = Component.translatable(filter.labelKey()).getString();
                gui.fill(mainX + 2, viewTop + 1, mainX + font.width(label) + 8,
                        viewTop + font.lineHeight + 5, 0xDD07131B);
                gui.text(font, label, mainX + 5, viewTop + 4, 0xFFFFFFFF);
            }
        }
    }

    private void drawEquipmentGrid(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        XianxiaUi.drawSubPanel(gui, mainX - 4, viewTop - 4, mainW + 8, viewBottom - viewTop + 8);
        equipRects.clear();
        int cols = columns();
        int y = viewTop - scrollY;
        // 只裁剪会滚动的标题、图标与价格；外框和滚动条保持完整显示。
        gui.enableScissor(mainX, viewTop, mainX + mainW, viewBottom);
        for (int tier = EquipmentRegistry.TIER_COMMON; tier <= EquipmentRegistry.TIER_LEGEND; tier++) {
            List<EquipDef> defs = filtered(tier);
            if (defs.isEmpty()) continue;
            String title = Component.translatable(switch (tier) {
                case EquipmentRegistry.TIER_FINE -> "screen.myriad.shop.tab_fine";
                case EquipmentRegistry.TIER_LEGEND -> "screen.myriad.shop.tab_legend";
                default -> "screen.myriad.shop.tab_common";
            }).getString();
            if (y >= viewTop - 12 && y < viewBottom) {
                gui.text(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                        mainX + 4, y + 2, tierColor(tier));
            }
            y += 16;
            for (int i = 0; i < defs.size(); i++) {
                EquipDef def = defs.get(i);
                int ix = mainX + (i % cols) * CELL_W + 5;
                int iy = y + (i / cols) * CELL_H;
                if (iy + CELL_H < viewTop || iy > viewBottom) continue;
                boolean selected = def.id().equals(selectedId);
                boolean hover = mouseX >= ix - 3 && mouseX <= ix + 34
                        && mouseY >= Math.max(viewTop, iy - 2) && mouseY <= Math.min(viewBottom, iy + 39);
                if (selected || hover) gui.fill(ix - 3, iy - 2, ix + 34, iy + 39,
                        selected ? 0x5578E6D0 : 0x22FFFFFF);
                drawEquipIcon(gui, font, ix, iy, 28, def.id());
                int cost = actualPrice(def);
                String price = String.valueOf(cost);
                gui.text(font, price, ix + 14 - font.width(price) / 2, iy + 30, canAfford(cost) ? GOLD_COLOR : 0xFFAD7F7F);
                int clickTop = Math.max(viewTop, iy - 2);
                int clickBottom = Math.min(viewBottom, iy + 39);
                if (clickBottom > clickTop) {
                    equipRects.add(new Object[] { ix - 3, clickTop, 37, clickBottom - clickTop, def.id() });
                }
            }
            y += ((defs.size() + cols - 1) / cols) * CELL_H + 5;
        }
        gui.disableScissor();
        if (maxScroll() > 0) {
            int trackX = mainX + mainW - 4;
            int viewH = viewBottom - viewTop;
            int thumbH = Math.max(18, viewH * viewH / Math.max(viewH, contentHeight()));
            int thumbY = viewTop + (viewH - thumbH) * scrollY / maxScroll();
            gui.fill(trackX, viewTop, trackX + 2, viewBottom, 0xFF263C3A);
            gui.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF78E6D0);
        }
    }

    private void drawDetail(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        recipeRects.clear();
        detailLeft = x + 3; detailRight = x + w - 3;
        detailTop = y + 3; detailBottom = y + h - 34;
        if (!java.util.Objects.equals(detailSelection, selectedId)) {
            detailSelection = selectedId; detailScroll = 0; detailMaxScroll = 0;
        }
        XianxiaUi.drawSubPanel(gui, x, y, w, h);
        if (selectedId == null) {
            String hint = Component.translatable("screen.myriad.shop.select_hint").getString();
            gui.text(font, hint, x + (w - font.width(hint)) / 2, y + 18, 0xFF8B9A99);
            return;
        }
        int center = x + w / 2;
        gui.enableScissor(x + 3, y + 3, x + w - 3, y + h - 34);
        int ty = y + 10 - detailScroll;
        int price;
        if (TacticalSupplySystem.isSupply(selectedId)) {
            drawSupplyIcon(gui, center - 28, ty, 56, selectedId);
            ty += 62;
            String name = Component.translatable("item.myriad." + selectedId).getString();
            gui.text(font, name, center - font.width(name) / 2, ty, 0xFF78E6D0);
            ty += 14;
            price = TacticalSupplySystem.supplyPrice(selectedId);
            for (var line : font.split(Component.translatable("item.myriad." + selectedId + ".desc"), w - 16)) {
                gui.text(font, line, x + 8, ty, 0xFFCCCCCC);
                ty += 11;
            }
        } else {
            EquipDef def = EquipmentRegistry.get(selectedId);
            if (def == null) { gui.disableScissor(); return; }
            String treeLabel = Component.translatable("screen.myriad.shop.recipe").getString();
            gui.text(font, treeLabel, center - font.width(treeLabel) / 2, ty, SECTION_COLOR);
            ty += 17;
            ty = drawRecipe(gui, font, def, x + 8, ty, w - 16, 0) + 8;
            if (def.ingredients().length == 0) {
                for (var line : font.split(Component.translatable("screen.myriad.shop.recipe_leaf"), w - 16)) {
                    gui.text(font, line, x + 8, ty, 0xFF8B9A99); ty += 11;
                }
            }
            String name = Component.translatable(def.nameKey()).getString();
            gui.text(font, name, center - font.width(name) / 2, ty, tierColor(def.tier()));
            ty += 14;
            price = actualPrice(def);
            if ("jungle_blade".equals(def.id())) {
                for (var line : font.split(Component.translatable("equipment.myriad.jungle_blade.desc"), w - 16)) {
                    gui.text(font, line, x + 8, ty, 0xFFBDE7CD); ty += 11;
                }
            }
            for (var entry : def.stats().entrySet()) {
                String attr = Component.translatable(entry.getKey().value().getDescriptionId()).getString();
                boolean pct = entry.getValue().operation()
                        == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                String line = attr + (pct ? " +" + Math.round(entry.getValue().amount() * 100) + "%"
                        : " +" + Math.round(entry.getValue().amount()));
                gui.text(font, line, x + 8, ty, attrColor(entry.getKey().value().getDescriptionId()));
                ty += 11;
            }
        }
        detailMaxScroll = Math.max(0, ty + detailScroll + 6 - detailBottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        gui.disableScissor();
        if (detailMaxScroll > 0) {
            int track = detailBottom - detailTop;
            int thumb = Math.max(12, track * track / (track + detailMaxScroll));
            int thumbY = detailTop + (track - thumb) * detailScroll / detailMaxScroll;
            gui.fill(x + w - 4, detailTop, x + w - 2, detailBottom, 0xFF263C3A);
            gui.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumb, 0xFF78E6D0);
        }
        for (Object[] rect : recipeRects) {
            if (!inside(mouseX, mouseY, rect)) continue;
            EquipDef hovered = EquipmentRegistry.get((String)rect[4]);
            if (hovered == null) continue;
            String label = Component.translatable(hovered.nameKey()).getString();
            label = font.plainSubstrByWidth(label, w - 16);
            gui.fill(x + 4, detailBottom - 15, x + w - 4, detailBottom, 0xF0081520);
            gui.text(font, label, x + 8, detailBottom - 12, tierColor(hovered.tier()));
        }
        String priceText = Component.translatable("screen.myriad.shop.price", price).getString();
        gui.text(font, priceText, center - font.width(priceText) / 2, y + h - 28, GOLD_COLOR);
        int btnW = w - 14;
        int bx = x + 7;
        int by = this.height - 38;
        boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + 27;
        String blocked = purchaseBlockReason();
        if (blocked != null) {
            String reason = Component.translatable(blocked).getString();
            gui.text(font, font.plainSubstrByWidth(reason, w - 16), x + 8, by - 13, 0xFFD7A994);
        }
        XianxiaUi.drawActionButton(gui, font, bx, by, btnW, 27,
                Component.translatable(blocked == null ? "screen.myriad.shop.buy_supply" : "screen.myriad.shop.disabled", price).getString(),
                blocked == null, hover && blocked == null);
    }

    /** Weight by leaf count so sibling subtrees never overlap. Repeated ingredients remain distinct nodes. */
    private int recipeLeaves(EquipDef def, int depth) {
        if (depth >= 8 || def.ingredients().length == 0) return 1;
        int leaves = 0;
        for (String id : def.ingredients()) {
            EquipDef child = EquipmentRegistry.get(id);
            if (child != null) leaves += recipeLeaves(child, depth + 1);
        }
        return Math.max(1, leaves);
    }

    private int drawRecipe(GuiGraphicsExtractor gui, Font font, EquipDef def, int x, int y, int w, int depth) {
        int center = x + w / 2, size = Math.min(28, Math.max(12, w - 8));
        int end = y + 43;
        if (depth < 8) {
            int total = recipeLeaves(def, depth), used = 0;
            for (String id : def.ingredients()) {
                EquipDef child = EquipmentRegistry.get(id);
                if (child == null) continue;
                int weight = recipeLeaves(child, depth + 1);
                int childX = x + w * used / total;
                int childW = w * (used + weight) / total - (childX - x);
                int childCenter = childX + childW / 2;
                int childY = y + 62;
                recipeLine(gui, childCenter, childY - 3, center, y + 42);
                // Arrow head points upward, from component to its result.
                recipeLine(gui, center - 3, y + 47, center, y + 42);
                recipeLine(gui, center + 3, y + 47, center, y + 42);
                end = Math.max(end, drawRecipe(gui, font, child, childX, childY, childW, depth + 1));
                used += weight;
            }
        }
        int ix = center - size / 2;
        gui.fill(ix - 2, y - 2, ix + size + 2, y + size + 2, tierColor(def.tier()));
        gui.fill(ix - 1, y - 1, ix + size + 1, y + size + 1, 0xFF0B1B25);
        drawEquipIcon(gui, font, ix, y, size, def.id());
        String cost = Integer.toString(def.totalPrice());
        gui.text(font, cost, center - font.width(cost) / 2, y + 31, GOLD_COLOR);
        int top = Math.max(detailTop, y - 2), bottom = Math.min(detailBottom, y + 42);
        if (bottom > top) recipeRects.add(new Object[] { ix - 2, top, size + 4, bottom - top, def.id() });
        return end;
    }

    private static void recipeLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(1, steps);
            int y = y1 + (y2 - y1) * i / Math.max(1, steps);
            gui.fill(x, y, x + 1, y + 1, 0xFF6DAEA8);
        }
    }

    private static int tierColor(int tier) {
        return tier == EquipmentRegistry.TIER_FINE ? 0xFF55AAFF
                : tier == EquipmentRegistry.TIER_LEGEND ? 0xFFFFAA55 : 0xFFFFFFFF;
    }

    private static int attrColor(String id) {
        if (id.contains("health") || id.contains("lifesteal")) return 0xFFFF7F7F;
        if (id.contains("spell") || id.contains("magic") || id.contains("mana")) return 0xFF7FB8FF;
        if (id.contains("speed") || id.contains("crit")) return 0xFFFFAA55;
        if (id.contains("defense") || id.contains("resistance")) return 0xFFBFBFBF;
        return 0xFFFFD700;
    }

    private void drawEquipIcon(GuiGraphicsExtractor gui, Font font, int x, int y, int size, String id) {
        if (id == null || id.isEmpty()) return;
        if ("jungle_blade".equals(id)) {
            gui.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD), x + (size - 16) / 2, y + (size - 16) / 2);
            return;
        }
        if (com.myriad.client.SkillIcon.hasResource("textures/gui/sprites/eq_" + id + ".png")) {
            gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath("myriad", "eq_" + id), x, y, size, size);
        } else {
            String name = Component.translatable("equipment.myriad." + id).getString();
            String mark = name.isEmpty() ? "?" : name.substring(0, 1);
            gui.text(font, mark, x + (size - font.width(mark)) / 2,
                    y + (size - font.lineHeight) / 2, 0xFFFFFFFF);
        }
    }

    private void drawSupplyIcon(GuiGraphicsExtractor gui, int x, int y, int size, String id) {
        gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath("myriad", "supply_" + id), x, y, size, size);
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
