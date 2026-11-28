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
public final class HeavenFireSeaSkill extends AbilitySkill {
    public static final int FIELD_TICKS = 80, PULSE_TICKS = 20;
    public static final double IMPACT_RADIUS = 5;

    private static final AbilityDamageFormula LANDING =
            AbilityDamageFormula.physicalFromSpell(140, 69, 1, .8f / (1 + 4 * .2f)).segment("skill.myriad.damage.segment.landing", 1);
    private static final AbilityDamageFormula PULSE =
            AbilityDamageFormula.physicalFromSpell(30, 13.8f, .2f, .8f * .2f / (1 + 4 * .2f)).segment("skill.myriad.damage.segment.pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(LANDING, PULSE);
    }

    public static final int FLIGHT_TICKS=18;
    public static final double RANGE=14,MIN_HEIGHT=5;
    /** Compensate a downhill landing so the takeoff-relative midpoint still reaches at least five blocks. */
    public static double arcHeight(Vec3 origin,Vec3 destination){return MIN_HEIGHT+Math.max(0,origin.y-destination.y)*.5;}
    public HeavenFireSeaSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,IMPACT_RADIUS,10);}
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return RANGE;} @Override public double previewRadius(Cast c){return IMPACT_RADIUS;}
 @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null
         &&com.myriad.character.ability.AbilityTargeting.clearArc(c.player,c.point,FLIGHT_TICKS,arcHeight(c.player.position(),c.point));}
 @Override public void release(Cast c){Vec3 at=c.point;FlameMajorAudio.seaJump(c);int gesture=FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.SEA_FLIGHT,32);
 boolean[] landedOnce={false};
 c.actionLeap(at,FLIGHT_TICKS,arcHeight(c.player.position(),at),true,()->{
 landedOnce[0]=true;FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.SEA_LAND,10,gesture);
 Vec3 landed=c.player.position();FlameMajorAudio.seaImpact(c,landed);com.myriad.character.ability.AbilityVisuals.field(c,landed,IMPACT_RADIUS,FIELD_TICKS);c.area(landed,IMPACT_RADIUS,LANDING.value(c),LANDING.kind());
 c.repeat(FIELD_TICKS,30,i->FlameMajorAudio.burning(c,landed));
         int[] lastPulse = {0};
        c.repeat(FIELD_TICKS, PULSE_TICKS, pulse -> {
            if (pulse <= lastPulse[0]) return;
            lastPulse[0] = pulse;
            c.pulse(landed, IMPACT_RADIUS, PULSE.value(c), PULSE.kind());
        });
    });
 c.whileActive(32,age->{if(landedOnce[0])return false;
 if(age>0&&!AbilityRuntime.moving(c.player)){FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.CANCEL,0,gesture);return false;}return true;});}
}
