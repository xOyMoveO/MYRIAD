package com.myriad.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.SkillIcon;
import com.myriad.network.NetworkSetup;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import com.myriad.registry.ModAttributes;

/**
 * 个人信息界面（C 键打开）：
 * 皮肤头像 / 角色 / 等级经验金币法力 / 装备 / 全部属性 / 技能冷却 / 对局数据。
 * 未开局也可打开；显示当前角色成长、装备、战斗属性、技能和宗门。
 * 打开时向服务端请求一次同步；若服务端未装模组（无同步数据）则显示提示而非空白。
 */
public class InfoScreen extends Screen {
    private static final int PANEL_COLOR = 0xC00F0F1A;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFF55;
    private static final int WARN_COLOR = 0xFFFF5555;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int MANA_COLOR = 0xFF55AAFF;
    private static final int LINE_HEIGHT = 12;
    private static final int COL2_X = 270;
    private static final int PORTRAIT_SIZE = 40;
    /** 界面适配基准尺寸（1280x720 @ guiScale 3）：宽高任一不足即整体收缩 */
    private static final float FIT_REF_WIDTH = 427.0f;
    private static final float FIT_REF_HEIGHT = 240.0f;

    /** 适配玩家界面尺寸的缩放因子（同时考虑宽与高，保证内容完整显示） */
    private static float fitScale(int width, int height) {
        return Math.min(1.0f, Math.min(width / FIT_REF_WIDTH, height / FIT_REF_HEIGHT));
    }

    /** 最近一帧鼠标屏幕坐标与缩放（供悬停检测换算） */
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private float lastScale = 1.0f;
    /** 内容纵向滚动（内容超出视口时可滚，保证全部内容可达） */
    private int scrollY = 0;
    /** 本帧内容底部（内容坐标），用于计算滚动上限 */
    private int contentBottom = 0;

    private float fitScaleValue() {
        return lastScale;
    }

    /** 最大可滚动距离（内容坐标） */
    private int maxScroll() {
        int viewH = (int) ((this.height - 48) / this.fitScaleValue());
        return Math.max(0, contentBottom - viewH);
    }

    private void scrollBy(int delta) {
        scrollY = Math.max(0, Math.min(maxScroll(), scrollY - delta * 16));
    }

    public InfoScreen() {
        super(Component.translatable("screen.myriad.info.title"));
        com.myriad.Myriad.LOGGER.info("[info-screen] opened (player={})",
                net.minecraft.client.Minecraft.getInstance().player != null);
        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    @Override protected void init() {
        if (com.myriad.character.rework.FirstGroupSkills.active(ClientDataHolder.characterId()))
            Minecraft.getInstance().setScreenAndShow(new FirstGroupScreen(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_C) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        PlayerDataPayload data = ClientDataHolder.get();
        int x = 30;
        int y = 26;
        XianxiaUi.drawPanel(gui, x - 10, y - 14, this.width - x - 20, this.height - y - 26);

        // 内容整体适配界面尺寸（同时考虑宽与高；超出部分可滚动查看）
        float s = fitScale(this.width, this.height);
        this.lastScale = s;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        int mouseSX = (int) (mouseX / s);
        int mouseSY = (int) (mouseY / s);
        int mouseSYScroll = mouseSY + scrollY;
        // 用上一帧内容高度夹取滚动量，保证不会滚出底部
        int maxScr = Math.max(0, contentBottom - (int) ((this.height - 48) / s));
        if (scrollY > maxScr) {
            scrollY = maxScr;
        }
        // 内容裁剪区（内容坐标，enableScissor 会按当前 pose 变换到屏幕坐标）
        int clipL = (int) ((x - 10) / s);
        int clipT = (int) (6 / s);
        int clipR = (int) ((this.width - 30) / s);
        int clipB = (int) ((this.height - 44) / s);

        gui.pose().pushMatrix();
        gui.pose().scale(s);
        gui.enableScissor(clipL, clipT, clipR, clipB);


        gui.pose().translate(0, -scrollY);

        // ---- 皮肤头像（左上角） ----
        if(!com.myriad.client.CharacterPortrait.draw(gui,CharacterType.byId(ClientDataHolder.characterId()),x,y-12,PORTRAIT_SIZE,1))
            PlayerFaceExtractor.extractRenderState(gui, player.getSkin(), x, y - 12, PORTRAIT_SIZE);

        // ---- 标题与角色（头像右侧） ----
        int tx = x + PORTRAIT_SIZE + 8;
        gui.text(font, Component.translatable("screen.myriad.info.title").withStyle(net.minecraft.ChatFormatting.BOLD),
                tx, y, TITLE_COLOR);
        y += LINE_HEIGHT;
        String characterName = characterName();
        gui.text(font, Component.literal("角色: " + characterName).withStyle(net.minecraft.ChatFormatting.BOLD),
                tx, y, characterName.isEmpty() ? TEXT_COLOR : SECTION_COLOR);
        y += LINE_HEIGHT + 4;

        // ---- 等级 / 经验 / 金币 / 法力 ----
        if (data == null) {
            gui.text(font, "等级: -  经验: -/-", x, y, TEXT_COLOR);
            y += LINE_HEIGHT;
            gui.text(font, Component.literal("金币: ").append(Component.literal("-").withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(GOLD_COLOR)))
                            .append(Component.literal("  法力: ")).append(Component.literal("-").withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(MANA_COLOR))),
                    x, y, TEXT_COLOR);
        } else {
            gui.text(font, "等级: " + data.level()
                    + "  经验: " + data.xp() + "/" + data.xpToNext(), x, y, TEXT_COLOR);
            y += LINE_HEIGHT;
            int maxMana = (int) attrValue(player, ModAttributes.MAX_MANA);
            gui.text(font, Component.literal("金币: ").append(Component.literal(String.valueOf(data.gold()))
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(GOLD_COLOR)))
                            .append(Component.literal("  法力: ")).append(Component.literal(
                            data.mana() + "/" + maxMana).withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(MANA_COLOR))),
                    x, y, TEXT_COLOR);
        }
        y += LINE_HEIGHT + 4;

        // ---- 装备（独立装备栏 6 槽） ----
        y = drawEquipment(gui, font, x, y);

        // ---- 属性 ----
        y = drawAttributes(gui, font, player, x, y);

        // ---- 技能（被动 + 主动，逐行可悬停查看介绍，点击技能行升级） ----
        if (!characterName.isEmpty()) {
            String pts = data == null ? "技能点: -"
                    : "技能点: " + data.skillPoints();
            String skillTitle = "── 技能 ──（" + pts + "）";
            gui.text(font, Component.literal(skillTitle).withStyle(net.minecraft.ChatFormatting.BOLD),
                    (int) (this.width / 2 / s) - font.width(skillTitle) / 2, y, SECTION_COLOR);
            y += LINE_HEIGHT;
            y = drawPassives(gui, font, x, y, mouseSX, mouseSYScroll, mouseX, mouseY);
            y = drawSkills(gui, font, x, y, mouseSX, mouseSYScroll, mouseX, mouseY);
        }

        // ---- 对局（仅对局期数据） ----
        if (hasMatch()) {
            gui.text(font, Component.literal("阵营: " + ClientDataHolder.factionName())
                            .withStyle(net.minecraft.ChatFormatting.BOLD),
                    x, y, SECTION_COLOR);
            y += LINE_HEIGHT;
            gui.text(font, Component.translatable("hud.myriad.destroy_crystal"), x, y, SECTION_COLOR);
            y += LINE_HEIGHT;
        }

        contentBottom = y;

        gui.pose().popMatrix();
        gui.disableScissor();

        // ---- 底部提示（固定不随滚动，不被裁剪） ----
        if (data == null) {
            gui.text(font, Component.literal("未收到服务器同步数据：请确认在装有本模组的服务器/单人世界中游玩")
                            .withStyle(net.minecraft.ChatFormatting.BOLD),
                    x, this.height - 40, WARN_COLOR);
        }
        gui.text(font, Component.translatable("screen.myriad.info.hint"), x, this.height - 22, 0xFF808080);
    }

    private boolean hasMatch() {
        return ClientDataHolder.phase() == com.myriad.game.GameManager.Phase.PLAYING
                || ClientDataHolder.phase() == com.myriad.game.GameManager.Phase.ENDED;
    }

    private String characterName() {
        CharacterType type = CharacterType.byId(ClientDataHolder.characterId());
        return type == null ? "" : Component.translatable(type.nameKey()).getString();
    }

    /** 独立装备栏（6 槽）：图标在上、名字在下，悬停查看属性，右键出售（70% 价格） */
    private int drawEquipment(GuiGraphicsExtractor gui, Font font, int x, int y) {
        int centerX = (int) (this.width / 2 / this.fitScaleValue());
        String title = "── 装备 ──（右键出售）";
        gui.text(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                centerX - font.width(title) / 2, y, SECTION_COLOR);
        y += LINE_HEIGHT;
        java.util.List<String> slots = com.myriad.client.ClientDataHolder.equipment();
        if (slots.isEmpty()) {
            gui.text(font, "（未同步）", x, y, TEXT_COLOR);
            return y + LINE_HEIGHT;
        }
        int slotSize = 32;
        int gap = 10;
        int mouseSX = (int) (this.lastMouseX / this.fitScaleValue());
        int mouseSY = (int) (this.lastMouseY / this.fitScaleValue()) + scrollY;
        equipRects.clear();
        // 整行居中（内容坐标系面板中心）
        int totalW = slots.size() * slotSize + (slots.size() - 1) * gap;
        int startX = centerX - totalW / 2;
        for (int i = 0; i < slots.size(); i++) {
            String equipId = slots.get(i);
            int sx = startX + i * (slotSize + gap);
            int sy = y;
            drawEquipIcon(gui, font, sx, sy, slotSize, equipId);
            String name = equipId.isEmpty() ? "空" : Component.translatable(
                    "equipment.myriad." + equipId).getString();
            gui.text(font, name, sx + (slotSize - font.width(name)) / 2, sy + slotSize + 2, TEXT_COLOR);
            equipRects.add(new int[] { sx, sy, slotSize, slotSize + 16, i });
            // 悬停查看属性
            if (!equipId.isEmpty()) {
                com.myriad.registry.EquipmentRegistry.EquipDef def =
                        com.myriad.registry.EquipmentRegistry.get(equipId);
                if (def != null) {
                    if (mouseSX >= sx && mouseSX <= sx + slotSize && mouseSY >= sy && mouseSY <= sy + slotSize + 16) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable(def.nameKey())
                                .withStyle(net.minecraft.ChatFormatting.BOLD));
                        tooltip.add(Component.literal("售价: " + Math.round(def.totalPrice() * 0.7) + " 金币（右键出售）")
                                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFD700)));
                        def.stats().forEach((attr, stat) -> {
                            String attrName = Component.translatable(attr.value().getDescriptionId()).getString();
                            boolean pct = stat.operation()
                                    == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                            tooltip.add(Component.literal(attrName + (pct
                                    ? " +" + Math.round(stat.amount() * 100) + "%"
                                    : " +" + Math.round(stat.amount()))));
                        });
                        gui.setComponentTooltipForNextFrame(font, tooltip, (int) this.lastMouseX, (int) this.lastMouseY);
                    }
                }
            }
        }
        return y + slotSize + 16 + LINE_HEIGHT;
    }

    /** 装备槽点击区域（x, y, w, h, slot），右键出售用 */
    private final List<int[]> equipRects = new ArrayList<>();

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (maxScroll() > 0) {
            scrollBy((int) Math.signum(deltaY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        float s = fitScaleValue();
        int mx = (int) (event.x() / s);
        int my = (int) (event.y() / s) + scrollY;
        if (event.buttonInfo().button() == 1 && !equipRects.isEmpty()) {
            for (int[] rect : equipRects) {
                if (mx >= rect[0] && mx <= rect[0] + rect[2] && my >= rect[1] && my <= rect[1] + rect[3]) {
                    ClientPacketDistributor.sendToServer(new NetworkSetup.SellEquipPayload(rect[4]));
                    return true;
                }
            }
        }
        if (event.buttonInfo().button() == 0 && !skillRects.isEmpty()) {
            CharacterType type = CharacterType.byId(ClientDataHolder.characterId());
            if (type != null) {
                for (int[] rect : skillRects) {
                    if (mx >= rect[0] && mx <= rect[0] + rect[2] && my >= rect[1] && my <= rect[1] + rect[3]) {
                        Skill skill = type.skill(rect[4]);
                        if (skill != null) {
                            ClientPacketDistributor.sendToServer(
                                    new NetworkSetup.UpgradeSkillPayload(skill.getId()));
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** 装备图标槽：贴图 textures/gui/sprites/eq_<id>.png 存在则渲染，否则占位框 + 首字 */
    private void drawEquipIcon(GuiGraphicsExtractor gui, Font font, int x, int y, int size, String equipId) {
        if (equipId.isEmpty()) {
            return;
        }
        boolean hasTex = com.myriad.client.SkillIcon.hasResource(
                "textures/gui/sprites/eq_" + equipId + ".png");
        if (hasTex) {
            gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("myriad", "eq_" + equipId),
                    x, y, size, size);
        } else {
            String ch = Component.translatable("equipment.myriad." + equipId).getString();
            if (!ch.isEmpty()) {
                ch = ch.substring(0, 1);
            }
            gui.text(font, ch, x + (size - font.width(ch)) / 2, y + (size - font.lineHeight) / 2, 0xFFFFFFFF);
        }
    }

    private static String itemName(ItemStack stack) {
        return stack.isEmpty() ? "—" : stack.getHoverName().getString();
    }

    private int drawAttributes(GuiGraphicsExtractor gui, Font font, LocalPlayer player, int x, int y) {
        // 标题居中于两列属性区域（内容坐标系）
        int areaCenter = (x + COL2_X + 160) / 2;
        String title = "── 属性 ──";
        gui.text(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                areaCenter - font.width(title) / 2, y, SECTION_COLOR);
        y += LINE_HEIGHT;
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] { attrName(player, Attributes.MAX_HEALTH),
                fmt(attrValue(player, Attributes.MAX_HEALTH)), 0xFFFF7F7F, "max_health" });
        rows.add(new Object[] { attrName(player, ModAttributes.PHYSICAL_DEFENSE),
                fmt(attrValue(player, ModAttributes.PHYSICAL_DEFENSE)), 0xFFBFBFBF, "physical_defense" });
        rows.add(new Object[] { attrName(player, ModAttributes.PHYSICAL_ATTACK),
                fmt(attrValue(player, ModAttributes.PHYSICAL_ATTACK)), 0xFFFFD700, "physical_attack" });
        rows.add(new Object[] { attrName(player, ModAttributes.ATTACK_SPEED),
                fmt(attrValue(player, ModAttributes.ATTACK_SPEED)), 0xFFFFAA55, "attack_speed" });
        rows.add(new Object[] { "移动速度",
                fmt(attrValue(player, Attributes.MOVEMENT_SPEED) / 0.1 * 100) + "%", 0xFF7FFF7F, "movement_speed" });
        rows.add(new Object[] { attrName(player, ModAttributes.SPELL_POWER),
                fmt(attrValue(player, ModAttributes.SPELL_POWER)), 0xFF7FB8FF, "spell_power" });
        rows.add(new Object[] { attrName(player, ModAttributes.MAGIC_RESISTANCE),
                fmt(attrValue(player, ModAttributes.MAGIC_RESISTANCE)), 0xFF9F8FFF, "magic_resistance" });
        rows.add(new Object[] { attrName(player, ModAttributes.CRIT_CHANCE),
                fmt(attrValue(player, ModAttributes.CRIT_CHANCE)) + "%", 0xFFFFAA55, "crit_chance" });
        rows.add(new Object[] { attrName(player, ModAttributes.CRIT_DAMAGE),
                fmt(attrValue(player, ModAttributes.CRIT_DAMAGE)) + "%", 0xFFFFAA55, "crit_damage" });
        rows.add(new Object[] { attrName(player, ModAttributes.PHYSICAL_LIFESTEAL),
                fmt(attrValue(player, ModAttributes.PHYSICAL_LIFESTEAL)) + "%", 0xFFFF6B6B, "physical_lifesteal" });
        rows.add(new Object[] { attrName(player, ModAttributes.SPELL_LIFESTEAL),
                fmt(attrValue(player, ModAttributes.SPELL_LIFESTEAL)) + "%", 0xFFFF6B6B, "spell_lifesteal" });
        rows.add(new Object[] { attrName(player, ModAttributes.TENACITY),
                fmt(attrValue(player, ModAttributes.TENACITY)) + "%", 0xFF9F8FFF, "tenacity" });
        rows.add(new Object[] { "物理穿透",
                fmt(attrValue(player, ModAttributes.FLAT_ARMOR_PEN))
                        + " (" + fmt(attrValue(player, ModAttributes.PERCENT_ARMOR_PEN)) + "%)", 0xFFFFD700, "armor_pen" });
        rows.add(new Object[] { "法术穿透",
                fmt(attrValue(player, ModAttributes.FLAT_MAGIC_PEN))
                        + " (" + fmt(attrValue(player, ModAttributes.PERCENT_MAGIC_PEN)) + "%)", 0xFF7FB8FF, "magic_pen" });
        rows.add(new Object[] { attrName(player, ModAttributes.MAX_MANA),
                fmt(attrValue(player, ModAttributes.MAX_MANA)), MANA_COLOR, "max_mana" });
        rows.add(new Object[] { attrName(player, ModAttributes.MANA_REGEN),
                fmt(attrValue(player, ModAttributes.MANA_REGEN)) + "/s", MANA_COLOR, "mana_regen" });
        rows.add(new Object[] { attrName(player, ModAttributes.COOLDOWN_REDUCTION),
                fmt(attrValue(player, ModAttributes.COOLDOWN_REDUCTION)) + "%", 0xFF7FFF7F, "cooldown_reduction" });
        rows.add(new Object[] { attrName(player, ModAttributes.HEALTH_REGEN),
                healthRegenText(attrValue(player, ModAttributes.HEALTH_REGEN)), 0xFFFF6B6B, "health_regen" });
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            int rowX = i % 2 == 0 ? x : COL2_X;
            int rowY = y + (i / 2) * LINE_HEIGHT;
            AttributeIcons.draw(gui, rowX, rowY - 3, (String) row[3]);
            gui.text(font, Component.literal(row[0] + ": ").append(Component.literal((String) row[1]).withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor((Integer) row[2]))),
                    rowX + 19, rowY, TEXT_COLOR);
        }
        return y + ((rows.size() + 1) / 2) * LINE_HEIGHT;
    }

    private static String healthRegenText(double healthPerSecond) {
        int amount = com.myriad.player.PlayerDataProvider.healthRegenPulseAmount(healthPerSecond);
        int ticks = com.myriad.player.PlayerDataProvider.healthRegenPulseTicks(healthPerSecond);
        if (amount <= 0 || ticks == Integer.MAX_VALUE) return "0/5s";
        return amount + "/" + Math.max(1, ticks / 20) + "s";
    }

    private static String attrName(LocalPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {
        return Component.translatable(holder.value().getDescriptionId()).getString();
    }

    /** 属性值：优先服务端快照（装备/成长权威），未同步回退本地玩家 */
    private static double attrValue(LocalPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {
        double snap = com.myriad.client.ClientDataHolder.attrValue(holder.value().getDescriptionId());
        return Double.isNaN(snap) ? player.getAttributeValue(holder) : snap;
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }

    /** 被动技能逐行显示（标为被动，悬停查看介绍） */
    private int drawPassives(GuiGraphicsExtractor gui, Font font, int x, int y,
            int mouseSX, int mouseSY, int mouseX, int mouseY) {
        CharacterType type = CharacterType.byId(ClientDataHolder.characterId());
        if (type == null) {
            return y;
        }
        for (Skill passive : type.passives()) {
            String name = Component.translatable(passive.nameKey()).getString();
            String resource = Component.translatable("screen.myriad.info.skill_passive_resource").getString();
            String label = Component.translatable("screen.myriad.info.skill_passive", name, resource).getString();
            int centerX = (int) (this.width / 2 / this.fitScaleValue());
            int px = centerX - (16 + font.width(label)) / 2;
            SkillIcon.draw(gui, font, px, y, 12, passive, false, true, "P");
            gui.text(font, Component.literal(label).withStyle(net.minecraft.ChatFormatting.BOLD)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55FFFF)),
                    px + 16, y, TEXT_COLOR);
            if (mouseSX >= px && mouseSX <= px + 16 + font.width(label) && mouseSY >= y && mouseSY <= y + LINE_HEIGHT) {
                java.util.List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(passive.nameKey()).withStyle(net.minecraft.ChatFormatting.BOLD));
                addSkillDescriptionAndDamage(tooltip, type, passive, 1);
                tooltip.add(Component.translatable("screen.myriad.info.skill_passive_resource")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(MANA_COLOR)));
                gui.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
            y += LINE_HEIGHT;
        }
        return y;
    }

    /** 技能行点击区域（x, y, w, h, slot），extractRenderState 时重建 */
    private final List<int[]> skillRects = new ArrayList<>();

    /** 技能卡片（横向居中排列）：图标槽 + 名称 + Lv/升级标记，点击卡片升级 */
    private int drawSkills(GuiGraphicsExtractor gui, Font font, int x, int y,
            int mouseSX, int mouseSY, int mouseX, int mouseY) {
        CharacterType type = CharacterType.byId(ClientDataHolder.characterId());
        if (type == null) {
            return y;
        }
        PlayerDataPayload data = ClientDataHolder.get();
        skillRects.clear();
        String[] keyLabels = { "①", "②", "③", "④", "⑤" };
        int iconSize = 18;
        int cardH = 31;
        int gap = 10;
        // 收集本角色全部主动技能（保留槽位）
        java.util.List<Skill> skills = new java.util.ArrayList<>();
        int[] slots = new int[CharacterType.SKILL_SLOTS];
        for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
            Skill s = type.skill(slot);
            if (s != null) {
                slots[skills.size()] = slot;
                skills.add(s);
            }
        }
        if (skills.isEmpty()) {
            return y;
        }
        // 先算每张卡片宽度，整行居中
        int[] cardWs = new int[skills.size()];
        int totalW = 0;
        for (int i = 0; i < skills.size(); i++) {
            int lv = data == null ? 0 : data.skillLevels().getOrDefault(skills.get(i).getId(), 0);
            String name = Component.translatable(skills.get(i).nameKey()).getString();
            String lvLabel = lv <= 0 ? "未学习" : "Lv." + lv + " [+1]";
            String resourceLabel = skillResourceLabel(type, skills.get(i), lv);
            int cardW = iconSize + 8 + Math.max(font.width(name), Math.max(font.width(lvLabel),
                    font.width(resourceLabel))) + 2;
            cardWs[i] = cardW;
            totalW += cardW + (i < skills.size() - 1 ? gap : 0);
        }
        int centerX = (int) (this.width / 2 / this.fitScaleValue());
        int startX = centerX - totalW / 2;
        // 与上方被动图标保持间距，向下偏移一点
        int cardY = y + 4;
        int cardX = startX;
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            int slot = slots[i];
            int lv = data == null ? 0 : data.skillLevels().getOrDefault(skill.getId(), 0);
            boolean ultimate = skill.isUltimate();
            String name = Component.translatable(skill.nameKey()).getString();
            String lvLabel = lv <= 0 ? "未学习" : "Lv." + lv + " [+1]";
            String resourceLabel = skillResourceLabel(type, skill, lv);
            int cardW = cardWs[i];
            gui.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x66000000);
            SkillIcon.draw(gui, font, cardX + 1, cardY + 1, iconSize, skill, ultimate, true, keyLabels[slot]);
            gui.text(font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD),
                    cardX + iconSize + 6, cardY,
                    ultimate ? GOLD_COLOR : TEXT_COLOR);
            gui.text(font, Component.literal(lvLabel)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(lv <= 0 ? 0xFF9E9E9E
                                            : (ultimate ? GOLD_COLOR : 0xFF55FFFF))),
                    cardX + iconSize + 6, cardY + 9, TEXT_COLOR);
            gui.text(font, Component.literal(resourceLabel)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(MANA_COLOR)),
                    cardX + iconSize + 6, cardY + 19, MANA_COLOR);
            skillRects.add(new int[] { cardX, cardY, cardW, cardH, slot });
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(skill.nameKey()).withStyle(net.minecraft.ChatFormatting.BOLD));
            addSkillDescriptionAndDamage(tooltip, type, skill, lv);
            tooltip.add(skillResourceTooltip(type, skill, lv));
            if (lv <= 0) {
                tooltip.add(Component.literal("未学习：加点后即可释放")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFE0E0E0)));
            }
            tooltip.add(Component.literal("点击该卡片升级，消耗技能点（大招需达到对应等级）")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF808080)));
            if (ultimate) {
                int[] gates = com.myriad.game.GameRules.ULTIMATE_GATES;
                int next = lv + 1;
                int need = next < gates.length ? gates[next] : Integer.MAX_VALUE;
                if (need < Integer.MAX_VALUE) {
                    tooltip.add(Component.literal("升到 Lv." + next + " 需角色等级达到 " + need)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFB74D)));
                }
            }
            if (mouseSX >= cardX && mouseSX <= cardX + cardW && mouseSY >= cardY && mouseSY <= cardY + cardH) {
                gui.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
            cardX += cardW + gap;
        }
        return cardY + cardH;
    }

    private void addSkillDescriptionAndDamage(List<Component> tooltip, CharacterType type, Skill skill, int level) {
        int valueLevel = Math.max(1, level);
        tooltip.add(Component.translatable("screen.myriad.info.skill_section_description")
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFD782)));
        for (String line : com.myriad.client.SkillTooltipDescriptions.describeLines(skill, valueLevel)) {
            tooltip.add(Component.literal(line)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFD5EED5)));
        }

        LocalPlayer player = Minecraft.getInstance().player;
        PlayerDataPayload data = ClientDataHolder.get();
        if (player == null || data == null) return;
        double ad = attrValue(player, ModAttributes.PHYSICAL_ATTACK);
        double sp = attrValue(player, ModAttributes.SPELL_POWER);
        double bonusAd = Math.max(0.0, ad - type.initialAd());
        List<String> damageLines = com.myriad.client.SkillTooltipDescriptions.currentDamageLines(
                skill, valueLevel, data.level(), ad, sp, bonusAd, data.maxHealth());
        if (!damageLines.isEmpty()) {
            tooltip.add(Component.translatable("screen.myriad.info.skill_section_values")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFD782)));
        }
        for (String line : damageLines) {
            tooltip.add(Component.literal(line)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFC766)));
        }
    }

    /** 当前技能等级的蓝耗、基础CD与按玩家冷却缩减计算后的实际CD。 */
    private String skillResourceLabel(CharacterType type, Skill skill, int level) {
        int valueLevel = Math.max(1, level);
        float mana = type == CharacterType.LINGYUAN ? 0.0F : skill.getManaCost();
        double effectiveSeconds = effectiveCooldownTicks(skill, valueLevel) / 20.0;
        String key = level <= 0 ? "screen.myriad.info.skill_resource_preview"
                : "screen.myriad.info.skill_resource";
        return Component.translatable(key, formatMana(mana), formatSeconds(effectiveSeconds)).getString();
    }

    private Component skillResourceTooltip(CharacterType type, Skill skill, int level) {
        int valueLevel = Math.max(1, level);
        float mana = type == CharacterType.LINGYUAN ? 0.0F : skill.getManaCost();
        double baseSeconds = skill.getCooldownTicks(valueLevel) / 20.0;
        double effectiveSeconds = effectiveCooldownTicks(skill, valueLevel) / 20.0;
        return Component.translatable("screen.myriad.info.skill_resource_detail",
                        valueLevel, formatMana(mana), formatSeconds(baseSeconds), formatSeconds(effectiveSeconds))
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(MANA_COLOR));
    }

    private int effectiveCooldownTicks(Skill skill, int level) {
        if (ClientDataHolder.isTraining() && ClientDataHolder.noCooldown()) return 0;
        double cdr = Math.clamp(ClientDataHolder.attrValue(
                ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId()), 0.0, 60.0);
        if (Double.isNaN(cdr)) cdr = 0.0;
        return Math.max(1, (int)(skill.getCooldownTicks(level) * (1.0 - cdr / 100.0)));
    }

    private static String formatMana(float mana) {
        return mana == Math.rint(mana) ? Integer.toString(Math.round(mana))
                : String.format(java.util.Locale.ROOT, "%.1f", mana);
    }

    private static String formatSeconds(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }
}
