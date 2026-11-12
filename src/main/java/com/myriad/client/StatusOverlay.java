package com.myriad.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

import com.myriad.network.NetworkSetup.PlayerDataPayload;
import com.myriad.registry.ModAttributes;

/**
 * 替换 vanilla 血量条与饱食度条的文本状态层：
 * 状态条左边缘与原版热键栏对齐，紧贴其上沿按「经验 → 生命 → 法力」竖向排列。
 */
public final class StatusOverlay {
    /** 界面适配基准宽度（与 HudOverlay 一致），guiScale 越大文本整体收缩，保证完整显示 */
    private static final float FIT_REF_WIDTH = 427.0f;
    private static final int STATUS_BAR_WIDTH = 132;
    private static final int STATUS_BAR_HEIGHT = 8;

    private StatusOverlay() {
    }

    /** 适配玩家界面尺寸的缩放因子：guiScale 越大（屏幕逻辑宽越小）文本越小 */
    private static float fitScale() {
        float w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.min(1.0f, w / FIT_REF_WIDTH);
    }

    /** 紧凑的纯矩形状态条：深色底、单像素描边和与数值同步的填充。 */
    private static void drawStatusBar(GuiGraphicsExtractor gui, int x, int y, int filled,
            int borderColor, int fillColor) {
        gui.fill(x - 1, y - 1, x + STATUS_BAR_WIDTH + 1, y + STATUS_BAR_HEIGHT + 1, borderColor);
        gui.fill(x, y, x + STATUS_BAR_WIDTH, y + STATUS_BAR_HEIGHT, 0xD6090E16);
        if (filled > 0) {
            gui.fill(x + 1, y + 1, x + 1 + Math.min(STATUS_BAR_WIDTH - 2, filled),
                    y + STATUS_BAR_HEIGHT - 1, fillColor);
        }
    }

    /**
     * 护盾位于生命条的后层：生命从左侧填充，护盾从右侧填充，重合时仍以生命颜色为准。
     * 这样护盾不会挤占法力/经验栏，又能在受伤时清晰显示为血条后的黄色矩形延伸。
     */
    private static void drawHealthWithShield(GuiGraphicsExtractor gui, int x, int y,
            int healthFill, int shieldFill) {
        int border = shieldFill > 0 ? 0xFFF1B84B : 0xFF8F3038;
        gui.fill(x - 1, y - 1, x + STATUS_BAR_WIDTH + 1, y + STATUS_BAR_HEIGHT + 1, border);
        gui.fill(x, y, x + STATUS_BAR_WIDTH, y + STATUS_BAR_HEIGHT, 0xD6090E16);
        if (shieldFill > 0) {
            int start = x + 1 + Math.max(0, STATUS_BAR_WIDTH - 2 - shieldFill);
            gui.fill(start, y + 1, x + STATUS_BAR_WIDTH - 1, y + STATUS_BAR_HEIGHT - 1, 0xEAF5C542);
        }
        if (healthFill > 0) {
            gui.fill(x + 1, y + 1, x + 1 + Math.min(STATUS_BAR_WIDTH - 2, healthFill),
                    y + STATUS_BAR_HEIGHT - 1, 0xE0D93643);
        }
    }

    /** 血量文本层（替换 vanilla 心形条） */
    public static final GuiLayer HEALTH = (gui, delta) -> {
        if (!ClientDataHolder.modEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientDataHolder.inGame()) {
            return;
        }
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.STATUS, () -> {
        PlayerDataPayload healthData = ClientDataHolder.get();
        int max = (int)Math.ceil(healthData == null ? mc.player.getMaxHealth() : healthData.maxHealth());
        int hp = (int)Math.ceil(healthData == null ? mc.player.getHealth() : healthData.health());
        Font font = mc.font;
        float s = fitScale();
        gui.pose().pushMatrix();
        gui.pose().scale(s);
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = mc.getWindow().getGuiScaledHeight() - 41;
        String healthText = "生命 " + hp + "/" + max;
        int healthFill = Math.round((STATUS_BAR_WIDTH - 2) * Math.max(0.0f,
                Math.min(1.0f, hp / (float) Math.max(1, max))));
        // 模组技能护盾以服务端为权威；不能依赖原版实体吸收值恰好在本帧完成客户端同步。
        float shield = healthData == null ? 0.0f : healthData.shield();
        int shieldFill = Math.round((STATUS_BAR_WIDTH - 2) * Math.max(0.0f,
                Math.min(1.0f, shield / (float) Math.max(1, max))));
        drawHealthWithShield(gui, x, y, healthFill, shieldFill);
        if (shield > 0.5f) {
            String shieldText = "护盾 " + (int) Math.ceil(shield);
            // 数值置于护盾条下方的右侧，避开下一行法力条与法力文字。
            gui.text(font, Component.literal(shieldText).withStyle(net.minecraft.ChatFormatting.BOLD),
                    x + STATUS_BAR_WIDTH + 6, y + STATUS_BAR_HEIGHT + 2, 0xFFFFD95A);
        }
        gui.text(font, Component.literal(healthText).withStyle(net.minecraft.ChatFormatting.BOLD),
                x - 10 - font.width(healthText), y - 1, 0xFFFF5555);
        gui.pose().popMatrix();
        });
    };

    /** 法力文本层（替换 vanilla 饱食度鸡腿条） */
    public static final GuiLayer MANA = (gui, delta) -> {
        if (!ClientDataHolder.modEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientDataHolder.inGame()) {
            return;
        }
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.STATUS, () -> {
        PlayerDataPayload data = ClientDataHolder.get();
        if (data != null && "lingyuan".equals(data.characterId()) && data.skillLevels().getOrDefault("g2_lingyuan_pa",0)>0) {
            int swords = data.flyingSwords();
            int qi = data.swordQi();
            Font font = mc.font;
            float s = fitScale();
            gui.pose().pushMatrix();
            gui.pose().scale(s);
            int x = mc.getWindow().getGuiScaledWidth() / 2 - 91;
            int y = mc.getWindow().getGuiScaledHeight() - 46;
            int filled = Math.round((STATUS_BAR_WIDTH - 2) * qi / 100.0f);
            drawStatusBar(gui, x, y, filled, 0xFF28789A, 0xE063CFFF);
            String qiText = "飞剑 " + swords + "  剑罡 " + qi + "/100";
            gui.text(font, qiText, x - 10 - font.width(qiText), y - 1, 0xFFFFFFFF);
            gui.pose().popMatrix();
        }
        int max = (int) com.myriad.client.ClientDataHolder.attrValue(
                com.myriad.registry.ModAttributes.MAX_MANA.value().getDescriptionId());
        if (Double.isNaN(com.myriad.client.ClientDataHolder.attrValue(
                com.myriad.registry.ModAttributes.MAX_MANA.value().getDescriptionId()))) {
            max = (int) mc.player.getAttributeValue(ModAttributes.MAX_MANA);
        }
        String mana = (ClientDataHolder.isTraining() && ClientDataHolder.noMana())
                ? "∞" : String.valueOf(data == null ? 0 : data.mana());
        Font font = mc.font;
        String text = "法力 " + mana + "/" + max;
        float s = fitScale();
        gui.pose().pushMatrix();
        gui.pose().scale(s);
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = mc.getWindow().getGuiScaledHeight() - 32;
        int manaValue = data == null ? 0 : data.mana();
        int manaFill = Math.round((STATUS_BAR_WIDTH - 2) * Math.max(0.0f,
                Math.min(1.0f, manaValue / (float) Math.max(1, max))));
        drawStatusBar(gui, x, y, manaFill, 0xFF28789A, 0xE055B6FF);
        gui.text(font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD),
                x - 10 - font.width(text), y - 1, 0xFF55AAFF);
        gui.pose().popMatrix();
        });
    };

    /** 底部经验区：游戏等级+经验文本层（替换 vanilla 经验等级数字与经验条，合并为一行） */
    public static final GuiLayer LEVEL = (gui, delta) -> {
        if (!ClientDataHolder.modEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientDataHolder.inGame()) {
            return;
        }
        PlayerDataPayload data = ClientDataHolder.get();
        if (data == null) {
            return;
        }
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.STATUS, () -> {
        Font font = mc.font;
        String levelText = "等级 " + data.level();
        String xpText = "经验 " + data.xp() + "/" + data.xpToNext();
        float s = fitScale();
        gui.pose().pushMatrix();
        gui.pose().scale(s);
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = mc.getWindow().getGuiScaledHeight() - 50;
        int xpFill = Math.round((STATUS_BAR_WIDTH - 2) * Math.max(0.0f, Math.min(1.0f,
                data.xp() / (float) Math.max(1, data.xpToNext()))));
        drawStatusBar(gui, x, y, xpFill, 0xFF7660A0, 0xE065D96C);
        gui.text(font, Component.literal(levelText)
                        .withStyle(net.minecraft.ChatFormatting.BOLD).withStyle(
                                net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFFF55)),
                x, y - 11, 0xFFFFFF55);
        gui.text(font, Component.literal(xpText)
                        .withStyle(net.minecraft.ChatFormatting.BOLD).withStyle(
                                net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55FF55)),
                x - 10 - font.width(xpText), y - 1, 0xFF55FF55);
        gui.pose().popMatrix();
        });
    };

    /** 空层：占位替换 vanilla 经验条背景/进度，避免残留渲染 */
    public static final GuiLayer NOOP = (gui, delta) -> {
    };
}
