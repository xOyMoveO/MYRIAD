package com.myriad.character.skills.fenjue.rework;

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
public final class LavaCageSkill extends AbilitySkill {
    public static final int WARNING_TICKS = 12, FIELD_TICKS = 80, PULSE_TICKS = 20, REFRESH_TICKS = 4, SLOW_TICKS = 8;
    public static final double RANGE = 12, RADIUS = 3.5;
    public static final float SLOW_RATE = .25f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(25, 11.25f, .2f, .5f / 4).segment("skill.myriad.damage.segment.pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public LavaCageSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,RADIUS,WARNING_TICKS);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return RADIUS;}
    @Override public double pointRange(Cast c){return RANGE;}
 @Override public void release(Cast c){FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.CAGE_FIRE,10);
 Vec3 at=c.point;FlameMajorAudio.cageCast(c,at);c.ring(at,RADIUS,false);c.later(WARNING_TICKS,false,()->{FlameMajorAudio.cageOpen(c,at);com.myriad.character.ability.AbilityVisuals.field(c,at,RADIUS,FIELD_TICKS);
 c.repeat(FIELD_TICKS,30,i->FlameMajorAudio.burning(c,at));
 c.tickFor(FIELD_TICKS,age->{if(age%REFRESH_TICKS==0){c.ring(at,RADIUS,false);for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW_RATE,SLOW_TICKS);}});
 c.repeat(FIELD_TICKS,PULSE_TICKS,i->c.pulse(at,RADIUS,DAMAGE.value(c),DAMAGE.kind()));});}
}
