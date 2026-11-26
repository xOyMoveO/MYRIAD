package com.myriad.client;

import com.myriad.network.EnemyHealthPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** 固定世界观感的英雄头顶血条；随观察距离缩放，敌军遵守战争迷雾。 */
public final class EnemyHealthBars {
    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 6;
    /** 8 格内保持完整可读尺寸，之后按距离自然缩小，模拟贴在目标头顶的世界标牌。 */
    private static final float FULL_SIZE_DISTANCE = 8.0F;
    private static final float MINIMUM_SCALE = 0.10F;
    private static final int ENEMY_EMPTY = 0xEE491F29;
    private static final int ENEMY_FILL = 0xFFEF5661;
    private static final int ALLY_EMPTY = 0xEE173D2A;
    private static final int ALLY_FILL = 0xFF55D985;
    private static EnemyHealthPayload snapshot;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static net.minecraft.client.player.LocalPlayer observer;
    private static long sequence = -1;
    private static int receivedTick;

    public static void reset() {
        snapshot = null;
        world = null;
        observer = null;
    }

    public static void resetConnection() {
        reset();
        sequence = -1;
    }

    public static void update(EnemyHealthPayload payload) {
        var mc = Minecraft.getInstance();
        if (payload.sequence() <= sequence) return;
        sequence = payload.sequence();
        if (mc.level == null || mc.player == null || !mc.level.dimension().identifier().equals(payload.dimension())) {
            reset();
            return;
        }
        world = mc.level;
        observer = mc.player;
        receivedTick = observer.tickCount;
        snapshot = payload;
    }
    public static void render(GuiGraphicsExtractor gui, Font font, float partialTick) {
        var mc = Minecraft.getInstance();
        boolean respawnSpectator = ClientDataHolder.buffTicks("hero_respawn") > 0;
        if (snapshot == null || !ClientDataHolder.modEnabled() || mc.player == null || mc.player != observer || mc.level != world
                || mc.player.tickCount - receivedTick > 20 || mc.player.isSpectator() && !respawnSpectator) return;
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        var camera = mc.gameRenderer.mainCamera();
        float age = Math.max(0, mc.player.tickCount - receivedTick + partialTick);
        var rows = new OverheadControlRow();
        for (var entry : snapshot.entries()) {
            if (!(mc.level.getEntity(entry.entityId()) instanceof net.minecraft.world.entity.player.Player target)
                    || target == mc.player || !target.getUUID().equals(entry.playerId())
                    || !target.isAlive() || target.isSpectator() || target.isInvisibleTo(mc.player)
                    || !FogOfWarClient.shouldRenderPlayer(target.getId()) || !mc.player.hasLineOfSight(target)) continue;
            Vec3 head = target.getPosition(partialTick).add(0, target.getBbHeight() + .35, 0);
            if (head.subtract(camera.position()).dot(new Vec3(camera.forwardVector())) <= 0) continue;
            var point = mc.gameRenderer.projectPointToScreen(head);
            int x = (int)((point.x + 1) * .5 * width), y = (int)((1 - point.y) * .5 * height);
            if (x < 0 || x > width || y < 28 || y > height) continue;
            float distance = (float) Math.sqrt(head.distanceToSqr(camera.position()));
            float scale = Math.clamp(FULL_SIZE_DISTANCE / Math.max(FULL_SIZE_DISTANCE, distance),
                    MINIMUM_SCALE, 1.0F);
            float fraction = Math.clamp(entry.health() / Math.max(1, entry.maximum()), 0, 1);
            int emptyColor = entry.ally() ? ALLY_EMPTY : ENEMY_EMPTY;
            int fillColor = entry.ally() ? ALLY_FILL : ENEMY_FILL;
            var health = net.minecraft.network.chat.Component.literal(Math.max(0, Math.round(entry.health())) + " / " + Math.round(entry.maximum()));
            if (entry.shield() > 0) health.append(net.minecraft.network.chat.Component.translatable("hud.myriad.overhead_shield",
                    Math.max(1, Math.round(entry.shield()))).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xA1EEFF)));
            String name = target.getName().getString();
            int nameColor = entry.ally() ? 0xFF72E69A : 0xFFFF8088;
            rows.protect(x, y, scale, Math.max(font.width(name), font.width(health)), entry.shield() > 0 ? 12 : 7);
            boolean cameraCanSee = !respawnSpectator || entry.ally()
                    || mc.getCameraEntity() instanceof net.minecraft.world.entity.player.Player teammate
                    && teammate != mc.player && teammate.isAlive() && !teammate.isSpectator()
                    && !target.isInvisibleTo(teammate) && teammate.hasLineOfSight(target);
            if (cameraCanSee && EnemyHealthPayload.inRange(mc.player.distanceToSqr(target))) {
                rows.offer(entry.entityId(), distance, x, y, scale, gui.pose().m00(), entry.controls(), age);
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x, y);
            gui.pose().scale(scale);
            int left = -BAR_WIDTH / 2;
            gui.fill(left - 1, -1, left + BAR_WIDTH + 1, BAR_HEIGHT + 1, 0xEE11151B);
            gui.fill(left, 0, left + BAR_WIDTH, BAR_HEIGHT, emptyColor);
            gui.fill(left, 0, left + Math.round(BAR_WIDTH * fraction), BAR_HEIGHT, fillColor);
            int shieldWidth = EnemyHealthPayload.shieldPixels(entry.shield(), entry.maximum(), BAR_WIDTH);
            if (shieldWidth > 0) {
                gui.fill(left - 1, BAR_HEIGHT + 1, left + BAR_WIDTH + 1, BAR_HEIGHT + 6, 0xEE11151B);
                gui.fill(left, BAR_HEIGHT + 2, left + BAR_WIDTH, BAR_HEIGHT + 5, 0xFF203E50);
                gui.fill(left, BAR_HEIGHT + 2, left + shieldWidth, BAR_HEIGHT + 5, 0xFF76D9FF);
            }
            gui.text(font, name, -font.width(name) / 2, -21, nameColor);
            gui.text(font, health, -font.width(health) / 2, -11, 0xFFFFFFFF);
            gui.pose().popMatrix();
        }
        rows.draw(gui, width, height, age);
    }
}
