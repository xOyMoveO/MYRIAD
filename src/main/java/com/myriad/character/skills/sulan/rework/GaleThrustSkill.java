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
public final class GaleThrustSkill extends AbilitySkill {
    public static final int DASH_TICKS = 7;
    public static final double RANGE = 6, PATH_RADIUS = 1.2;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(50, 18.75f, .6f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public GaleThrustSkill(Definition definition){super(definition);}
    @Override public void dashPose(Cast c,DashPose phase,Vec3 direction){
        WindWalkingBladeSkill.trackSkillDash(c,phase);
        WindCastCues.dash(c,phase,direction);
    }
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.arc(c,c.player.position(),1.4,55);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;} @Override public void release(Cast c){
 c.actionDash(c.forward(RANGE),DASH_TICKS,PATH_RADIUS,(t,first)->{
     if(c.damage(t,DAMAGE.value(c),DAMAGE.kind()))WindVisuals.cut(c,t,.62,55);
 },()->com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),.68),false,false);}
}
