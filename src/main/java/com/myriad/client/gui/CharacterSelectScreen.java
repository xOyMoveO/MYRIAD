package com.myriad.client.gui;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.client.ClientDataHolder;
import com.myriad.network.NetworkSetup;

/**
 * 职业选择界面（/myriad choose 打开）：
 * 左侧为可选职业列表（被其他玩家选中的置灰），右侧为所选职业的介绍；
 * 每个职业仅一人可选（服务端校验）。点击「确认选择」生效。
 */
public class CharacterSelectScreen extends Screen {
    private static final int LIST_W = 170;
    private static final int ROW_H = 26;
    private static final int CONFIRM_W = 120;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFF55;
    private static final int TAKEN_COLOR = 0xFF808080;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private String selectedId = null;
    private Button confirmButton;
    private int scrollRow = 0;
    private int visibleRows = 1;
    private int listX;
    private int listTop;
    private int listBottom;

    public CharacterSelectScreen() {
        super(Component.translatable("screen.myriad.choose.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        CharacterType[] types = CharacterType.playableValues();
        String current = ClientDataHolder.characterId();
        boolean firstOpen = selectedId == null;
        if (selectedId == null) {
            selectedId = current.isEmpty() ? types[0].getId() : current;
        }
        java.util.Map<String, Boolean> taken = ClientDataHolder.takenCharacters();

        listX = this.width / 2 - LIST_W - 60;
        if (listX < 10) {
            listX = 10;
        }
        listTop = 34;
        listBottom = Math.max(listTop + ROW_H, this.height - 18);
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, types.length - visibleRows);
        if (firstOpen) {
            int selectedIndex = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i].getId().equals(selectedId)) {
                    selectedIndex = i;
                    break;
                }
            }
            scrollRow = Math.max(0, Math.min(maxScroll, selectedIndex - visibleRows / 2));
        } else {
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow));
        }
        int shown = Math.min(visibleRows, types.length - scrollRow);
        for (int row = 0; row < shown; row++) {
            CharacterType type = types[scrollRow + row];
            boolean isTaken = taken.getOrDefault(type.getId(), false) && !type.getId().equals(current);
            boolean selected = type.getId().equals(selectedId);
            String name = Component.translatable(type.nameKey()).getString()
                    + "（" + Component.translatable("faction.myriad." + type.getFaction().getName()).getString() + "）";
            if (isTaken) {
                name += " ⚑";
            } else if (selected) {
                name += " ✓";
            }
            Component label = Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(isTaken ? 0xFF808080 : 0xFFFFFFFF));
            addRenderableWidget(Button.builder(label, btn -> {
                        if (isTaken) {
                            return;
                        }
                        selectedId = type.getId();
                        rebuild();
                    })
                    .bounds(listX, listTop + row * ROW_H, LIST_W, 22)
                    .build());
        }

        if (maxScroll > 0) {
            Button up = addRenderableWidget(Button.builder(Component.literal("▲"), btn -> scrollBy(-1))
                    .bounds(listX + LIST_W + 3, listTop, 18, 18)
                    .build());
            up.active = scrollRow > 0;
            Button down = addRenderableWidget(Button.builder(Component.literal("▼"), btn -> scrollBy(1))
                    .bounds(listX + LIST_W + 3, listTop + visibleRows * ROW_H - 18, 18, 18)
                    .build());
            down.active = scrollRow < maxScroll;
        }

        // 确认选择按钮（右侧底部）
        addRenderableWidget(Button.builder(Component.translatable("hero.guide.open"), b -> {
            var chosen = CharacterType.byId(selectedId);
            if(chosen!=null) net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new CharacterGuideScreen(this, chosen));
        }).bounds(width-116,8,108,20).build());
        int rightX = listX + LIST_W + 24;
        confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.myriad.choose.confirm"), btn -> {
                            if (selectedId == null || taken.getOrDefault(selectedId, false)) {
                                return;
                            }
                            ClientPacketDistributor.sendToServer(
                                    new NetworkSetup.ChooseCharacterPayload(selectedId));
                            this.onClose();
                        })
                .bounds(rightX, this.height - 44, CONFIRM_W, 22)
                .build());
        // 所选角色已被他人占用时禁用确认
        confirmButton.active = !(taken.getOrDefault(selectedId, false) && !selectedId.equals(current));
    }

    /** 重建界面（切换选中角色后刷新按钮状态） */
    private void rebuild() {
        clearWidgets();
        init();
    }

    private void scrollBy(int rows) {
        int maxScroll = Math.max(0, CharacterType.playableValues().length - visibleRows);
        int next = Math.max(0, Math.min(maxScroll, scrollRow + rows));
        if (next != scrollRow) {
            scrollRow = next;
            rebuild();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= listX && mouseX <= listX + LIST_W + 22
                && mouseY >= listTop && mouseY <= listBottom
                && Math.abs(deltaY) > 0.0) {
            scrollBy(deltaY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gui,
            int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        int total = CharacterType.playableValues().length;
        int maxScroll = Math.max(0, total - visibleRows);
        if (maxScroll > 0) {
            int trackX = listX + LIST_W + 1;
            int trackTop = listTop + 20;
            int trackBottom = listTop + visibleRows * ROW_H - 20;
            int trackH = Math.max(8, trackBottom - trackTop);
            int thumbH = Math.max(8, trackH * visibleRows / total);
            int thumbY = trackTop + (trackH - thumbH) * scrollRow / maxScroll;
            gui.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0x663C4654);
            gui.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF55FFFF);
        }
        // 右侧介绍区（适配界面尺寸）
        float s = Math.min(1.0f, this.width / 427.0f);
        gui.pose().pushMatrix();
        gui.pose().scale(s);
        CharacterType type = CharacterType.byId(selectedId);
        if (type == null) {
            gui.pose().popMatrix();
            return;
        }
        net.minecraft.client.gui.Font font = Minecraft_font();
        int rightX = (int) ((this.width / 2 + 10) / s);
        int y = (int) (34 / s);
        gui.text(font, Component.translatable(type.nameKey()).withStyle(net.minecraft.ChatFormatting.BOLD),
                rightX, y, TITLE_COLOR);
        y += 14;
        gui.text(font, Component.literal("阵营: ").append(Component.translatable(
                        "faction.myriad." + type.getFaction().getName()))
                        .withStyle(net.minecraft.ChatFormatting.BOLD),
                rightX, y, HIGHLIGHT_COLOR);
        y += 14;
        int descWidth = Math.max(80, (int) ((this.width - rightX - 12) * s) - 12);
        if (com.myriad.character.rework.FirstGroupSkills.active(type.getId())) {
            String[] labels={"被动A","被动B","常规①","常规②","主要A","主要B","终极A","终极B"};
            for(var skill:com.myriad.character.rework.FirstGroupSkills.candidates(type.getId())) {
                gui.text(font,labels[skill.index()]+"  "+skill.displayName()+" / "+skill.maxRank(),rightX,y,TEXT_COLOR);
                if(mouseX/s>=rightX&&mouseX/s<=rightX+descWidth&&mouseY/s>=y&&mouseY/s<y+15){
                    var lines=new java.util.ArrayList<Component>();
                    for(String line:com.myriad.client.SkillTooltipDescriptions.describeLines(skill,1))lines.add(Component.literal(line));
                    gui.setComponentTooltipForNextFrame(font,lines,(int)(mouseX/s),(int)(mouseY/s));
                }
                y+=16;
            }
            gui.text(font,"被动、主要、终极各二选一",rightX,y,SECTION_COLOR);
            gui.pose().popMatrix();
            return;
        }
        // 被动技能
        for (Skill passive : type.passives()) {
            gui.text(font, Component.literal("── 被动 ── ").withStyle(net.minecraft.ChatFormatting.BOLD)
                            .append(Component.translatable(passive.nameKey())
                                    .withStyle(net.minecraft.ChatFormatting.BOLD)),
                    rightX, y, SECTION_COLOR);
            y += 12;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.literal(com.myriad.client.SkillTooltipDescriptions.describe(passive, 1)), descWidth);
            int shown = Math.min(lines.size(), 3);
            for (int i = 0; i < shown; i++) {
                gui.text(font, lines.get(i), rightX + 10, y, 0xFFAAAAAA);
                y += 10;
            }
            y += 4;
        }
        gui.text(font, Component.literal("── 技能 ──").withStyle(net.minecraft.ChatFormatting.BOLD),
                rightX, y, SECTION_COLOR);
        y += 12;
        String[] keyLabels = { "Q", "W", "E", "R", "F" };
        for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
            Skill skill = type.skill(slot);
            if (skill == null) {
                continue;
            }
            gui.text(font, Component.literal(keyLabels[slot] + " ")
                            .append(Component.translatable(skill.nameKey())
                                    .withStyle(net.minecraft.ChatFormatting.BOLD)),
                    rightX, y, TEXT_COLOR);
            y += 10;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.literal(com.myriad.client.SkillTooltipDescriptions.describe(skill, 1)), descWidth);
            int shown = Math.min(lines.size(), 2);
            for (int i = 0; i < shown; i++) {
                gui.text(font, lines.get(i), rightX + 10, y, 0xFFAAAAAA);
                y += 10;
            }
            y += 4;
        }
        gui.pose().popMatrix();
    }

    private static net.minecraft.client.gui.Font Minecraft_font() {
        return net.minecraft.client.Minecraft.getInstance().font;
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
