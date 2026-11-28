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
public final class ThousandWindDashSkill extends AbilitySkill {
    public static final int WINDUP_TICKS = 10, DASH_TICKS = 14, SLOW_TICKS = 30;
    public static final double RANGE = 12, PATH_RADIUS = 1.2, IMPACT_RADIUS = 2.5;
    public static final float SLOW_RATE = .30f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(160, 81.25f, 1.2f, .8f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public ThousandWindDashSkill(Definition definition){super(definition);}
    @Override public void windupPose(Cast c,boolean winding){WindCastCues.windup(c,winding);}
    @Override public void dashPose(Cast c,DashPose phase,Vec3 direction){
        WindWalkingBladeSkill.trackSkillDash(c,phase);
        WindCastCues.dash(c,phase,direction);
    }
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.charging(c,WINDUP_TICKS,RANGE,.35);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
 @Override public void release(Cast c){c.windup(WINDUP_TICKS,0,()->c.actionDash(c.forward(RANGE),DASH_TICKS,PATH_RADIUS,(t,first)->{},
 ()->{Vec3 at=c.player.position();c.area(at,IMPACT_RADIUS,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(at,IMPACT_RADIUS))c.slow(t,SLOW_RATE,SLOW_TICKS);},true,true));}
}
