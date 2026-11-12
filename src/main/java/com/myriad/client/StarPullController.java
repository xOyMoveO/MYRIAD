package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** 逆转星盘牵引的客户端 motion 同步，避免本地移动输入覆盖服务端吸引速度。 */
public final class StarPullController {
    private static double centerX;
    private static double centerZ;
    private static double strength;
    private static double blend;
    private static int remainingTicks;

    private StarPullController() { }

    public static void start(float x, float z, float pullStrength, float velocityBlend, int ticks) {
        centerX = x;
        centerZ = z;
        strength = pullStrength;
        blend = velocityBlend;
        remainingTicks = Math.max(0, ticks);
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            remainingTicks = 0;
            return;
        }
        if (remainingTicks <= 0) return;
        remainingTicks--;
        // 突进 motion 优先，结束后若仍在星盘内，服务端会继续下发牵引状态。
        if (DashController.isDashing()) return;
        Vec3 delta = new Vec3(centerX - player.getX(), 0, centerZ - player.getZ());
        if (delta.horizontalDistanceSqr() <= .12) return;
        Vec3 pull = delta.normalize().scale(strength);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x * blend + pull.x, velocity.y,
                velocity.z * blend + pull.z);
    }
}
