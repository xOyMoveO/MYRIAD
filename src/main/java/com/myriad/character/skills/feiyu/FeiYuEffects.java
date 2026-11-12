package com.myriad.character.skills.feiyu;

import com.myriad.Config;
import com.myriad.game.VisionSystem;
import com.myriad.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Visual-only profiles; every emitted point is filtered per observer. */
final class FeiYuEffects {
    private FeiYuEffects() { }
    static int samples(int count) {
        return Config.FEIYU_EFFECT_QUALITY.get() == Config.EffectQuality.LOW
                ? Math.max(1, (count + 2) / 3) : count;
    }
    static <T extends ParticleOptions> void send(ServerLevel level, T particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        Vec3 point = new Vec3(x, y, z);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.position().distanceToSqr(point) <= 4096 && VisionSystem.canSeePosition(viewer, point))
                level.sendParticles(viewer, particle, false, false, x, y, z,
                        count == 0 ? 0 : samples(count), dx, dy, dz, speed);
        }
    }
    static void mote(ServerLevel level, Vec3 point, float r, float g, float b) {
        send(level, ModParticles.SPIRIT_MOTE.get(), point.x, point.y, point.z, 0, r, g, b, 1);
    }
    static void burst(ServerLevel level, Vec3 center, int count, double radius, float r, float g, float b) {
        for (int i = 0; i < samples(count); i++) {
            Vec3 offset = new Vec3(level.getRandom().nextDouble() * 2 - 1,
                    level.getRandom().nextDouble() * 2 - 1, level.getRandom().nextDouble() * 2 - 1).scale(radius);
            mote(level, center.add(offset), r, g, b);
        }
    }
}
