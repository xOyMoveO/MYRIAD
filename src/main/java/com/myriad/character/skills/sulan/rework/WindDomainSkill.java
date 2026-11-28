package com.myriad.character.skills.sulan.rework;

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
public final class WindDomainSkill extends AbilitySkill {
    public static final int FIELD_TICKS = 80, REFRESH_TICKS = 4, STATUS_TICKS = 8;
    public static final double RADIUS = 5;
    public static final float HASTE_RATE = .15f, SLOW_RATE = .25f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(90, 43.75f, .65f, .8f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public WindDomainSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.following(c,com.myriad.character.ability.AbilityVfxGeometry.SEAL,c.player,RADIUS,FIELD_TICKS);}
    @Override public void release(Cast c){WindCastCues.release(c);c.area(c.player.position(),RADIUS,DAMAGE.value(c),DAMAGE.kind());
 c.tickFor(FIELD_TICKS,age->{if(age%REFRESH_TICKS==0){Vec3 at=c.player.position();
     for(var ally:c.allies(at,RADIUS)){c.slow(ally,-HASTE_RATE,STATUS_TICKS);if(age%8==0)WindVisuals.domainWind(c,ally,false);}
     for(var t:c.enemies(at,RADIUS)){c.slow(t,SLOW_RATE,STATUS_TICKS);if(age%8==0)WindVisuals.domainWind(c,t,true);}
 }});}
}
