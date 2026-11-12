package com.myriad.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** Short-lived textured blade strokes and closing return sigils. */
public final class ZhechuanSigilParticle extends SingleQuadParticle {
    private final float size;
    private final boolean glyph;
    private ZhechuanSigilParticle(ClientLevel level, double x, double y, double z,
            double angle, double gold, double size, SpriteSet sprites, boolean glyph) {
        super(level, x, y, z, sprites.first());
        this.size = (float)Math.max(.2, Math.min(3, size));
        this.glyph = glyph;
        this.quadSize = this.size;
        this.roll = this.oRoll = (float)angle;
        this.lifetime = glyph ? 14 : 8;
        this.gravity = 0;
        this.hasPhysics = false;
        this.friction = 1;
        this.setColor(gold > .5 ? 1F : .28F, gold > .5 ? .82F : .94F, gold > .5 ? .4F : 1F);
        this.setAlpha(.85F);
    }
    @Override public void tick() {
        this.oRoll = this.roll;
        super.tick();
        float progress = this.age / (float)this.lifetime;
        this.roll += glyph ? .045F : -.09F;
        this.quadSize = size * (glyph ? .65F + progress * .6F : .85F + progress * .25F);
        this.setAlpha(.85F * (1 - progress) * (1 - progress));
    }
    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean glyph;
        public Provider(SpriteSet sprites, boolean glyph) { this.sprites = sprites; this.glyph = glyph; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double angle, double gold, double size, RandomSource random) {
            return new ZhechuanSigilParticle(level, x, y, z, angle, gold, size, sprites, glyph);
        }
    }
}
