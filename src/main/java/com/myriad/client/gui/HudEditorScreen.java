package com.myriad.client.gui;

import com.myriad.client.HudLayoutConfig;
import com.myriad.client.MinimapConfig;
import com.myriad.client.MinimapOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 可视化 HUD 编辑器：左键拖动，滚轮缩放，右键切换显隐。 */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudLayoutConfig.Element selected = HudLayoutConfig.Element.STATUS;
    private HudLayoutConfig.Element dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("screen.myriad.hud_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height - 27;
        int buttonW = Math.min(96, Math.max(58, (width - 30) / 4));
        int totalW = buttonW * 4 + 18;
        int startX = (width - totalW) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.hud_editor.reset_one"), button ->
                        HudLayoutConfig.reset(selected))
                .bounds(startX, y, buttonW, 20).build());
        addRenderableWidget(Button.builder(visibilityLabel(), button -> {
                    var state = HudLayoutConfig.state(selected);
                    HudLayoutConfig.set(selected, state.x(), state.y(), state.scale(), !state.visible());
                    rebuild();
                }).bounds(startX + buttonW + 6, y, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.myriad.hud_editor.reset_all"), button ->
                        HudLayoutConfig.resetAll())
                .bounds(startX + (buttonW + 6) * 2, y, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(startX + (buttonW + 6) * 3, y, buttonW, 20).build());
    }

    private Component visibilityLabel() {
        return Component.translatable(HudLayoutConfig.state(selected).visible()
                ? "screen.myriad.hud_editor.visible_short"
                : "screen.myriad.hud_editor.hidden_short");
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        HudLayoutConfig.Element hit = elementAt(event.x(), event.y());
        if (hit == null) return false;
        selected = hit;
        if (event.buttonInfo().button() == 1) {
            var state = HudLayoutConfig.state(hit);
            HudLayoutConfig.set(hit, state.x(), state.y(), state.scale(), !state.visible());
            rebuild();
            return true;
        }
        if (event.buttonInfo().button() == 0) {
            var state = HudLayoutConfig.state(hit);
            rebuild();
            dragging = hit;
            dragOffsetX = event.x() - state.x() * width;
            dragOffsetY = event.y() - state.y() * height;
            setDragging(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging == null || event.buttonInfo().button() != 0) {
            return super.mouseDragged(event, deltaX, deltaY);
        }
        var state = HudLayoutConfig.state(dragging);
        float x = (float)((event.x() - dragOffsetX) / Math.max(1, width));
        float y = (float)((event.y() - dragOffsetY) / Math.max(1, height));
        HudLayoutConfig.setTransient(dragging, x, y, state.scale(), state.visible());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.buttonInfo().button() == 0) {
            dragging = null;
            setDragging(false);
            HudLayoutConfig.saveNow();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        HudLayoutConfig.Element hit = elementAt(mouseX, mouseY);
        if (hit != null) selected = hit;
        if (deltaY == 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        var state = HudLayoutConfig.state(selected);
        HudLayoutConfig.set(selected, state.x(), state.y(), state.scale() + (deltaY > 0 ? .05F : -.05F), state.visible());
        rebuild();
        return true;
    }

    private HudLayoutConfig.Element elementAt(double mouseX, double mouseY) {
        HudLayoutConfig.Element[] elements = HudLayoutConfig.Element.values();
        for (int i = elements.length - 1; i >= 0; i--) {
            PreviewRect rect = rect(elements[i]);
            if (mouseX >= rect.x && mouseX <= rect.x + rect.w
                    && mouseY >= rect.y && mouseY <= rect.y + rect.h) return elements[i];
        }
        return null;
    }

    private PreviewRect rect(HudLayoutConfig.Element element) {
        var state = HudLayoutConfig.state(element);
        BaseRect base = defaultRect(element);
        float defaultAnchorX = element.defaultX() * width;
        float defaultAnchorY = element.defaultY() * height;
        float targetAnchorX = state.x() * width;
        float targetAnchorY = state.y() * height;
        // 与 HudLayoutConfig.render 完全一致：先平移到目标锚点，再以该锚点缩放原始 HUD。
        int x = Math.round(targetAnchorX + (base.x - defaultAnchorX) * state.scale());
        int y = Math.round(targetAnchorY + (base.y - defaultAnchorY) * state.scale());
        int w = Math.max(1, Math.round(base.w * state.scale()));
        int h = Math.max(1, Math.round(base.h * state.scale()));
        return new PreviewRect(x, y, w, h);
    }

    /** 每个 HUD 在未经过 HudLayoutConfig 变换前的实际默认绘制边界。 */
    private BaseRect defaultRect(HudLayoutConfig.Element element) {
        float fit = Math.min(1.0F, width / 427.0F);
        float centerX = width * .5F;
        float centerY = height * .5F;
        return switch (element) {
            case INFO -> new BaseRect(6 * fit, 6 * fit, 148 * fit, 45 * fit);
            case STATUS -> new BaseRect((centerX - 205) * fit, (height - 62) * fit,
                    260 * fit, 44 * fit);
            // 技能栏实际固定在屏幕左侧垂直居中，而不是底部中央。
            case SKILLS -> new BaseRect(6, centerY - 14 - 35 * fit, 234 * fit, 95 * fit);
            // 普攻区包含角色专属层数、能量和节奏提示，按最大常用占用区域显示。
            case ATTACKS -> new BaseRect(centerX + 85, height - 73, 145, 63);
            case MINIMAP -> new BaseRect(
                    MinimapConfig.x(width, MinimapOverlay.SIZE),
                    MinimapConfig.y(height, MinimapOverlay.SIZE),
                    MinimapOverlay.SIZE, MinimapOverlay.SIZE);
            case BUFFS -> new BaseRect(width - 150, 6, 144, 60);
            case TARGET -> new BaseRect(centerX - 90, 9, 180, 35);
            case OBJECTIVE -> new BaseRect(centerX - 91, 9, 182, 43);
            case ANNOUNCEMENTS -> new BaseRect(centerX - 112, height * .12F - 18, 224, 38);
            case PROMPTS -> new BaseRect(centerX - 87, centerY - 33, 174, 97);
            case HERO_RESOURCE -> new BaseRect(width - 40, height - 52, 32, 43);
            case DPS -> new BaseRect(4, height - 21, 90, 14);
            case COMBAT_STATS -> new BaseRect(width - 162, 88, 154, 76);
            case FEEDBACK -> new BaseRect(centerX - 14, centerY - 14, 28, 28);
            // 世界飘字和头顶血条会跟随投影目标；这里显示其默认参考区域。
            case FLOATERS -> new BaseRect(centerX - 54, height * .75F - 22, 108, 44);
            case WORLD_BARS -> new BaseRect(centerX - 38, centerY - 17, 76, 34);
            case TAB -> {
                float tabHeight = 116;
                float scale = Math.min(1.0F, Math.min((width - 16) / 480.0F,
                        (height - 24) / tabHeight));
                yield new BaseRect((width - 480 * scale) * .5F, 12 * scale,
                        480 * scale, tabHeight * scale);
            }
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xB0080D14);
        for (int x = 0; x < width; x += 20) gui.fill(x, 27, x + 1, height - 33, 0x182A5868);
        for (int y = 27; y < height - 33; y += 20) gui.fill(0, y, width, y + 1, 0x182A5868);

        Font font = Minecraft.getInstance().font;
        HudLayoutConfig.Element hovered = elementAt(mouseX, mouseY);
        for (HudLayoutConfig.Element element : HudLayoutConfig.Element.values()) {
            drawPreview(gui, font, element, element == selected, element == hovered);
        }

        Component title = Component.translatable("screen.myriad.hud_editor.title");
        gui.text(font, title, 8, 7, 0xFFFFE5A1);
        var state = HudLayoutConfig.state(selected);
        Component values = Component.translatable("screen.myriad.hud_editor.values",
                Math.round(state.x() * 100), Math.round(state.y() * 100), Math.round(state.scale() * 100));
        gui.text(font, values, width - font.width(values) - 8, 7, 0xFFD4E7EE);
        Component hint = Component.translatable("screen.myriad.hud_editor.drag_hint");
        gui.text(font, hint, (width - font.width(hint)) / 2, 18, 0xFF93D8E8);
        // 控件最后绘制，避免位于屏幕底部的真实 HUD 边界盖住按钮。
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphicsExtractor gui, Font font, HudLayoutConfig.Element element,
            boolean selectedNow, boolean hovered) {
        PreviewRect rect = rect(element);
        var state = HudLayoutConfig.state(element);
        int fill = state.visible() ? (selectedNow ? 0xAA17384A : hovered ? 0x99304B57 : 0x78202D36) : 0x783B2026;
        int border = selectedNow ? 0xFFFFD66B : hovered ? 0xFF63E5FF : state.visible() ? 0xFF668C9D : 0xFFB95A65;
        gui.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, fill);
        gui.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, border);
        gui.fill(rect.x, rect.y + rect.h - 1, rect.x + rect.w, rect.y + rect.h, border);
        gui.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.h, border);
        gui.fill(rect.x + rect.w - 1, rect.y, rect.x + rect.w, rect.y + rect.h, border);
        if (rect.w >= 12 && rect.h >= 12) {
            gui.fill(rect.x + 5, rect.y + rect.h - 7, rect.x + rect.w - 5, rect.y + rect.h - 4,
                    state.visible() ? 0xCC4FC8DD : 0x886B3A40);
        }
        String label = Component.translatable("screen.myriad.hud_editor.element." + element.id()).getString();
        while (font.width(label) > rect.w - 8 && label.length() > 2) {
            label = label.substring(0, label.length() - 2) + "…";
        }
        if (rect.w >= 18 && rect.h >= 14) {
            gui.text(font, label, rect.x + 4, rect.y + 4, state.visible() ? 0xFFFFFFFF : 0xFFFF9AA4);
        }
        String scale = Math.round(state.scale() * 100) + "%";
        if (rect.w >= font.width(scale) + 8 && rect.h >= 30) {
            gui.text(font, scale, rect.x + rect.w - font.width(scale) - 4,
                    rect.y + rect.h - 18, 0xFFC6DCE5);
        }
    }

    @Override
    public void onClose() {
        if (dragging != null) HudLayoutConfig.saveNow();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private record PreviewRect(int x, int y, int w, int h) { }
    private record BaseRect(float x, float y, float w, float h) { }
}
