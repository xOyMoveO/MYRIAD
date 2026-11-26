package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.registry.ModSounds.Night.*;

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
public final class ShadowReturnSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(55, 18, .65f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public static final double DASH_DISTANCE=7;
    public static final int DASH_TICKS=4;
    @Override public boolean hasOwnCastSound() { return true; }

    public ShadowReturnSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.returnAnchor(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.RETURN,9);}
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return DASH_DISTANCE;}
 @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void dashPose(Cast c, DashPose phase, Vec3 direction) {
        if (phase == DashPose.START) NightAudio.play(c, c.player.position(), OUT);
    }

 @Override public void release(Cast c){var m=c.memory();m.point=c.player.position();m.until=c.now()+60;c.buff(getId()+"_recast",60);
 c.actionDash(c.point.subtract(c.player.position()),DASH_TICKS,1.2,(t,first)->c.damage(t,DAMAGE.value(c),DAMAGE.kind()),()->{},false,false);}
 @Override public boolean recastAvailable(Cast c){return c.memory().point!=null&&c.memory().until>c.now();}
 @Override public void recast(Cast c){var m=c.memory();if(c.safe(m.point)){
 com.myriad.character.ability.AbilityVisuals.beam(c,c.player.position().add(0,.45,0),m.point.add(0,.45,0),.15);
 com.myriad.character.ability.AbilityVisuals.burst(c,m.point,.6);
 Vec3 from = c.player.position();
 var departing = NightAudio.listeners(c, from);
 c.player.teleportTo(m.point.x,m.point.y,m.point.z);
 if (c.player.position().distanceToSqr(m.point) < 1.0e-6) {
     NightAudio.send(departing, from, RETURN_VANISH);
     NightAudio.play(c, m.point, RETURN_ARRIVE);
 }
 m.until=0;m.point=null;c.buff(getId()+"_recast",0);
 NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.RETURN,8);}}
}
