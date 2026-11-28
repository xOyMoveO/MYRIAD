package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class InkProtectionArraySkill extends AbilitySkill {
    public static final double RANGE = 12;
    public static final double RADIUS = 4;
    public static final int WARNING_TICKS = 12;
    public static final int FIELD_TICKS = 80;
    public static final int SHIELD_TICKS = 40;
    public static final int REFRESH_TICKS = 4;
    public static final int GUARD_TICKS = 6;
    public static final float SLOW = .25f;
    public static final int SLOW_TICKS = 8;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", RANGE,
            "radius", RADIUS,
            "warningSeconds", WARNING_TICKS / 20.0,
            "fieldSeconds", FIELD_TICKS / 20.0,
            "shieldSeconds", SHIELD_TICKS / 20.0,
            "guardPercent", Math.round(100 * (1 - AbilityRuntime.GUARD12_DAMAGE_MULTIPLIER)),
            "slowPercent", Math.round(100 * SLOW));
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 60, 20.7f, .35f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public InkProtectionArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){InkVisuals.warn(c,c.point,RADIUS,WARNING_TICKS);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return RADIUS;}
    @Override public double pointRange(Cast c){return RANGE;}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
 @Override public void release(Cast c){InkCastCues.release(c);Vec3 at=c.point;c.later(WARNING_TICKS,false,()->{InkCastCues.activate(c);com.myriad.character.ability.AbilityVisuals.field(c,at,RADIUS,FIELD_TICKS);
 for(var ally:c.allies(at,RADIUS))c.shield(ally,SHIELD.value(c),SHIELD_TICKS);
 c.tickFor(FIELD_TICKS,age->{if(age%REFRESH_TICKS==0){for(var ally:c.allies(at,RADIUS))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard12",GUARD_TICKS);
 for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW,SLOW_TICKS);}});});}
}
