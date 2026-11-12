package com.myriad.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** 素岚旋转击的白色月牙刀光；速度参数用于传递初始滚转、旋转速度和尺寸。 */
public final class SulanBladeArcParticle extends SingleQuadParticle {
    private final float baseSize;
    private final float spinSpeed;

    private SulanBladeArcParticle(ClientLevel level, double x, double y, double z,
            float initialRoll, float spinSpeed, float size, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.baseSize = size;
        this.spinSpeed = spinSpeed;
        this.quadSize = size;
        this.roll = initialRoll;
        this.oRoll = initialRoll;
        this.lifetime = 25;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.setAlpha(0.96F);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) {
            return;
        }
        this.roll += this.spinSpeed;
        float progress = this.age / (float)this.lifetime;
        this.quadSize = this.baseSize * (0.88F + 0.20F * Mth.sin(progress * Mth.PI));
        this.setAlpha(0.96F * (1.0F - progress * progress));
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

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                double x, double y, double z, double initialRoll, double spinSpeed, double size,
                RandomSource random) {
            float resolvedSize = (float)(size > 0.05 ? size : 1.4);
            float resolvedSpin = (float)(Math.abs(spinSpeed) > 0.01 ? spinSpeed : 0.45);
            return new SulanBladeArcParticle(level, x, y, z,
                    (float)initialRoll, resolvedSpin, resolvedSize, this.sprites);
        }
    }
}
