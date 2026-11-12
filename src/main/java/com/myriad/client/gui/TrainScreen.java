package com.myriad.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.client.ClientDataHolder;
import com.myriad.network.NetworkSetup;

/**
 * 训练模式管理界面（/myriad train 打开）：
 * 提升等级 / 获得金币 / 训练宗门 / 无冷却 / 无限金币 / 无限蓝开关。
 */
public class TrainScreen extends Screen {
    private static final int SECTION_COLOR = 0xFF55FFFF;

    public TrainScreen() {
        super(Component.translatable("screen.myriad.train.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int w = 200;
        int x = centerX - w / 2;
        int y = 44;
        int rowH = 26;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.myriad.train.level"), btn ->
                                ClientPacketDistributor.sendToServer(
                                        new NetworkSetup.TrainActionPayload("level", false)))
                .bounds(x, y, w, 20).build());
        y += rowH;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.myriad.train.gold"), btn ->
                                ClientPacketDistributor.sendToServer(
                                        new NetworkSetup.TrainActionPayload("gold", true)))
                .bounds(x, y, w, 20).build());
        y += rowH;
        int factionGap = 4;
        int factionWidth = (w - factionGap) / 2;
        xuanxiaoBtn = addRenderableWidget(Button.builder(
                        factionLabel("screen.myriad.train.faction_xuanxiao",
                                ClientDataHolder.faction() == com.myriad.game.Faction.XUANXIAO), btn ->
                                ClientPacketDistributor.sendToServer(
                                        new NetworkSetup.TrainActionPayload("faction_xuanxiao", true)))
                .bounds(x, y, factionWidth, 20).build());
        youmingBtn = addRenderableWidget(Button.builder(
                        factionLabel("screen.myriad.train.faction_youming",
                                ClientDataHolder.faction() == com.myriad.game.Faction.YOUMING), btn ->
                                ClientPacketDistributor.sendToServer(
                                        new NetworkSetup.TrainActionPayload("faction_youming", true)))
                .bounds(x + factionWidth + factionGap, y, factionWidth, 20).build());
        y += rowH + 8;
        // 开关：无冷却 / 无限金币 / 无限蓝（每 tick 实时刷新状态）
        Button cdBtn = addRenderableWidget(Button.builder(
                        toggleLabel("screen.myriad.train.nocooldown", ClientDataHolder.noCooldown()), btn ->
                                ClientPacketDistributor.sendToServer(new NetworkSetup.TrainActionPayload(
                                        "nocooldown", !ClientDataHolder.noCooldown())))
                .bounds(x, y, w, 20).build());
        y += rowH;
        Button goldBtn = addRenderableWidget(Button.builder(
                        toggleLabel("screen.myriad.train.nogold", ClientDataHolder.noGoldCost()), btn ->
                                ClientPacketDistributor.sendToServer(new NetworkSetup.TrainActionPayload(
                                        "nogold", !ClientDataHolder.noGoldCost())))
                .bounds(x, y, w, 20).build());
        y += rowH;
        Button manaBtn = addRenderableWidget(Button.builder(
                        toggleLabel("screen.myriad.train.nomana", ClientDataHolder.noMana()), btn ->
                                ClientPacketDistributor.sendToServer(new NetworkSetup.TrainActionPayload(
                                        "nomana", !ClientDataHolder.noMana())))
                .bounds(x, y, w, 20).build());
        this.cdBtn = cdBtn;
        this.goldBtn = goldBtn;
        this.manaBtn = manaBtn;
    }

    private Button cdBtn;
    private Button goldBtn;
    private Button manaBtn;
    private Button xuanxiaoBtn;
    private Button youmingBtn;

    /** 每 tick 刷新开关按钮状态（服务端同步后实时更新） */
    @Override
    public void tick() {
        if (cdBtn != null) {
            cdBtn.setMessage(toggleLabel("screen.myriad.train.nocooldown", ClientDataHolder.noCooldown()));
        }
        if (goldBtn != null) {
            goldBtn.setMessage(toggleLabel("screen.myriad.train.nogold", ClientDataHolder.noGoldCost()));
        }
        if (manaBtn != null) {
            manaBtn.setMessage(toggleLabel("screen.myriad.train.nomana", ClientDataHolder.noMana()));
        }
        if (xuanxiaoBtn != null) {
            xuanxiaoBtn.setMessage(factionLabel("screen.myriad.train.faction_xuanxiao",
                    ClientDataHolder.faction() == com.myriad.game.Faction.XUANXIAO));
        }
        if (youmingBtn != null) {
            youmingBtn.setMessage(factionLabel("screen.myriad.train.faction_youming",
                    ClientDataHolder.faction() == com.myriad.game.Faction.YOUMING));
        }
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable(key).append("：")
                .append(Component.literal(on ? "开启" : "关闭")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(on ? 0xFF55FF55 : 0xFF888888)));
    }

    private static Component factionLabel(String key, boolean active) {
        return Component.translatable(key).copy().withStyle(
                net.minecraft.network.chat.Style.EMPTY.withColor(active ? 0xFF55FF55 : 0xFFB8C4D4))
                .append(active ? Component.translatable("screen.myriad.train.faction_current")
                        .withStyle(net.minecraft.ChatFormatting.GREEN) : Component.empty());
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gui,
            int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        String title = Component.translatable("screen.myriad.train.title").getString();
        gui.text(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(title) / 2, 10, SECTION_COLOR);
        String hint = Component.translatable("screen.myriad.train.hint").getString();
        gui.text(font, Component.literal(hint),
                this.width / 2 - font.width(hint) / 2, 24, 0xFF808080);
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
