package com.myriad.client;

import com.myriad.game.Faction;
import com.myriad.game.LaneMinionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** 小兵只显示可补刀的实际血量与短血条，不显示实体名称。 */
public final class MinionHealthBars {
    private static final int WIDTH = 42;
    private static final int HEIGHT = 4;

    private MinionHealthBars() {}

    public static void render(GuiGraphicsExtractor gui, Font font, float partialTick) {
        var mc = Minecraft.getInstance();
        if (!ClientDataHolder.modEnabled() || mc.player == null || mc.level == null) return;
        var camera = mc.gameRenderer.mainCamera();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        Faction faction = ClientDataHolder.faction();

        for (LaneMinionEntity minion : mc.level.getEntitiesOfClass(LaneMinionEntity.class,
                mc.player.getBoundingBox().inflate(24), entity -> entity.isAlive())) {
            if (!mc.player.hasLineOfSight(minion)) continue;
            Vec3 head = minion.getPosition(partialTick).add(0, minion.getBbHeight() + .18, 0);
            Vec3 offset = head.subtract(camera.position());
            if (offset.dot(new Vec3(camera.forwardVector())) <= 0) continue;
            var point = mc.gameRenderer.projectPointToScreen(head);
            int x = (int) ((point.x + 1) * .5 * screenWidth);
            int y = (int) ((1 - point.y) * .5 * screenHeight);
            if (x < 0 || x >= screenWidth || y < 18 || y >= screenHeight) continue;

            float maximum = Math.max(1, minion.displayMaxHealth());
            float health = Math.max(0, minion.displayHealth());
            float fraction = Math.clamp(health / maximum, 0, 1);
            float distance = (float) Math.sqrt(offset.lengthSqr());
            float scale = Math.clamp(8.0F / Math.max(8.0F, distance), .45F, 1.0F);
            boolean ally = faction != null && (minion.style() < 3) == (faction == Faction.XUANXIAO);
            int fill = ally ? 0xFF55D985 : fraction <= .25F ? 0xFFFFB24D : 0xFFF26A68;
            int empty = ally ? 0xDD173D2A : 0xDD491F29;
            String label = Integer.toString(Math.round(health));

            gui.pose().pushMatrix();
            gui.pose().translate(x, y);
            gui.pose().scale(scale);
            int left = -WIDTH / 2;
            gui.fill(left - 1, -1, left + WIDTH + 1, HEIGHT + 1, 0xEE11151B);
            gui.fill(left, 0, left + WIDTH, HEIGHT, empty);
            gui.fill(left, 0, left + Math.round(WIDTH * fraction), HEIGHT, fill);
            gui.text(font, label, -font.width(label) / 2, -11, 0xFFFFFFFF);
            gui.pose().popMatrix();
        }
    }
}
