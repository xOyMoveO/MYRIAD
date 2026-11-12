package com.myriad.client.gui;

import com.myriad.network.NetworkSetup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 训练假人双防设置：右键假人打开，提交后由服务端校验实体、距离和 0~1000 范围。 */
public final class JiarenSettingsScreen extends Screen {
    private final int entityId;
    private final float initialPhysical;
    private final float initialMagic;
    private EditBox physicalBox;
    private EditBox magicBox;

    public JiarenSettingsScreen(int entityId, float physicalDefense, float magicDefense) {
        super(Component.translatable("screen.myriad.jiaren_settings.title"));
        this.entityId = entityId;
        this.initialPhysical = physicalDefense;
        this.initialMagic = magicDefense;
    }

    @Override protected void init() {
        int cx = width / 2;
        int top = height / 2 - 62;
        physicalBox = numericBox(cx - 12, top + 22, initialPhysical);
        magicBox = numericBox(cx - 12, top + 48, initialMagic);
        addRenderableWidget(physicalBox);
        addRenderableWidget(magicBox);

        int[] presets = {0, 50, 100, 200};
        for (int i = 0; i < presets.length; i++) {
            int value = presets[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), button -> {
                        physicalBox.setValue(String.valueOf(value));
                        magicBox.setValue(String.valueOf(value));
                    }).bounds(cx - 100 + i * 51, top + 76, 47, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.myriad.save"), button -> save())
                .bounds(cx - 78, top + 104, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.myriad.cancel"), button -> onClose())
                .bounds(cx + 4, top + 104, 74, 20).build());
        setInitialFocus(physicalBox);
    }

    private EditBox numericBox(int x, int y, float value) {
        EditBox box = new EditBox(font, x, y, 112, 18, Component.empty());
        box.setMaxLength(7);
        box.setValue(format(value));
        return box;
    }

    private static String format(float value) {
        return value == Math.rint(value) ? Integer.toString(Math.round(value)) : String.format("%.2f", value);
    }

    private void save() {
        try {
            float physical = Float.parseFloat(physicalBox.getValue());
            float magic = Float.parseFloat(magicBox.getValue());
            ClientPacketDistributor.sendToServer(
                    new NetworkSetup.SetJiarenDefensesPayload(entityId, physical, magic));
            onClose();
        } catch (NumberFormatException ignored) {
            physicalBox.setValue("0");
            magicBox.setValue("0");
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int top = height / 2 - 62;
        gui.fill(cx - 118, top - 12, cx + 118, top + 132, 0xE0101824);
        gui.fill(cx - 118, top - 12, cx + 118, top - 10, 0xFF5ADDD4);
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        String title = getTitle().getString();
        gui.text(font, title, cx - font.width(title) / 2, top, 0xFF8EFFF4);
        gui.text(font, Component.translatable("attribute.myriad.physical_defense"), cx - 100, top + 27, 0xFFE4C28B);
        gui.text(font, Component.translatable("attribute.myriad.magic_resistance"), cx - 100, top + 53, 0xFF8CCBFF);
        gui.text(font, Component.translatable("screen.myriad.jiaren_settings.presets"), cx - 100, top + 67, 0xFF9AA8B8);
    }
}
