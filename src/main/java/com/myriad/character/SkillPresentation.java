package com.myriad.character;

import com.myriad.client.particle.ParticleEffects;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;

/** Bounded authored silhouettes and material-based audio, after successful validation only. */
public final class SkillPresentation {
    private SkillPresentation() { }
    private record Style(String sound, int rgb, int shape) { }
    private static Style style(CharacterType type) {
        return switch (type) {
            case YINGXIAO -> new Style("blade", 0xA8A8A8, 0);
            case TIANXING -> new Style("thunder", 0x9ECCFF, 2);
            case FENJUE -> new Style("fire", 0xFF742D, 3);
            case XUANCHEN -> new Style("wood", 0xECC978, 2);
            case SULAN -> new Style("wind", 0xBAF4DB, 0);
            case LINGYUAN -> new Style("blade", 0xC1EFFF, 0);
            case HANZE -> new Style("ice", 0xA1DFFF, 2);
            case QINGLUO -> new Style("wood", 0x88CF65, 1);
            case CHIXIAO -> new Style("stone", 0xFFAB58, 0);
            case YUNSHENG -> new Style("wind", 0xB4E9D8, 1);
            case YIKE -> new Style("shot",0xFF6C78,2);
            case SHUO -> new Style("blade",0xCBEAFF,0);
        };
    }
    public static void cast(ServerPlayer p, Skill skill) {
        if(skill instanceof com.myriad.character.ability.AbilitySkill ability && ability.hasOwnCastSound())return;
        var data = PlayerDataProvider.get(p);
        CharacterType type = data == null ? null : CharacterType.byId(data.getCharacterId());
        if (type == null) return;
        Style s = style(type);
        int slot = 0;
        for (int i = 0; i < 5; i++) if (type.skill(i) != null && type.skill(i).getId().equals(skill.getId())) slot = i;
        if (skill instanceof com.myriad.character.ability.AbilitySkill ability) slot = Math.max(0, ability.slot());
        boolean ultimate = skill.isUltimate();
        // Stored on the entity: no global UUID map or cross-world lifecycle leak.
        long now = p.level().getGameTime();
        long last = p.getPersistentData().getLongOr("myriad_presentation_cast", Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < 2) return;
        p.getPersistentData().putLong("myriad_presentation_cast", now);
        p.level().playSound(null, p.blockPosition(), ModSounds.foley(s.sound), SoundSource.PLAYERS,
                ultimate ? .65F : .42F, 1F);
        // New modular skills author their own silhouettes; do not overlay the old generic burst.
        if(skill instanceof com.myriad.character.ability.AbilitySkill)return;
        Vec3 center = p.position().add(0, .12, 0);
        Vec3 forward = p.getLookAngle().multiply(1, 0, 1).normalize();
        if (forward.lengthSqr() < .1) forward = new Vec3(0, 0, 1);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        int points = ultimate ? 28 : 16;
        double radius = ultimate ? 1.85 : 1.05 + slot * .12;
        float r = ((s.rgb >> 16) & 255) / 255F, g = ((s.rgb >> 8) & 255) / 255F, b = (s.rgb & 255) / 255F;
        for (int i = 0; i < points; i++) {
            double t = i / (double)(points - 1), angle = t * Math.PI * 2;
            Vec3 at = switch (s.shape) {
                // A low, forward curved blade arc; stays below the crosshair.
                case 0 -> center.add(forward.scale(.35 + Math.sin(t * Math.PI) * radius))
                        .add(side.scale((t * 2 - 1) * radius)).add(0, .18 + .3 * Math.sin(t * Math.PI), 0);
                // Double, interlaced wave / leaf / moon orbits.
                case 1 -> center.add(Math.cos(angle) * radius, .1 + .18 * Math.sin(angle * 2), Math.sin(angle) * radius);
                // Distinct faceted seal rather than a filled particle cloud.
                case 2 -> {
                    double edge = t * 4, f = edge - Math.floor(edge), a = Math.floor(edge) * Math.PI / 2;
                    yield center.add(new Vec3(Math.cos(a), 0, Math.sin(a)).lerp(
                            new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2)), f).scale(radius));
                }
                default -> center.add(side.scale(Math.sin(angle) * radius)).add(forward.scale(Math.cos(angle) * .4))
                        .add(0, .2 + Math.abs(Math.sin(angle)) * .6, 0);
            };
            ParticleEffects.mote(p.level(), at, r, g, b);
        }
    }
    public static net.minecraft.sounds.SoundEvent impactSound(CharacterType type) {
        return ModSounds.foley(style(type).sound);
    }
    public static void hit(ServerPlayer p, LivingEntity target, boolean critical) {
        if (target instanceof com.myriad.game.DefenseTowerEntity) return;
        var data = PlayerDataProvider.get(p);
        var type = data == null ? null : CharacterType.byId(data.getCharacterId());
        if (type == null) return;
        // Fenjue's contact is emitted after measured HP/shield loss, not this older generic burst.
        if(type==CharacterType.FENJUE)return;
        long now = p.level().getGameTime();
        long last = p.getPersistentData().getLongOr("myriad_presentation_hit", Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < 5) return;
        p.getPersistentData().putLong("myriad_presentation_hit", now);
        Style s = style(type);
        Vec3 c = target.position().add(0, target.getBbHeight() * .5, 0);
        // Personal confirmation only: no hidden-target position is broadcast to spectators/enemies.
        if (!com.myriad.game.VisionSystem.canSeePosition(p, c)) return;
        Vec3 forward = c.subtract(p.getEyePosition()).normalize();
        Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(forward).normalize();
        if(com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())){
            var skill=com.myriad.character.rework.FirstGroupSkills.candidates(data.getCharacterId()).get(0);
            com.myriad.character.ability.AbilityVisuals.impact(
                    com.myriad.character.ability.AbilityRuntime.context(p,skill),c);
            return;
        }
        var dust = new net.minecraft.core.particles.DustParticleOptions(critical ? 0xFFD36A : s.rgb, critical ? .85F : .6F);
        int count = critical ? 12 : 8;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            Vec3 at = c.add(side.scale(Math.cos(angle) * (critical ? .42 : .26)))
                    .add(up.scale(Math.sin(angle) * (critical ? .42 : .26)));
            p.level().sendParticles(p, dust, false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        p.level().sendParticles(p, net.minecraft.core.particles.ParticleTypes.CRIT, false, false,
                c.x, c.y, c.z, critical ? 7 : 3, .12, .15, .12, .12);
    }
}
