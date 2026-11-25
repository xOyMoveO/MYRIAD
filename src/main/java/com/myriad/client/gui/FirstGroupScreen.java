package com.myriad.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.MyriadClient;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.SkillIcon;
import com.myriad.client.SkillTooltipDescriptions;
import com.myriad.client.UiFeedback;
import com.myriad.client.gui.SkillChoiceLayout.Box;
import com.myriad.game.TacticalSupplySystem;
import com.myriad.network.NetworkSetup;
import com.myriad.network.Packets;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import static com.myriad.client.gui.SkillChoiceTheme.*;

/** Character skills, attributes and supplies share a frame and keep server-validated purchases. */
public final class FirstGroupScreen extends Screen {
    private static final int SKILLS = 0, ATTRIBUTES = 1, SUPPLIES = 2;
    private static final int LINE_HEIGHT = 11;
    // MouseHandler recognizes a second click within 250 ms. Keep the original card positions until then.
    private static final long DOUBLE_CLICK_MILLIS = 250;
    private static final String ATTRIBUTE_PREFIX = "g1_attribute_";
    private static final String[] GROUPS = {"passive", "regular", "main", "ultimate"};
    private static final String[] TABS = {"skills", "attributes", "supplies"};
    private static final String[] TAB_LABELS = {"screen.myriad.g1.tab_skills",
            "screen.myriad.g1.tab_attributes", "screen.myriad.shop.tab_supply"};

    private int page, scroll, detailScroll, detailMaximum, tooltipScroll, labelWidth;
    private boolean draggingContent, draggingDetails, activationHeld, detailsOpen;
    private long detailsOpenAt;
    private double dragOffset;
    private String role = "", focused = "", keyboardTooltip = "", lastClicked = "";
    private final SkillChoiceInteraction selection = new SkillChoiceInteraction();
    private final List<Action> actions = new ArrayList<>();
    private TooltipTarget tooltip, nextTooltip;
    private SkillChoiceTooltipLayout tooltipLayout;
    private Object submittedSnapshot;
    private long submittedAt;

    private enum ActionKind { CLOSE, PAGE, ITEM, CONFIRM, SUPPLY, DETAILS }
    private record Action(Box box, String id, ActionKind kind, boolean content) {}
    private record TooltipTarget(String id, Box anchor, List<String> lines) {}
    private record TextLine(FormattedCharSequence text, int color) {}

    public FirstGroupScreen(boolean attributes) {
        super(Component.translatable("screen.myriad.g1.title"));
        page = attributes ? ATTRIBUTES : SKILLS;
        Packets.sendToServer(new NetworkSetup.RequestSyncPayload());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        actions.clear();
        draggingContent = draggingDetails = activationHeld = false;
        lastClicked = "";
        clearTooltip();
        labelWidth = 0;
        for (String group : GROUPS) labelWidth = Math.max(labelWidth, font.width(groupName(group)));
        clampScroll();
    }

    private static String text(String key, Object... args) { return Component.translatable(key, args).getString(); }
    private static String groupName(String group) { return text("screen.myriad.g1." + group); }

    private SkillChoiceLayout layout() { return SkillChoiceLayout.create(width, height, scroll, labelWidth, page == SKILLS && detailsOpen); }

    private int maximum(SkillChoiceLayout layout) {
        return switch (page) {
            case SKILLS -> layout.maxY();
            case ATTRIBUTES -> layout.attributeMaximum();
            default -> layout.supplyMaximum(TacticalSupplySystem.supplyIds().size());
        };
    }

    private void clampScroll() { scroll = Math.clamp(scroll, 0, maximum(layout())); }

    private void clearTooltip() {
        tooltip = nextTooltip = null;
        tooltipLayout = null;
        tooltipScroll = 0;
        keyboardTooltip = "";
    }

    private boolean pending() {
        if (submittedSnapshot != null && (ClientDataHolder.get() != submittedSnapshot
                || System.nanoTime() - submittedAt > 1_500_000_000L)) submittedSnapshot = null;
        return submittedSnapshot != null;
    }

    private String error(String id) {
        var data = ClientDataHolder.get();
        if (data == null) return "screen.myriad.g1.loading";
        if (pending()) return "screen.myriad.g1.processing";
        if (id.startsWith(ATTRIBUTE_PREFIX)) {
            String key = id.substring(ATTRIBUTE_PREFIX.length());
            if (!FirstGroupGrowth.ATTRIBUTES.contains(key)) return "message.myriad.g1.invalid_skill";
            if (!UiFeedback.shopAllowed) return "message.myriad.shop_base_only";
            return FirstGroupGrowth.purchaseError(data.buffs().getOrDefault(id, 0), data.gold(), data.training() && data.noGoldCost());
        }
        if (TacticalSupplySystem.isSupply(id)) {
            if (!UiFeedback.shopAllowed) return "message.myriad.shop_base_only";
            return !(data.training() && data.noGoldCost()) && data.gold() < TacticalSupplySystem.supplyPrice(id)
                    ? "message.myriad.equip_no_gold" : null;
        }
        return FirstGroupSkills.learnError(data.characterId(), data.skillLevels(), data.skillPoints(), data.level(), id);
    }

    private void submit(String id) {
        if (error(id) != null) return;
        submittedSnapshot = ClientDataHolder.get();
        submittedAt = System.nanoTime();
        if (id.startsWith(ATTRIBUTE_PREFIX) || TacticalSupplySystem.isSupply(id)) {
            Packets.sendToServer(new NetworkSetup.ShopBuyPayload(id));
        } else {
            Packets.sendToServer(new NetworkSetup.UpgradeSkillPayload(id));
        }
        actions.clear();
    }

    private boolean perform(Action action, boolean twice, boolean keyboard) {
        switch (action.kind) {
            case CLOSE -> onClose();
            case PAGE -> {
                int next = Integer.parseInt(action.id.substring(5));
                if (next != page) {
                    page = next;
                    scroll = detailScroll = detailMaximum = 0;
                    detailsOpen = false;
                    detailsOpenAt = 0;
                    selection.reset();
                    clearTooltip();
                    actions.clear();
                }
            }
            case ITEM -> {
                String previous = selection.selected();
                boolean buy = selection.select(action.id, twice, error(action.id) == null);
                if (!action.id.equals(previous)) {
                    detailScroll = 0;
                    clearTooltip();
                    if (page == SKILLS && previous.isEmpty()) {
                        detailsOpenAt = keyboard ? Util.getMillis() : Util.getMillis() + DOUBLE_CLICK_MILLIS;
                    }
                }
                if (keyboard) keyboardTooltip = action.id + ":tile";
                if (buy) submit(action.id);
            }
            case CONFIRM -> {
                String id = selection.selected();
                if (selection.confirm(id, twice, !id.isEmpty() && error(id) == null)) submit(id);
            }
            case SUPPLY -> {
                if (!twice && error(action.id) == null) submit(action.id);
            }
            case DETAILS -> { }
        }
        return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (MyriadClient.KEY_INFO.matchesMouse(event)) return closeFromBinding();
        if (event.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, twice);
        keyboardTooltip = "";
        var layout = layout();
        double x = event.x(), y = event.y();
        boolean insideTooltip = tooltipLayout != null && tooltipLayout.box().contains(x, y)
                && tooltip != null && !tooltip.anchor.contains(x, y);
        if (insideTooltip && !layout.close().contains(x, y)) {
            lastClicked = "";
            return true;
        }
        int maximum = maximum(layout);
        if (maximum > 0 && layout.verticalTrack().contains(x, y)) {
            lastClicked = "";
            draggingContent = true;
            return startDrag(layout.verticalThumb(maximum, scroll), y);
        }
        if (layout.details() != null && detailMaximum > 0 && layout.detailsTrack().contains(x, y)) {
            lastClicked = "";
            draggingDetails = true;
            focused = "details";
            return startDrag(SkillChoiceLayout.thumb(layout.detailsTrack(), detailMaximum, detailScroll), y);
        }
        for (var action : actions) {
            if (!action.box.contains(x, y) || action.content && !layout.canHit(action.box, x, y)) continue;
            focused = action.id;
            boolean sameActionTwice = SkillChoiceInteraction.sameActionTwice(lastClicked, action.id, twice);
            lastClicked = action.id;
            return perform(action, sameActionTwice, false);
        }
        lastClicked = "";
        return super.mouseClicked(event, twice);
    }

    private boolean startDrag(Box thumb, double y) {
        if (y >= thumb.y() && y < thumb.bottom()) {
            dragOffset = y - thumb.y();
            return true;
        }
        dragOffset = thumb.h() / 2.0;
        return drag(y);
    }

    private boolean drag(double y) {
        var layout = layout();
        if (draggingDetails && layout.details() != null) {
            detailScroll = SkillChoiceLayout.scrollTo(layout.detailsTrack(), y, detailMaximum, dragOffset);
        } else if (draggingContent) {
            setScroll(SkillChoiceLayout.scrollTo(layout.verticalTrack(), y, maximum(layout), dragOffset));
        }
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return (draggingContent || draggingDetails) && event.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT
                ? drag(event.y()) : super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if ((draggingContent || draggingDetails) && event.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingContent = draggingDetails = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (tooltip != null && tooltipLayout != null && tooltipLayout.maximum() > 0
                && (tooltip.anchor.contains(x, y) || tooltipLayout.box().contains(x, y))) {
            tooltipScroll = Math.clamp(tooltipScroll - (int) Math.round(dy * 3), 0, tooltipLayout.maximum());
            return true;
        }
        var layout = layout();
        int delta = (int) Math.round(dy * 24);
        if (layout.details() != null && layout.details().contains(x, y)) {
            detailScroll = Math.clamp(detailScroll - delta, 0, detailMaximum);
            return true;
        }
        if (!layout.viewport().contains(x, y) && !layout.verticalTrack().contains(x, y)) return super.mouseScrolled(x, y, dx, dy);
        setScroll(scroll - delta);
        return true;
    }

    private void setScroll(int requested) {
        int next = Math.clamp(requested, 0, maximum(layout()));
        if (next != scroll) {
            scroll = next;
            clearTooltip();
            actions.clear();
        }
    }

    private boolean closeFromBinding() {
        MyriadClient.KEY_INFO.consumeClick();
        onClose();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (MyriadClient.KEY_INFO.matches(event)) return closeFromBinding();
        if (event.key() == InputConstants.KEY_TAB && !actions.isEmpty()) {
            var ids = actions.stream().map(Action::id).toList();
            Action action = actions.get(SkillChoiceInteraction.stepFocus(ids, focused, event.hasShiftDown()));
            focused = action.id;
            if (action.content) {
                var layout = layout();
                setScroll(layout.reveal(action.box, scroll, maximum(layout)));
            }
            clearTooltip();
            return true;
        }
        if (isActivationKey(event)) {
            if (activationHeld) return true;
            for (var action : actions) {
                if (action.id.equals(focused)) {
                    activationHeld = true;
                    return perform(action, false, true);
                }
            }
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            int delta = event.key() == InputConstants.KEY_PAGEDOWN ? 1 : -1;
            if (tooltipLayout != null && tooltipLayout.maximum() > 0) {
                tooltipScroll = Math.clamp(tooltipScroll + delta * Math.max(1, tooltipLayout.visibleLines() - 1), 0, tooltipLayout.maximum());
            } else if (layout().details() != null && ("details".equals(focused) || !keyboardTooltip.isEmpty())) {
                detailScroll = Math.clamp(detailScroll + delta * layout().detailsViewport().h(), 0, detailMaximum);
            } else {
                setScroll(scroll + delta * layout().viewport().h());
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (isActivationKey(event)) activationHeld = false;
        return super.keyReleased(event);
    }

    private static boolean isActivationKey(KeyEvent event) {
        return event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
                || event.key() == InputConstants.KEY_SPACE;
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        int remaining = maxWidth - font.width("…");
        return remaining <= 0 ? font.plainSubstrByWidth(value, Math.max(1, maxWidth))
                : font.plainSubstrByWidth(value, remaining) + "…";
    }

    private void textIn(GuiGraphicsExtractor g, String value, int x, int y, int maxWidth, int color) {
        if (maxWidth > 0) g.text(font, fit(value, maxWidth), x, y, color);
    }

    private void wrapped(GuiGraphicsExtractor g, String value, Box box, int lines, int color) {
        var rows = font.split(Component.literal(value), Math.max(1, box.w()));
        int size = Math.min(lines, rows.size());
        int top = box.y() + Math.max(0, (box.h() - size * LINE_HEIGHT) / 2);
        for (int i = 0; i < size; i++) g.text(font, rows.get(i), box.x(), top + i * LINE_HEIGHT, color);
    }

    private void button(GuiGraphicsExtractor g, Action action, String label, boolean enabled,
            boolean selected, SkillChoiceLayout layout, int mx, int my) {
        var b = action.box;
        boolean hover = action.content ? layout.canHit(b, mx, my) : b.contains(mx, my);
        SkillChoiceTheme.panelButton(g, font, b, label, enabled, hover || focused.equals(action.id), selected);
        actions.add(action);
    }

    private void offer(String id, Box anchor, Supplier<List<String>> details, int mx, int my, Box clip) {
        boolean sameSource = tooltip != null && tooltip.id.equals(id) && tooltip.anchor.equals(anchor);
        boolean over = anchor.contains(mx, my) && (clip == null || clip.contains(mx, my));
        boolean retained = tooltipLayout != null && tooltipLayout.keepsOpen(anchor, mx, my);
        if (tooltip != null && tooltipLayout != null && tooltipLayout.box().contains(mx, my) && !sameSource) return;
        if (keyboardTooltip.equals(id) && (clip == null || anchor.overlaps(clip)) || over || sameSource && retained) {
            if (nextTooltip == null || !keyboardTooltip.equals(nextTooltip.id)) nextTooltip = new TooltipTarget(id, anchor, details.get());
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        actions.clear();
        nextTooltip = null;
        var data = ClientDataHolder.get();
        if (data != null && !FirstGroupSkills.active(data.characterId())) {
            onClose();
            return;
        }
        if (data != null && !role.equals(data.characterId())) {
            role = data.characterId();
            selection.reset();
            scroll = detailScroll = 0;
            detailsOpen = false;
            detailsOpenAt = 0;
            clearTooltip();
        }
        if (detailsOpenAt != 0 && Util.getMillis() >= detailsOpenAt) {
            detailsOpen = true;
            detailsOpenAt = 0;
        }
        clampScroll();
        var layout = layout();
        var panel = layout.panel();
        var header = layout.headerText();
        var view = layout.viewport();
        backdrop(g, layout);
        g.nextStratum();
        textIn(g, data == null ? getTitle().getString() : getTitle().getString() + " · " + text("character.myriad." + role),
                header.x(), layout.titleY(), header.w(), GOLD);
        if (data != null) {
            String status = text("screen.myriad.g1.status", data.level(), data.skillPoints(), data.gold());
            if (font.width(status) > header.w()) status = text("screen.myriad.g1.status_compact", data.level(), data.skillPoints(), data.gold());
            textIn(g, status, header.x(), layout.statusY(), header.w(), JADE);
            offer("vitals", header, () -> List.of(text("screen.myriad.g1.vitals", Math.round(data.health()), Math.round(data.maxHealth()), data.mana())),
                    mx, my, null);
        }
        SkillChoiceTheme.divider(g, header.x(), panel.right() - (header.x() - panel.x()), layout.headerDividerY(), 0xFF52837B);
        drawTabs(g, layout, mx, my);
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        if (data == null) textIn(g, text("screen.myriad.g1.loading"), view.x() + 6, view.y() + 6, view.w() - 12, JADE);
        else if (page == SKILLS) drawSkills(g, layout, mx, my);
        else if (page == ATTRIBUTES) drawAttributes(g, layout, mx, my);
        else drawSupplies(g, layout, mx, my);
        g.disableScissor();
        if (data != null && layout.details() != null) drawDetails(g, layout, mx, my);
        int maximum = maximum(layout);
        if (data != null && maximum > 0) scrollbar(g, layout.verticalTrack(), layout.verticalThumb(maximum, scroll));
        SkillChoiceTheme.divider(g, layout.body().x(), layout.body().right(), layout.footerDividerY(), 0xFF52837B);
        if (data != null) {
            if (page == SUPPLIES) drawHint(g, layout, "screen.myriad.g1.supply_hint");
            else drawSelected(g, layout, mx, my);
        }
        button(g, new Action(layout.close(), "close", ActionKind.CLOSE, false), "×", true, false, layout, mx, my);
        drawTooltip(g);
    }

    private void drawTabs(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        var body = layout.body();
        if (!layout.topTabs()) {
            int x = layout.navigationDividerX();
            g.fill(x, body.y() + 3, x + 1, body.bottom() - 3, 0xFF395E58);
        }
        for (int i = 0; i < TABS.length; i++) {
            var b = layout.tab(i);
            String label = text(TAB_LABELS[i]);
            button(g, new Action(b, "page:" + i, ActionKind.PAGE, false), b.h() >= 30 ? "" : label, true, page == i, layout, mx, my);
            if (b.h() >= 30) {
                int size = Math.min(16, b.h() - 20);
                int x = b.x() + (b.w() - size) / 2;
                CultivationIcons.navigation(g, TABS[i], new Box(x, b.y() + 4, size, size), page == i ? GOLD : JADE);
                String fitted = fit(label, b.w() - 6);
                g.text(font, fitted, b.x() + (b.w() - font.width(fitted)) / 2, b.bottom() - 12, page == i ? GOLD : JADE);
            }
            int index = i;
            if (font.width(label) > b.w() - 8) offer("nav:" + i, b, () -> List.of(text(TAB_LABELS[index])), mx, my, null);
        }
    }

    private void drawSkills(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        var data = ClientDataHolder.get();
        var candidates = FirstGroupSkills.candidates(data.characterId());
        var view = layout.viewport();
        for (int row = 0; row < 4; row++) {
            int first = SkillChoiceLayout.firstIndex(row);
            int group = first / 2;
            Box category = layout.category(group);
            if (category.overlaps(view)) {
                String name = groupName(GROUPS[group]);
                String rule = text(group == 1 ? "screen.myriad.g1.both_short" : "screen.myriad.g1.one_short");
                int y = category.y() + (category.h() - 2 * LINE_HEIGHT) / 2;
                textIn(g, name, category.x() + 4, y, category.w() - 6, GOLD);
                textIn(g, rule, category.x() + 4, y + LINE_HEIGHT, category.w() - 6, MUTED);
                offer("group:" + group, category, () -> List.of(name, text(group == 1 ? "screen.myriad.g1.both" : "screen.myriad.g1.one")), mx, my, view);
            }
            for (int index = first; index <= first + 1; index++) {
                var skill = candidates.get(index);
                Box b = layout.card(index);
                actions.add(new Action(b, skill.getId(), ActionKind.ITEM, true));
                if (!b.overlaps(view)) continue;
                int rank = data.skillLevels().getOrDefault(skill.getId(), 0);
                int other = FirstGroupSkills.opposite(index);
                boolean locked = other >= 0 && data.skillLevels().getOrDefault(candidates.get(other).getId(), 0) > 0;
                boolean hovered = layout.canHit(b, mx, my);
                card(g, b, selection.selected().equals(skill.getId()), rank > 0, locked, hovered || focused.equals(skill.getId()));
                int size = Math.max(1, Math.min(32, Math.min(b.h() - 8, b.w() / 3)));
                int iconX = b.x() + 5;
                int iconY = b.y() + (b.h() - size) / 2;
                SkillIcon.drawArtwork(g, font, iconX, iconY, size, skill);
                if (other >= 0 && size >= 14) {
                    String branch = (index & 1) == 0 ? "A" : "B";
                    g.fill(iconX, iconY, iconX + 9, iconY + 9, 0xF0091921);
                    g.text(font, branch, iconX + 2, iconY, GOLD);
                }
                int start = iconX + size + 5;
                int textWidth = b.right() - 4 - start;
                if (textWidth > 0) {
                    if (b.h() >= 35) wrapped(g, SkillTooltipDescriptions.abilityName(skill), new Box(start, b.y() + 1, textWidth, b.h() - 11), 2, locked ? MUTED : GOLD);
                    else textIn(g, SkillTooltipDescriptions.abilityName(skill), start, b.y() + 4, textWidth, locked ? MUTED : GOLD);
                    textIn(g, locked ? text("screen.myriad.g1.locked") : rank + "/" + skill.maxRank(),
                            start, b.bottom() - 11, textWidth, locked ? MUTED : JADE);
                }
                if (!layout.wideDetails()) {
                    offer(skill.getId() + ":tile", b, () -> skillLines(skill, rank, error(skill.getId())), mx, my, view);
                }
            }
        }
    }

    private List<String> skillLines(AbilitySkill skill, int rank, String reason) {
        var data = ClientDataHolder.get();
        var lines = new ArrayList<String>();
        lines.add(SkillTooltipDescriptions.abilityName(skill));
        int level = skill.baseAvailable() ? rank : Math.max(1, rank);
        String rankText = skill.baseAvailable() ? text("screen.myriad.rivet.anchor_rank", rank, skill.maxRank())
                : text(rank > 0 ? "screen.myriad.g1.learned_rank" : "screen.myriad.g1.unlearned_rank", rank, skill.maxRank());
        if (rank == 0 && !skill.baseAvailable()) rankText += " · " + text("hud.myriad.combat.level", level);
        lines.add(rankText);
        if (skill.isPassive()) lines.add(text("screen.myriad.g1.passive_cost"));
        else {
            double reduction = ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId());
            boolean noCooldown = data.training() && data.noCooldown();
            double base = skill.getCooldownTicks(level) / 20.0;
            if (noCooldown || Double.isFinite(reduction)) {
                double cooldown = noCooldown ? 0 : base * (1 - Math.clamp(reduction, 0, ModAttributes.MAX_COOLDOWN_REDUCTION) / 100);
                double remaining = rank > 0 || skill.baseAvailable() ? ClientDataHolder.cooldownTicks(skill.slot()) / 20.0 : 0;
                lines.add(text("screen.myriad.g1.current_cost", Math.round(skill.getManaCost()),
                        String.format(Locale.ROOT, "%.1f", cooldown), String.format(Locale.ROOT, "%.1f", remaining)));
            } else {
                lines.add(text("screen.myriad.g1.resource", Math.round(skill.getManaCost()), String.format(Locale.ROOT, "%.1f", base)));
            }
        }
        lines.addAll(SkillTooltipDescriptions.describeLines(skill, level));
        lines.add(text(FirstGroupSkills.opposite(skill.index()) < 0 ? "screen.myriad.g1.both" : "screen.myriad.g1.one"));
        if (reason != null) lines.add(text(reason));
        return lines;
    }

    private void drawDetails(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        Box box = layout.details();
        actions.add(new Action(box, "details", ActionKind.DETAILS, false));
        plate(g, box, focused.equals("details") ? JADE : 0xFF315854, 0xFF112830);
        var data = ClientDataHolder.get();
        var skill = FirstGroupSkills.byId(selection.selected());
        if (skill == null) return;
        List<String> description = skillLines(skill, data.skillLevels().getOrDefault(skill.getId(), 0), error(skill.getId()));
        Box view = layout.detailsViewport();
        var lines = wrapLines(description, view.w());
        detailMaximum = Math.max(0, lines.size() * LINE_HEIGHT - view.h());
        detailScroll = Math.clamp(detailScroll, 0, detailMaximum);
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        for (int index = 0; index < lines.size(); index++) {
            int y = view.y() + index * LINE_HEIGHT - detailScroll;
            if (y + LINE_HEIGHT <= view.y() || y >= view.bottom()) continue;
            var line = lines.get(index);
            g.text(font, line.text, view.x(), y, line.color);
        }
        g.disableScissor();
        if (detailMaximum > 0) {
            var track = layout.detailsTrack();
            scrollbar(g, track, SkillChoiceLayout.thumb(track, detailMaximum, detailScroll));
        }
    }

    private long attributeTotal() {
        return FirstGroupGrowth.ATTRIBUTES.stream().mapToLong(key -> ClientDataHolder.syncedCounter(ATTRIBUTE_PREFIX + key)).sum();
    }

    private int supplyCost(String id) {
        var data = ClientDataHolder.get();
        return data.training() && data.noGoldCost() ? 0 : TacticalSupplySystem.supplyPrice(id);
    }

    private static String nextAttributePrice(int rank) {
        long price = FirstGroupGrowth.price(rank);
        return price == FirstGroupGrowth.PRICE_OVERFLOW ? text("message.myriad.g1.attribute_price_overflow")
                : text("screen.myriad.g1.attribute_next_price", price);
    }

    private static boolean hasteCapped(String key) {
        return key.equals("cooldown") && ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId())
                >= ModAttributes.MAX_COOLDOWN_REDUCTION;
    }

    private List<String> attributeLines(String key) {
        var data = ClientDataHolder.get();
        String id = ATTRIBUTE_PREFIX + key;
        int rank = ClientDataHolder.syncedCounter(id);
        var lines = new ArrayList<String>();
        lines.add(text("screen.myriad.g1.attribute." + key));
        lines.add(text("screen.myriad.g1.attribute_rank", rank));
        lines.add(text("screen.myriad.g1.attribute_effect." + key));
        if (hasteCapped(key)) lines.add(text("screen.myriad.g1.attribute_haste_capped", ModAttributes.MAX_COOLDOWN_REDUCTION));
        lines.add(nextAttributePrice(rank));
        if (data.training() && data.noGoldCost()) lines.add(text("screen.myriad.g1.attribute_free"));
        String reason = error(id);
        if (reason != null && !reason.equals("message.myriad.g1.attribute_price_overflow")) lines.add(text(reason));
        lines.add(text("screen.myriad.g1.attribute_click_hint"));
        return lines;
    }

    private void drawAttributes(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        var view = layout.viewport();
        textIn(g, text("screen.myriad.g1.attribute_budget_compact", attributeTotal()), view.x() + 4, view.y() + 4 - scroll, view.w() - 8, GOLD);
        for (int i = 0; i < FirstGroupGrowth.ATTRIBUTES.size(); i++) {
            String key = FirstGroupGrowth.ATTRIBUTES.get(i);
            String id = ATTRIBUTE_PREFIX + key;
            var row = layout.attributeRow(i, scroll);
            var icon = layout.attribute(i, scroll);
            actions.add(new Action(icon, id, ActionKind.ITEM, true));
            if (!row.overlaps(view)) continue;
            int rank = ClientDataHolder.syncedCounter(id);
            boolean selected = selection.selected().equals(id);
            boolean hovered = layout.canHit(icon, mx, my);
            card(g, row, selected, false, false, hovered || focused.equals(id));
            CultivationIcons.attribute(g, key, icon);
            var details = layout.attributeDetails(i, scroll);
            String name = text("screen.myriad.g1.attribute." + key);
            String acquired = text("screen.myriad.g1.attribute_rank", rank);
            int acquiredWidth = font.width(acquired);
            boolean sameLine = font.width(name) + acquiredWidth + 14 <= details.w();
            textIn(g, name, details.x(), details.y() + 1, details.w(), GOLD);
            int lineY = details.y() + LINE_HEIGHT;
            if (sameLine) textIn(g, acquired, details.right() - acquiredWidth, details.y() + 1, acquiredWidth, JADE);
            else {
                textIn(g, acquired, details.x(), lineY, details.w(), JADE);
                lineY += LINE_HEIGHT;
            }
            int quoteLines = Math.max(1, (details.bottom() - lineY + 2) / LINE_HEIGHT);
            wrapped(g, nextAttributePrice(rank), new Box(details.x(), lineY, details.w(), quoteLines * LINE_HEIGHT), quoteLines, GOLD);
            if (sameLine && quoteLines > 1 && font.width(nextAttributePrice(rank)) <= details.w() && hasteCapped(key)) {
                textIn(g, text("screen.myriad.g1.attribute_haste_limit", ModAttributes.MAX_COOLDOWN_REDUCTION),
                        details.x(), details.bottom() - font.lineHeight, details.w(), MUTED);
            }
            offer(id + ":tile", icon, () -> attributeLines(key), mx, my, view);
        }
    }

    private void drawSupplies(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        int index = 0;
        var view = layout.viewport();
        for (String id : TacticalSupplySystem.supplyIds()) {
            var row = layout.supply(index, scroll);
            var buy = layout.supplyAction(index++, scroll);
            var action = new Action(buy, id, ActionKind.SUPPLY, true);
            if (!row.overlaps(view)) {
                actions.add(action);
                continue;
            }
            plate(g, row, 0xFF315854, INK);
            CultivationIcons.supply(g, id, new Box(row.x() + 5, row.y() + 5, 20, 20));
            int titleX = row.x() + 31;
            wrapped(g, text("item.myriad." + id), new Box(titleX, row.y(), Math.max(1, buy.x() - titleX - 5), row.h()), 2, GOLD);
            button(g, action, text("screen.myriad.g1.buy", supplyCost(id)), error(id) == null, false, layout, mx, my);
            offer(id + ":supply", row, () -> {
                var lines = new ArrayList<String>();
                lines.add(text("item.myriad." + id));
                lines.add(text("item.myriad." + id + ".desc"));
                lines.add(text("screen.myriad.g1.buy", supplyCost(id)));
                String reason = error(id);
                if (reason != null) lines.add(text(reason));
                return lines;
            }, mx, my, view);
        }
    }

    private void drawHint(GuiGraphicsExtractor g, SkillChoiceLayout layout, String key) {
        var body = layout.body();
        wrapped(g, text(key), new Box(body.x(), layout.footerDividerY() + 3, body.w(), layout.primary().h()), 2, MUTED);
    }

    private void drawSelected(GuiGraphicsExtractor g, SkillChoiceLayout layout, int mx, int my) {
        String id = selection.selected();
        if (id.isEmpty()) {
            drawHint(g, layout, page == SKILLS ? "screen.myriad.g1.icon_hint" : "screen.myriad.g1.attribute_hint");
            return;
        }
        String name;
        String label;
        Supplier<List<String>> details;
        if (page == SKILLS) {
            var skill = FirstGroupSkills.byId(id);
            if (skill == null || !skill.role().equals(role)) return;
            int rank = ClientDataHolder.get().skillLevels().getOrDefault(id, 0);
            name = SkillTooltipDescriptions.abilityName(skill);
            label = text(rank >= skill.maxRank() ? "screen.myriad.g1.maxed" : rank == 0 ? "screen.myriad.g1.learn" : "screen.myriad.g1.rank_up");
            details = () -> skillLines(skill, rank, error(id));
        } else {
            String key = id.substring(ATTRIBUTE_PREFIX.length());
            name = text("screen.myriad.g1.attribute." + key);
            var data = ClientDataHolder.get();
            long price = FirstGroupGrowth.price(ClientDataHolder.syncedCounter(id));
            if (data.training() && data.noGoldCost()) label = text("screen.myriad.g1.buy", 0);
            else if (price == FirstGroupGrowth.PRICE_OVERFLOW) label = text("screen.myriad.g1.attribute_price_limit");
            else label = text("screen.myriad.g1.buy", price);
            details = () -> attributeLines(key);
        }
        wrapped(g, text("screen.myriad.g1.selected", name), layout.selectedLabel(), 2, GOLD);
        button(g, new Action(layout.primary(), "confirm", ActionKind.CONFIRM, false),
                pending() ? text("screen.myriad.g1.processing") : label, error(id) == null, true, layout, mx, my);
        offer(id + ":selected", layout.selectedLabel(), details, mx, my, null);
    }

    private List<TextLine> wrapLines(List<String> description, int width) {
        var wrapped = new ArrayList<TextLine>();
        for (int i = 0; i < description.size(); i++) {
            for (var line : font.split(Component.literal(description.get(i)), Math.max(1, width))) {
                wrapped.add(new TextLine(line, i == 0 ? GOLD : JADE));
            }
        }
        return wrapped;
    }

    private void drawTooltip(GuiGraphicsExtractor g) {
        if (nextTooltip == null) {
            tooltip = null;
            tooltipLayout = null;
            tooltipScroll = 0;
            return;
        }
        if (tooltip == null || !tooltip.id.equals(nextTooltip.id)) tooltipScroll = 0;
        tooltip = nextTooltip;
        Box anchor = tooltip.anchor;
        int widest = 0, total = 0;
        for (String line : tooltip.lines) {
            int measured = font.width(line);
            widest = Math.max(widest, measured);
            total += measured;
        }
        int balanced = (int) Math.ceil(Math.sqrt(total * font.lineHeight * 1.7)) + 14;
        int preferred = Math.clamp(Math.min(widest + 14, balanced), 48, 240);
        var wrapped = wrapLines(tooltip.lines, SkillChoiceTooltipLayout.width(width, anchor, preferred) - 14);
        tooltipLayout = SkillChoiceTooltipLayout.create(width, height, anchor, wrapped.size(), tooltipScroll, preferred, layout().footerDividerY() - 3);
        tooltipScroll = tooltipLayout.scroll();
        var box = tooltipLayout.box();
        g.nextStratum();
        plate(g, box, GOLD, 0xFF081A21);
        g.nextStratum();
        g.enableScissor(box.x() + 2, box.y() + 2, box.right() - 2, box.bottom() - 2);
        int end = Math.min(wrapped.size(), tooltipScroll + tooltipLayout.visibleLines());
        for (int i = tooltipScroll; i < end; i++) {
            var line = wrapped.get(i);
            g.text(font, line.text, box.x() + 7, box.y() + 6 + (i - tooltipScroll) * SkillChoiceTooltipLayout.LINE_HEIGHT, line.color);
        }
        if (tooltipLayout.maximum() > 0) {
            textIn(g, text("screen.myriad.g1.tooltip_scroll", tooltipScroll + 1, end, wrapped.size()),
                    box.x() + 7, box.bottom() - 12, box.w() - 14, MUTED);
        }
        g.disableScissor();
    }
}
