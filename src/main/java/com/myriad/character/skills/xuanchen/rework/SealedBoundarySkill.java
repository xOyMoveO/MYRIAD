package com.myriad.character.skills.xuanchen.rework;

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
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class SealedBoundarySkill extends AbilitySkill {
    public static final double RANGE = 12;
    public static final double RADIUS = 4;
    public static final int WARNING_TICKS = 14;
    public static final int FIELD_TICKS = 60;
    public static final int ROOT_TICKS = 20;
    public static final int REFRESH_TICKS = 4;
    public static final float SLOW = .2f;
    public static final int SLOW_TICKS = 8;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", RANGE,
            "radius", RADIUS,
            "warningSeconds", WARNING_TICKS / 20.0,
            "fieldSeconds", FIELD_TICKS / 20.0,
            "rootSeconds", ROOT_TICKS / 20.0,
            "slowPercent", Math.round(100 * SLOW));
    }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 170, 74.75f, 1.1f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SealedBoundarySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){InkVisuals.warn(c,c.point,RADIUS,WARNING_TICKS);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return RADIUS;}
    @Override public double pointRange(Cast c){return RANGE;}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
 @Override public void release(Cast c){InkCastCues.release(c);Vec3 at=c.point;c.later(WARNING_TICKS,false,()->{InkCastCues.activate(c);com.myriad.character.ability.AbilityVisuals.field(c,at,RADIUS,FIELD_TICKS);InkVisuals.closeBoundary(c,at);
 for(var t:c.enemies(at,RADIUS))c.damage(t,DAMAGE.value(c),DAMAGE.kind());
 for(var t:c.enemies(at,RADIUS)){c.root(t,ROOT_TICKS,false);InkVisuals.binding(c,t,12);}
 c.tickFor(FIELD_TICKS,age->{if(age%REFRESH_TICKS==0){for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW,SLOW_TICKS);}});});}
}
