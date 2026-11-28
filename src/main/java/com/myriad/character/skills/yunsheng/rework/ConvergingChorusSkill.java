package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class ConvergingChorusSkill extends AbilitySkill {
    public static final double RANGE = 14;
    public static final double RADIUS = 6;
    public static final int WINDUP_TICKS = 20;
    public static final int FIELD_TICKS = 80;
    public static final int PULSE_TICKS = 20;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", RANGE,
            "radius", RADIUS,
            "windupSeconds", WINDUP_TICKS / 20.0,
            "fieldSeconds", FIELD_TICKS / 20.0,
            "pulseCount", FIELD_TICKS / PULSE_TICKS);
    }

    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 45, 20.7f, .25f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 23, .25f).segment("skill.myriad.damage.segment.pulse", FIELD_TICKS / PULSE_TICKS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public ConvergingChorusSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(WINDUP_TICKS,0,()->{WarSpiritHandCues.cast(c);AbilityVisuals.field(c,at,RADIUS,FIELD_TICKS);c.repeat(FIELD_TICKS,PULSE_TICKS,i->{for(var ally:c.allies(at,RADIUS))c.heal(ally,HEAL.value(c));c.pulse(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());});});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,RADIUS,WINDUP_TICKS);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void windupPose(Cast c,boolean winding){WarSpiritHandCues.chorus(c,winding);}
}

