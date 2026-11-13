package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import com.myriad.character.ability.AbilityTargeting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class FlameStepSkill extends AbilitySkill {
    public static final double DISTANCE=8;
    public FlameStepSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),.8);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;} @Override public void release(Cast c){
        var delta=c.forward(DISTANCE);
        boolean begins=AbilityTargeting.clearSweep(c.player,delta.scale(1.0/6));
        if(begins){FlameStepVfx.follow(c,0);FlameStepAudio.start(c);}
        c.actionDash(delta,6,1.2,(t,first)->c.damage(t,c.value(35,12,.4f),PHYSICAL),()->{
            if(!begins)return;
            if(!AbilityRuntime.valid(c.player)||c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0){AbilityVisuals.cancelSkill(c);return;}
            // Cosmetic finish only. The dash's existing once-per-target damage remains unchanged.
            var from=c.player.getEyePosition();
            AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.ARC,from,from.add(delta.normalize()),2.2,1,8,false);
            FlameStepAudio.finish(c);
        },false,false);
        if(begins)c.whileActive(10,age->{
            if(!AbilityRuntime.moving(c.player))return false;
            if(age>0&&age%2==0)FlameStepVfx.follow(c,2);
            return true;
        });
    }
}
