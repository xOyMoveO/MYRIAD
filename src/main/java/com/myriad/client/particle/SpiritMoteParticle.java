package com.myriad.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** 全亮、轻微上浮并渐隐的通用灵光粒子。 */
public final class SpiritMoteParticle extends SingleQuadParticle {
    private final float baseSize;

    private SpiritMoteParticle(ClientLevel level, double x, double y, double z,
            float red, float green, float blue, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.get(random));
        this.baseSize = 0.10F + random.nextFloat() * 0.07F;
        this.quadSize = baseSize;
        this.lifetime = 16 + random.nextInt(9);
        this.gravity = -0.012F;
        this.friction = 0.92F;
        this.hasPhysics = false;
        this.xd = (random.nextDouble() - 0.5) * 0.018;
        this.yd = 0.018 + random.nextDouble() * 0.018;
        this.zd = (random.nextDouble() - 0.5) * 0.018;
        this.setColor(red, green, blue);
        this.setAlpha(0.90F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        float progress = this.age / (float)this.lifetime;
        this.quadSize = this.baseSize * (0.75F + 0.55F * Mth.sin(progress * Mth.PI));
        this.setAlpha(0.90F * (1.0F - progress * progress));
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
                double x, double y, double z, double red, double green, double blue,
                RandomSource random) {
            // SimpleParticleType 没有自定义网络参数；count=0 时三个偏移量可稳定传递到这里。
            return new SpiritMoteParticle(level, x, y, z,
                    Mth.clamp((float)red, 0.0F, 1.0F),
                    Mth.clamp((float)green, 0.0F, 1.0F),
                    Mth.clamp((float)blue, 0.0F, 1.0F), sprites, random);
        }
    }
}
