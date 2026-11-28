package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordArraySkill extends AbilitySkill {
    public static final int DURATION_TICKS=80, PULSE_TICKS=20, SLOW_TICKS=20;
    public static final double POINT_RANGE=14, RADIUS=4;
    public static final float SLOW=.25f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 12.5f, .25f).segment("skill.myriad.damage.segment.pulse", DURATION_TICKS / PULSE_TICKS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("point_range", POINT_RANGE, "radius", RADIUS, "duration", DURATION_TICKS / 20.0, "slow_pct", SLOW * 100);
    }

    public SwordArraySkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return POINT_RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void selectionPose(Cast c,boolean active){LingyuanHandCues.aim(c,active);}
@Override public void release(Cast c){LingyuanHandCues.release(c);Vec3 at=c.point;c.repeat(DURATION_TICKS,PULSE_TICKS,i->{c.pulse(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW,SLOW_TICKS);SwordVisuals.arrayPulse(c,at,i);});}
@Override public void present(Cast c){SwordVisuals.array(c);}
}

