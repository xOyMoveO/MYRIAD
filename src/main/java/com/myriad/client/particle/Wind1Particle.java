package com.myriad.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Wind1.efkproj 的客户端粒子近似实现。
 * Effekseer 中的两个 billboard 节点分别由 WIND1_LARGE/WIND1_SMALL 驱动。
 */
public final class Wind1Particle extends SingleQuadParticle {
    private final float baseSize;
    private final float spinSpeed;
    private final boolean large;

    private Wind1Particle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, float initialRoll,
            float spinSpeed, float size, boolean large, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.large = large;
        this.baseSize = size;
        this.spinSpeed = spinSpeed;
        this.xd = large ? 0.0 : (random.nextDouble() - 0.5) * 0.08;
        this.yd = large ? 0.0 : (random.nextDouble() - 0.5) * 0.08;
        this.zd = large ? 0.0 : (random.nextDouble() - 0.5) * 0.08;
        this.quadSize = size;
        this.roll = initialRoll;
        this.oRoll = initialRoll;
        this.lifetime = large ? 30 : 20;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.setAlpha(0.0F);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) {
            return;
        }
        this.roll += this.spinSpeed;
        float progress = this.age / (float) this.lifetime;
        float fade = Mth.sin(Mth.PI * progress);
        this.quadSize = this.baseSize * (large
                ? 0.72F + 0.38F * fade
                : 0.62F + 0.28F * fade);
        this.setAlpha((large ? 0.82F : 0.52F) * fade);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean large;

        public Provider(SpriteSet sprites, boolean large) {
            this.sprites = sprites;
            this.large = large;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            float size = (float) (xSpeed > 0.05 ? xSpeed : (large ? 1.35 : 0.78));
            float roll = (float) ySpeed;
            float spin = (float) zSpeed;
            return new Wind1Particle(level, x, y, z, 0.0, 0.0, 0.0,
                    roll, spin, size, large, sprites, random);
        }
    }
}
