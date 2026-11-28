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
public final class HeavenCoverArraySkill extends AbilitySkill {
    public static final double RADIUS = 6;
    public static final int WINDUP_TICKS = 16;
    public static final int FIELD_TICKS = 80;
    public static final int PULSE_TICKS = 20;
    public static final int REFRESH_TICKS = 4;
    public static final int GUARD_TICKS = 6;
    public static final float SLOW = .3f;
    public static final int SLOW_TICKS = 8;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "radius", RADIUS,
            "windupSeconds", WINDUP_TICKS / 20.0,
            "fieldSeconds", FIELD_TICKS / 20.0,
            "pulseCount", FIELD_TICKS / PULSE_TICKS,
            "guardPercent", Math.round(100 * (1 - AbilityRuntime.GUARD_15_DAMAGE_TAKEN)),
            "slowPercent", Math.round(100 * SLOW));
    }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 23, .3f).segment("skill.myriad.damage.segment.pulse", FIELD_TICKS / PULSE_TICKS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public HeavenCoverArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.player.position(),RADIUS,WINDUP_TICKS);}
    @Override public void windupPose(Cast c,boolean winding){InkCastCues.charge(c,winding);}
    @Override public void release(Cast c){c.windup(WINDUP_TICKS,0,()->{InkCastCues.release(c);Vec3 at=c.player.position();com.myriad.character.ability.AbilityVisuals.field(c,at,RADIUS,FIELD_TICKS);
 c.tickFor(FIELD_TICKS,age->{if(age%REFRESH_TICKS==0){for(var ally:c.allies(at,RADIUS))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard15",GUARD_TICKS);
 for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW,SLOW_TICKS);}});
 c.repeat(FIELD_TICKS,PULSE_TICKS,i->{
     for(var target:c.enemies(at,RADIUS))c.damage(target,DAMAGE.value(c),DAMAGE.kind(),false);
     InkVisuals.arrayPulse(c,at,(i-1)*90);
 });});}
}
