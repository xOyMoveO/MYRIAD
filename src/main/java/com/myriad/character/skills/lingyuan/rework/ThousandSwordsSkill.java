package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class ThousandSwordsSkill extends AbilitySkill {
    public static final int DURATION_TICKS=120, PULSE_TICKS=20;
    public static final double POINT_RANGE=14, RADIUS=10;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 55, 34.5f, .25f).segment("skill.myriad.damage.segment.pulse", DURATION_TICKS / PULSE_TICKS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("point_range", POINT_RANGE, "radius", RADIUS, "duration", DURATION_TICKS / 20.0);
    }

    public ThousandSwordsSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return POINT_RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void selectionPose(Cast c,boolean active){LingyuanHandCues.aim(c,active);}
@Override public void release(Cast c){LingyuanHandCues.release(c);Vec3 at=c.point;c.repeat(DURATION_TICKS,PULSE_TICKS,i->{c.pulse(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());SwordVisuals.rainPulse(c,at,i);for(var t:c.enemies(at,RADIUS).stream().limit(8).toList())AbilityVisuals.beam(c,t.position().add(0,3.8,0),t.position().add(0,.4,0),.15);});}
@Override public void present(Cast c){SwordVisuals.rain(c);}
}

