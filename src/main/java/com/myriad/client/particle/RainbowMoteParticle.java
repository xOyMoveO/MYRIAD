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
 * Bedrock Snowstorm 彩虹粒子的 NeoForge 实现：全亮、轻微上浮、生命周期内先显后隐。
 * RGB 通过 count=0 粒子包的三个偏移量传递，便于服务端组合为彩虹环。
 */
public final class RainbowMoteParticle extends SingleQuadParticle {
    private final float baseSize;

    private RainbowMoteParticle(ClientLevel level, double x, double y, double z,
            float red, float green, float blue, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.get(random));
        this.baseSize = 0.075F + random.nextFloat() * 0.045F;
        this.quadSize = baseSize * 0.35F;
        this.lifetime = 24 + random.nextInt(8);
        this.gravity = -0.018F;
        this.friction = 0.94F;
        this.hasPhysics = false;
        this.xd = (random.nextDouble() - 0.5) * 0.014;
        this.yd = 0.020 + random.nextDouble() * 0.016;
        this.zd = (random.nextDouble() - 0.5) * 0.014;
        this.setColor(red, green, blue);
        this.setAlpha(0.92F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        float progress = this.age / (float)this.lifetime;
        float pulse = Mth.sin(progress * Mth.PI);
        this.quadSize = this.baseSize * (0.35F + 0.85F * pulse);
        this.setAlpha(0.92F * pulse);
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
            return new RainbowMoteParticle(level, x, y, z,
                    Mth.clamp((float)red, 0.0F, 1.0F),
                    Mth.clamp((float)green, 0.0F, 1.0F),
                    Mth.clamp((float)blue, 0.0F, 1.0F), sprites, random);
        }
    }
}
