package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.registry.ModSounds.Night.*;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ShadowDaggerSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(45, 20.25f, .55f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public boolean hasOwnCastSound() { return true; }

    public ShadowDaggerSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.daggerRelease(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.THROW,9);}
    @Override public void release(Cast c) {
        c.actionProjectileToFirst(15, 1.5, .8, (target, contact) -> {
            if (c.damage(target, DAMAGE.value(c), DAMAGE.kind())) NightAudio.impact(c, target, contact, HIT);
            c.slow(target, .25f, 30);
        });
        NightAudio.play(c, c.player.getEyePosition().add(c.player.getLookAngle().scale(.4)), THROW);
    }

    @Override public void projectileBlockedByWind(Cast c, Vec3 contact) {
        NightAudio.play(c, contact, BLOCK);
    }
}
