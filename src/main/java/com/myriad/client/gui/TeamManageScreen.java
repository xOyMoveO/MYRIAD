package com.myriad.client.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.network.NetworkSetup;

/**
 * 开局管理界面（/myriad start 打开）：
 * 顶部「随机开局」（按 2v4 / 3v5 均衡分配）与「开始游戏」；
 * 玩家列表每行显示当前宗门，可用玄霄宗/幽冥宗按钮分配（服务端校验权限）。
 */
public class TeamManageScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    /** 服务端推送的最新玩家阵营表（name -> factionId 0=未分配 1=玄霄宗 2=幽冥宗） */
    private static Map<String, Integer> teams = new LinkedHashMap<>();
    private static String arenaId = "5v5";
    private static boolean duelAvailable;
    private boolean ready;
    private boolean selecting;
    private int scrollRow;
    private int visibleRows;

    public TeamManageScreen() {
        super(Component.translatable("screen.myriad.team.title"));
        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestTeamListPayload());
    }

    /** 网络包回调（主线程）：刷新玩家列表 */
    public static void receiveTeamList(Map<String, Integer> incoming, String selectedArena, boolean hasDuel) {
        teams = new LinkedHashMap<>(incoming);
        arenaId = selectedArena;
        duelAvailable = hasDuel;
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof TeamManageScreen screen) {
            screen.ready = true;
            screen.selecting = false;
            screen.rebuild();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int listW = Math.min(320, width - 12);
        int listX = centerX - listW / 2;
        int startY = 83;

        addRenderableWidget(Button.builder(Component.translatable("arena.myriad.5v5"), btn -> selectArena("5v5"))
                .bounds(listX, 27, listW / 2 - 5, 20).build()).active = ready && !selecting && !arenaId.equals("5v5");
        addRenderableWidget(Button.builder(Component.translatable("arena.myriad.3v3"), btn -> selectArena("3v3"))
                .bounds(centerX + 5, 27, listW / 2 - 5, 20).build()).active = ready && !selecting && duelAvailable && !arenaId.equals("3v3");

        // 顶部操作按钮
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.myriad.team.random"), btn -> {
                            ClientPacketDistributor.sendToServer(new NetworkSetup.StartMatchPayload(0, arenaId));
                            this.onClose();
                        })
                .bounds(centerX - listW / 2, 56, 120, 20)
                .build()).active = ready && !selecting;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.myriad.team.start"), btn -> {
                            ClientPacketDistributor.sendToServer(new NetworkSetup.StartMatchPayload(1, arenaId));
                            this.onClose();
                        })
                .bounds(centerX + 20, 56, 120, 20)
                .build()).active = ready && !selecting;

        // 玩家行：名字 + [宗门] + 玄霄宗/幽冥宗按钮
        int i = 0;
        visibleRows = Math.max(1, (height - startY - 40) / ROW_H);
        scrollRow = Math.clamp(scrollRow, 0, Math.max(0, teams.size() - visibleRows));
        for (Map.Entry<String, Integer> entry : teams.entrySet()) {
            if (i < scrollRow || i >= scrollRow + visibleRows) { i++; continue; }
            String name = entry.getKey();
            int faction = entry.getValue();
            int y = startY + (i - scrollRow) * ROW_H;
            String factionLabel = faction == 1
                    ? "[" + Component.translatable("faction.myriad.xuanxiao").getString() + "]"
                    : faction == 2
                            ? "[" + Component.translatable("faction.myriad.youming").getString() + "]"
                            : "[" + Component.translatable("screen.myriad.team.unassigned").getString() + "]";
            int factionColor = faction == 1 ? 0xFFFF5555 : faction == 2 ? 0xFF55AAFF : 0xFF808080;
            addRenderableWidget(Button.builder(Component.literal(name + "  " + factionLabel)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(factionColor)),
                            btn -> {
                            })
                    .bounds(listX, y, listW - 150, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("faction.myriad.xuanxiao"), btn -> {
                        ClientPacketDistributor.sendToServer(new NetworkSetup.SetTeamPayload(name, 1));
                    })
                    .bounds(listX + listW - 146, y, 48, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("faction.myriad.youming"), btn -> {
                        ClientPacketDistributor.sendToServer(new NetworkSetup.SetTeamPayload(name, 2));
                    })
                    .bounds(listX + listW - 94, y, 48, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("screen.myriad.team.clear"), btn -> {
                        ClientPacketDistributor.sendToServer(new NetworkSetup.SetTeamPayload(name, 0));
                    })
                    .bounds(listX + listW - 42, y, 42, 18)
                    .build());
            i++;
        }
    }

    private void selectArena(String id) {
        selecting = true;
        rebuild();
        ClientPacketDistributor.sendToServer(new NetworkSetup.SelectArenaPayload(id));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scrollRow = Math.clamp(scrollRow - (int)Math.signum(dy), 0, Math.max(0, teams.size() - visibleRows));
        rebuild();
        return true;
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gui,
            int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        gui.text(font, Component.translatable("screen.myriad.team.title")
                        .withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(Component.translatable("screen.myriad.team.title")) / 2,
                0, TITLE_COLOR);
        Component selected = Component.translatable("screen.myriad.team.arena", Component.translatable("arena.myriad." + arenaId));
        gui.text(font, selected, this.width / 2 - font.width(selected) / 2, 14, SECTION_COLOR);
        if (ready && !duelAvailable)
            gui.text(font, Component.translatable("screen.myriad.team.no_duel"), this.width / 2 - 160, this.height - 32, 0xFFFFAA55);
        gui.text(font, Component.translatable("screen.myriad.team.hint"),
                this.width / 2 - font.width(Component.translatable("screen.myriad.team.hint")) / 2,
                this.height - 20, 0xFF808080);
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
