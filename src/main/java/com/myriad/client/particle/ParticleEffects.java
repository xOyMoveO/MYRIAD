package com.myriad.client.particle;

import com.myriad.registry.ModParticles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 服务器侧技能特效工具。只负责向附近客户端同步视觉，不承载任何伤害、命中或状态逻辑。
 */
public final class ParticleEffects {
    private ParticleEffects() {
    }

    /** 在一个点生成可着色的灵光。RGB 分量范围均为 0~1。 */
    public static void mote(ServerLevel level, Vec3 position, float red, float green, float blue) {
        level.sendParticles(ModParticles.SPIRIT_MOTE.get(), position.x, position.y, position.z,
                0, clamp(red), clamp(green), clamp(blue), 1.0);
    }

    /** 在球状范围内洒出灵光，适用于命中、治疗、爆发等短促反馈。 */
    public static void burst(ServerLevel level, Vec3 center, int count, double radius,
            float red, float green, float blue) {
        int safeCount = Math.max(0, Math.min(count, 96));
        for (int i = 0; i < safeCount; i++) {
            Vec3 offset;
            do {
                offset = new Vec3(level.getRandom().nextDouble() * 2.0 - 1.0,
                        level.getRandom().nextDouble() * 2.0 - 1.0,
                        level.getRandom().nextDouble() * 2.0 - 1.0);
            } while (offset.lengthSqr() > 1.0);
            mote(level, center.add(offset.scale(radius)), red, green, blue);
        }
    }

    /** 在水平面绘制一圈灵光，适用于护盾、领域和蓄力范围。 */
    public static void ring(ServerLevel level, Vec3 center, double radius, int count,
            float red, float green, float blue) {
        int safeCount = Math.max(3, Math.min(count, 96));
        for (int i = 0; i < safeCount; i++) {
            double angle = Math.PI * 2.0 * i / safeCount;
            mote(level, center.add(Math.cos(angle) * radius, 0.04, Math.sin(angle) * radius),
                    red, green, blue);
        }
    }

    /**
     * Snowstorm {@code rainbow} 的模组化版本：一帧生成一圈彩虹灵屑。
     * 调用方在持续效果中每 2~4 tick 调用一次，即可得到原文件中环绕、上浮、渐隐的彩虹光环。
     * 每次最多发送 48 粒，避免原配置 250/s 的高粒子量影响多人对局性能。
     */
    public static void rainbowRing(ServerLevel level, Vec3 center, double radius, int count) {
        int safeCount = Math.max(3, Math.min(count, 48));
        double phase = level.getGameTime() * 0.18;
        for (int i = 0; i < safeCount; i++) {
            double angle = phase + Math.PI * 2.0 * i / safeCount;
            double r = radius * (0.55 + level.getRandom().nextDouble() * 0.45);
            float hue = (float)((i / (double)safeCount + level.getGameTime() * 0.0125) % 1.0);
            float[] rgb = hueToRgb(hue);
            level.sendParticles(ModParticles.RAINBOW_MOTE.get(),
                    center.x + Math.cos(angle) * r, center.y + 0.04, center.z + Math.sin(angle) * r,
                    0, rgb[0], rgb[1], rgb[2], 1.0);
        }
    }

    private static float[] hueToRgb(float hue) {
        float h = hue * 6.0F;
        int sector = (int)Math.floor(h);
        float f = h - sector;
        return switch (sector % 6) {
            case 0 -> new float[] {1.0F, f, 0.12F};
            case 1 -> new float[] {1.0F - f, 1.0F, 0.12F};
            case 2 -> new float[] {0.12F, 1.0F, f};
            case 3 -> new float[] {0.12F, 1.0F - f, 1.0F};
            case 4 -> new float[] {f, 0.12F, 1.0F};
            default -> new float[] {1.0F, 0.12F, 1.0F - f};
        };
    }

    /** 沿两点之间均匀铺开灵光，适用于冲刺、弹道和剑气拖尾。 */
    public static void trail(ServerLevel level, Vec3 start, Vec3 end, double spacing,
            float red, float green, float blue) {
        double distance = start.distanceTo(end);
        int steps = Math.max(1, Math.min(96, (int)Math.ceil(distance / Math.max(0.05, spacing))));
        for (int i = 0; i <= steps; i++) {
            mote(level, start.lerp(end, i / (double)steps), red, green, blue);
        }
    }

    private static double clamp(float value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
