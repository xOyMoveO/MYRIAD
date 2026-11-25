package com.myriad.client;

import static com.myriad.client.CombatHudLayout.GAP;
import static com.myriad.client.CombatHudLayout.SLOT;
import static com.myriad.client.CombatHudLayout.SLOT_STEP;

import com.myriad.client.CombatHudLayout.Bounds;
import com.myriad.client.CombatHudLayout.Layout;
import com.myriad.client.CombatHudSkin.Slice;
import com.myriad.client.CombatHudState.SkillStatus;
import com.myriad.client.CombatHudState.Slot;
import com.myriad.client.CombatHudState.Snapshot;
import com.myriad.mixin.ChatComponentAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public final class CombatHudOverlay {
    private static final int WHITE = 0xFFF6F0DF;
    private static final int TEXT_DIM = 0xFFC2CCCF;
    private static final int MANA = 0xFF508EBA;
    private static final int LIFE = 0xFFA74152;
    private static final int XP = 0xFF8568AD;
    private static final int ATTACK = 0xFF77A193;
    private static final int READY = 0xFF84CCAD;
    private static final int AMBER = 0xFFFFD78C;
    private static final int SLOT_GAP = SLOT_STEP - SLOT;
    private static final int[] RING_X = new int[24];
    private static final int[] RING_Y = new int[24];

    static {
        for (int point = 0; point < RING_X.length; point++) {
            double angle = Math.TAU * point / RING_X.length - Math.PI / 2;
            RING_X[point] = (int)Math.round(6 * Math.cos(angle));
            RING_Y[point] = (int)Math.round(6 * Math.sin(angle));
        }
    }

    private CombatHudOverlay() {}

    static Layout draw(GuiGraphicsExtractor gui, Font font) {
        if (Minecraft.getInstance().gui.hud.isHidden()) return null;
        var config = HudLayoutConfig.state(HudLayoutConfig.Element.COMBAT);
        if (!config.visible()) return null;
        var state = CombatHudState.current();
        if (state == null) return null;
        var roleRows = CombatHudRoleStatus.rows(state);
        var controls = controlRows(state);
        Layout layout = layout(gui.guiWidth(), gui.guiHeight(), config, state, roleRows.size(), controls.size(),
                focusedChat(gui.guiHeight()));
        if (layout == null) return null;
        boolean skin = SkillIcon.hasResource(CombatHudSkin.PATH);
        gui.pose().pushMatrix();
        gui.pose().translate(layout.bounds().x(), layout.bounds().y());
        gui.pose().scale(layout.scale());
        drawPanel(gui, font, layout, state, roleRows, controls, skin);
        gui.pose().popMatrix();
        return layout;
    }

    public static Layout preview(int width, int height) {
        var state = CombatHudState.current();
        return layout(width, height, HudLayoutConfig.state(HudLayoutConfig.Element.COMBAT), state,
                state == null ? 0 : CombatHudRoleStatus.rows(state).size(),
                state == null ? 0 : controlRows(state).size(), null);
    }

    private static Layout layout(int width, int height, HudLayoutConfig.State config, Snapshot state,
                                 int roleRows, int controls, Bounds chat) {
        return CombatHudLayout.at(width, height, config.x(), config.y(), config.scale(),
                roleRows, controls, hasCharges(state), hotbar(width, height), chat);
    }

    private static boolean hasCharges(Snapshot state) {
        return state != null && state.activeSlots().stream().anyMatch(slot -> slot.charges() >= 0);
    }

    public static int focusedChatLineLimit(int vanillaLines, int historyLines, int lineHeight) {
        var mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ChatScreen) || mc.gui.hud.isHidden() || vanillaLines <= 1
                || historyLines <= 1) return vanillaLines;
        var config = HudLayoutConfig.state(HudLayoutConfig.Element.COMBAT);
        if (!config.visible()) return vanillaLines;
        var state = CombatHudState.current();
        if (state == null) return vanillaLines;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int roleRows = CombatHudRoleStatus.rows(state).size();
        int controls = controlRows(state).size();
        return CombatHudLayout.chatLineLimit(width, height, config.x(), config.y(), config.scale(),
                roleRows, controls, hasCharges(state), hotbar(width, height), chatWidth(mc),
                lineHeight, mc.options.chatScale().get(), vanillaLines, historyLines);
    }

    private static Bounds hotbar(int width, int height) {
        var mc = Minecraft.getInstance();
        int left = 0;
        int right = 0;
        if (mc.getCameraEntity() instanceof Player player) {
            boolean leftMain = player.getMainArm() == HumanoidArm.LEFT;
            if (!player.getOffhandItem().isEmpty()) {
                if (leftMain) right = 29;
                else left = 29;
            }
            if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                if (leftMain) left = 22;
                else right = 24;
            }
        }
        return CombatHudLayout.hotbar(width, height, left, right);
    }

    private static Bounds focusedChat(int screenHeight) {
        var mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ChatScreen)) return null;
        var options = mc.options;
        var chat = mc.gui.hud.getChat();
        var lines = (ChatComponentAccessor)chat;
        int lineHeight = lines.myriad$chatLineHeight();
        int available = Math.max(0, lines.myriad$visibleChatLines().size() - lines.myriad$chatScroll());
        int count = Math.min(available, chat.getLinesPerPage());
        return CombatHudLayout.focusedChat(chatWidth(mc), screenHeight, count, lineHeight, options.chatScale().get());
    }

    private static int chatWidth(Minecraft mc) {
        return ChatComponent.getWidth(mc.options.chatWidth().get()) + 12;
    }

    private static void drawPanel(GuiGraphicsExtractor gui, Font font, Layout layout, Snapshot state,
                                  List<CombatHudRoleStatus.Row> roleRows, List<Component> controls, boolean skin) {
        int right = layout.width() - GAP;
        if (skin) CombatHudSkin.PANEL.frame(gui, layout.width(), layout.height());
        else {
            gui.fill(0, 0, layout.width(), layout.height(), 0xF0182429);
            border(gui, 0, 0, layout.width(), layout.height(), 0xFF65766E);
        }

        if (state.health() / state.maxHealth() < .25F) {
            if (skin) CombatHudSkin.LOW_HEALTH.frame(gui, layout.width(), layout.height());
            else border(gui, 0, 0, layout.width(), layout.height(), 0xFFCA6772);
        }

        int x = layout.contentX();
        var level = label("level", state.level());
        if (!layout.compact()) {
            gui.fill(GAP, GAP, 40, 40, 0xE3152026);
            if (!CharacterPortrait.draw(gui, state.character(), GAP + 2, GAP + 2, 30, 1)) {
                String name = Component.translatable("character.myriad." + state.character().getId()).getString();
                centered(gui, font, name.isEmpty() ? "?" : name.substring(0, 1), GAP, 19, 34, WHITE);
            }
            if (skin) CombatHudSkin.PORTRAIT.draw(gui, GAP, GAP, 34, 34);
            else border(gui, GAP, GAP, 34, 34, 0xFFA99B7C);
            centered(gui, font, level.getString(), GAP, 43, 34, WHITE);
        }

        drawHealth(gui, font, state, x, right, skin);
        int manaEnd = layout.compact() ? right - font.width(level) - 6 : right;
        track(gui, skin, CombatHudSkin.MANA, x, 21, manaEnd - x, 12,
                state.unlimitedMana() ? 1F : state.mana() / (float)state.maxMana(), MANA);
        var mana = state.unlimitedMana() ? label("mana_infinite")
                : label("mana", state.mana(), Math.round(state.maxMana()));
        gui.text(font, mana, x + 3, 23, WHITE);
        if (layout.compact()) {
            gui.fill(manaEnd + 2, 21, right, 33, 0xC51B3036);
            gui.text(font, level, right - font.width(level) - 2, 23, WHITE);
        }

        for (int i = 0; i < 4; i++) {
            drawSlot(gui, font, state.activeSlots().get(i), x + i * SLOT_STEP, layout.skillY(), false, skin);
        }
        drawSlot(gui, font, state.passive(), x + 4 * SLOT_STEP, layout.skillY(), true, skin);
        if (!layout.compact()) {
            int sideX = x + 5 * SLOT_STEP + 1;
            if (skin) CombatHudSkin.GOLD.draw(gui, sideX, layout.skillY() + 1, 8, 8);
            int goldX = skin ? sideX + 9 : sideX;
            String gold = label("gold", state.gold()).getString();
            if (font.width(gold) > right - goldX) gold = Integer.toString(state.gold());
            gui.text(font, gold, goldX, layout.skillY() + 1, AMBER);
            drawAttack(gui, font, state, sideX, layout.skillY() + 11, right - sideX, false, skin);
        }

        int roleY = layout.roleTop();
        for (var row : roleRows) {
            if (row.progress() >= 0) {
                gui.fill(GAP, roleY, right, roleY + 10, 0xB91D2E36);
                int extent = length(right - GAP, row.progress());
                gui.fill(GAP, roleY + 9, GAP + extent, roleY + 10, row.color());
            }
            gui.text(font, fit(font, row.text().getString(), right - GAP - 4), GAP + 2, roleY, row.color());
            roleY += 11;
        }
        int controlY = layout.controlTop();
        for (var control : controls) {
            gui.fill(GAP, controlY, right, controlY + 10, 0xD23A2839);
            if (skin) CombatHudSkin.CONTROL.draw(gui, GAP + 1, controlY, 9, 9);
            else gui.text(font, "!", GAP + 3, controlY, AMBER);
            gui.text(font, control, GAP + 13, controlY, AMBER);
            controlY += 11;
        }
        if (layout.attackRow()) drawAttack(gui, font, state, GAP, layout.attackTop(), right - GAP, true, skin);
        drawXp(gui, font, state, layout, skin);
    }

    private static void drawHealth(GuiGraphicsExtractor gui, Font font, Snapshot state, int x, int right,
                                   boolean skin) {
        int width = right - x;
        track(gui, skin, CombatHudSkin.HEALTH, x, GAP, width, 13, state.health() / state.maxHealth(), LIFE);
        long hp = (long)Math.ceil(state.health());
        long max = (long)Math.ceil(state.maxHealth());
        int shieldLeft = right;
        if (state.shield() > 0) {
            int interior = width - 2;
            int filled = length(interior, state.health() / state.maxHealth());
            int shieldWidth = Math.min(interior - filled,
                    Math.max(1, length(interior, state.shield() / state.maxHealth())));
            int shieldX = x + 1 + filled;
            if (shieldWidth > 0) {
                if (skin) CombatHudSkin.SHIELD.shield(gui, shieldX, GAP + 1, shieldWidth, 11);
                else {
                    gui.fill(shieldX, GAP + 1, shieldX + shieldWidth, GAP + 12, 0xFFC28E35);
                    for (int row = 0; row < 11; row++) {
                        for (int col = Math.floorMod(-row, 5); col < shieldWidth; col += 5) {
                            gui.fill(shieldX + col, GAP + 1 + row, shieldX + col + 1, GAP + 2 + row, 0xFFEAC775);
                        }
                    }
                }
            }
            var value = label("shield", (long)Math.ceil(state.shield()));
            shieldLeft = right - 3 - font.width(value);
            gui.text(font, value, shieldLeft, GAP + 3, AMBER);
        }
        var value = label("health", hp, max);
        if (x + 3 + font.width(value) + 4 > shieldLeft) value = Component.literal(hp + " / " + max);
        gui.text(font, value, x + 3, GAP + 3, WHITE);
    }

    private static void drawXp(GuiGraphicsExtractor gui, Font font, Snapshot state, Layout layout, boolean skin) {
        int x = GAP;
        int width = layout.width() - 2 * GAP;
        int y = layout.xpTop();
        if (state.maxLevel()) {
            var text = label("max_level");
            int textEnd = x + font.width(text) + 5;
            gui.text(font, text, x, y, 0xFFE3D1FA);
            width -= textEnd - x;
            x = textEnd;
        }
        track(gui, skin, CombatHudSkin.XP, x, y + (state.maxLevel() ? 3 : 0), width, 3,
                state.maxLevel() ? 1F : state.xp() / (float)Math.max(1, state.xpToNext()), XP);
    }

    private static void drawAttack(GuiGraphicsExtractor gui, Font font, Snapshot state, int x, int y,
                                   int width, boolean wide, boolean skin) {
        var value = state.attack();
        var title = label("attack");
        String reported = value.ready() ? label("ready").getString()
                : value.cooldownTicks() > 0 ? secondsWithUnit(value.cooldownTicks()).getString() : "—";
        gui.text(font, title, x + 2, y, WHITE);
        int valueY = wide ? y : y + 10;
        int color = value.ready() ? READY : value.cooldownTicks() > 0 ? WHITE : TEXT_DIM;
        gui.text(font, reported, wide ? x + width - font.width(reported) - 2 : x + 2, valueY, color);
        int trackWidth = wide ? width : width - 2;
        track(gui, skin, CombatHudSkin.ATTACK, x, valueY + 10, trackWidth, 4, value.progress(),
                value.ready() ? READY : value.cooldownTicks() > 0 ? ATTACK : 0xFF5A686D);
    }

    private static void drawSlot(GuiGraphicsExtractor gui, Font font, Slot slot, int x, int y,
                                 boolean passive, boolean skin) {
        gui.fill(x, y, x + SLOT, y + SLOT, 0xFF1B2A30);
        if (slot != null) SkillIcon.drawArtwork(gui, font, x + 2, y + 2, SLOT - 4, slot.skill());
        boolean ultimate = slot != null && slot.skill().isUltimate();
        if (skin) (ultimate ? CombatHudSkin.ULTIMATE : CombatHudSkin.SKILL).draw(gui, x, y, SLOT, SLOT);
        else border(gui, x, y, SLOT, SLOT, ultimate ? 0xFFD4B781 : 0xFF768780);

        if (slot == null || slot.status() == SkillStatus.LOCKED) {
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xA810171C);
            if (skin) CombatHudSkin.LOCK.draw(gui, x + 6, y + 6, 14, 14);
            else {
                border(gui, x + 10, y + 6, 6, 7, TEXT_DIM);
                gui.fill(x + 8, y + 11, x + 18, y + 20, TEXT_DIM);
                gui.fill(x + 12, y + 13, x + 14, y + 17, 0xFF19272E);
            }
        } else if (slot.status() == SkillStatus.COOLDOWN) {
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xA80A141C);
            centered(gui, font, seconds(slot.cooldownTicks()), x, y + 9, SLOT, AMBER);
        } else if (slot.status() == SkillStatus.NO_MANA) {
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xBE0A2334);
            String text = label("no_mana").getString();
            var lines = font.split(Component.literal(text), SLOT - 2);
            boolean singleLine = lines.size() == 1;
            if (singleLine && skin) CombatHudSkin.NO_MANA.draw(gui, x + 9, y + 3, 8, 8);
            int lineY = y + (singleLine ? skin ? 14 : 9 : 4);
            for (var line : lines) {
                if (lineY + 8 > y + SLOT) break;
                gui.text(font, line, x + (SLOT - font.width(line)) / 2, lineY, 0xFFB8EBFA);
                lineY += 9;
            }
        } else if (slot.status() == SkillStatus.UNAVAILABLE) {
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xA810171C);
            centered(gui, font, "—", x, y + 9, SLOT, TEXT_DIM);
        } else if (slot.status() == SkillStatus.READY) {
            int borderInset = ultimate ? 1 : 0;
            if (skin) CombatHudSkin.READY.draw(gui, x + borderInset, y + borderInset,
                    SLOT - 2 * borderInset, SLOT - 2 * borderInset);
            else border(gui, x + borderInset, y + borderInset,
                    SLOT - 2 * borderInset, SLOT - 2 * borderInset, READY);
        }

        if (slot != null && slot.recastTicks() > 0) {
            String time = seconds(slot.recastTicks());
            int labelWidth = font.width(time);
            int markSize = 8;
            int reX = x + (SLOT - labelWidth - markSize - 1) / 2;
            if (skin) CombatHudSkin.RECAST.draw(gui, reX, y - 10, markSize, markSize);
            else gui.text(font, label("recast"), reX, y - 10, AMBER);
            gui.text(font, time, reX + markSize + 1, y - 10, AMBER);
        } else if (slot != null && slot.rank() > 0) {
            centered(gui, font, Integer.toString(slot.rank()), x, y - 10, SLOT, TEXT_DIM);
        }

        if (passive) {
            // Passive is a read-only label, independent of every key binding.
            centered(gui, font, label("passive").getString(), x - SLOT_GAP / 2, y + SLOT + 2,
                    SLOT_STEP + 4, TEXT_DIM);
        } else {
            centered(gui, font, fit(font, slot.binding().getString(), SLOT_STEP - 1), x - SLOT_GAP / 2,
                    y + SLOT + 2, SLOT_STEP, WHITE);
        }
        if (slot != null && slot.charges() >= 0) drawCharges(gui, font, slot, x, y + SLOT + 12, skin);
    }

    private static void drawCharges(GuiGraphicsExtractor gui, Font font, Slot slot, int x, int y, boolean skin) {
        int center = x + SLOT / 2;
        float fraction = slot.chargeTotal() <= 0 ? 1F : 1F - slot.chargeRemaining() / (float)slot.chargeTotal();
        int filled = length(RING_X.length, fraction);
        for (int point = 0; point < RING_X.length; point++) {
            int dotX = center + RING_X[point];
            int dotY = y + 7 + RING_Y[point];
            gui.fill(dotX, dotY, dotX + 1, dotY + 1, point < filled ? 0xFFA2DCEF : 0xFF475C63);
        }
        if (skin) CombatHudSkin.CHARGE.draw(gui, center + 8, y + 3, 8, 8);
        centered(gui, font, Integer.toString(slot.charges()), x, y + 3, SLOT,
                slot.charges() > 0 ? WHITE : TEXT_DIM);
    }

    private static List<Component> controlRows(Snapshot state) {
        var rows = new ArrayList<Component>(2);
        if (state.control().stun() > 0) rows.add(label("stunned", secondsWithUnit(state.control().stun())));
        if (state.control().silence() > 0) rows.add(label("silenced", secondsWithUnit(state.control().silence())));
        return rows;
    }

    private static int length(int width, float fraction) {
        return Math.round(width * Math.clamp(fraction, 0F, 1F));
    }

    private static void track(GuiGraphicsExtractor gui, boolean skin, Slice track, int x, int y,
                              int w, int h, float fraction, int fill) {
        if (skin) track.track(gui, x, y, w, h);
        else gui.fill(x, y, x + w, y + h, 0xFF0E1920);
        int extent = length(w - 2, fraction);
        if (extent > 0) gui.fill(x + 1, y + 1, x + 1 + extent, y + h - 1, fill);
    }

    private static void border(GuiGraphicsExtractor gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y + 1, x + 1, y + h - 1, color);
        gui.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void centered(GuiGraphicsExtractor gui, Font font, String text, int x, int y,
                                 int width, int color) {
        gui.text(font, text, x + (width - font.width(text)) / 2, y, color);
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    static String seconds(int ticks) {
        return CombatHudSkillRules.cooldownText(ticks);
    }

    static Component secondsWithUnit(int ticks) {
        return label("duration_tenths", seconds(ticks));
    }

    static MutableComponent label(String key, Object... args) {
        return Component.translatable("hud.myriad.combat." + key, args);
    }
}
