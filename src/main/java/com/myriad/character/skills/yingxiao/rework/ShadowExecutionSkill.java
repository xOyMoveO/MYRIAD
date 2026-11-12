package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.SkillTargets;
import com.myriad.game.VisionSystem;
import com.myriad.network.NightSkillViewPayload;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.PHYSICAL;

/** Lock one hero, 0.8-second interruptible windup, supported behind-target blink and one stab. */
public final class ShadowExecutionSkill extends AbilitySkill {
    public static final int WINDUP_TICKS=16;
    public ShadowExecutionSkill(Definition definition){super(definition);}
    @Override public boolean canRelease(Cast c){
        var target=c.target(8,false);
        return c.data.getBuff("g1_root")==0&&target!=null&&c.hero(target)&&ShadowBehindLanding.find(c.player,target)!=null;
    }
    @Override public void release(Cast c){
        var target=c.target(8,false);if(target==null)return;
        NightVisuals.execution(c,target);
        NightSkillPresentation.play(c,NightSkillViewPayload.READY,WINDUP_TICKS);
        boolean[] resolved={false};
        c.later(WINDUP_TICKS,true,()->{
            resolved[0]=true;
            if(!target.isAlive()||target.isRemoved()||target.level()!=c.player.level()
                    ||!SkillTargets.enemy(c.player,target)||!VisionSystem.canSee(c.player,target)||target.isInvisibleTo(c.player)
                    ||target.distanceToSqr(c.player)>64||!c.player.hasLineOfSight(target)
                    ||c.data.getBuff("g1_root")>0||c.data.getBuff("g1_stun")>0){NightSkillPresentation.cancel(c);return;}
            Vec3 at=ShadowBehindLanding.find(c.player,target);
            if(at==null){NightSkillPresentation.cancel(c);NightVisuals.fizzle(c,c.player.position());return;}
            Vec3 from=c.player.position();Vec3 aim=target.getBoundingBox().getCenter().subtract(at.add(0,c.player.getEyeHeight(),0));
            float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.max(.001,aim.horizontalDistance())));
            // A genuine blink, not a dash and not a teleport fallback for a non-teleport ability.
            if(!c.player.teleportTo(c.player.level(),at.x,at.y,at.z,java.util.Set.of(),yaw,pitch,true)){
                NightSkillPresentation.cancel(c);return;
            }
            c.player.setDeltaMovement(Vec3.ZERO);c.player.hurtMarked=true;c.player.resetFallDistance();
            AbilityVisuals.burst(c,from.add(0,.4,0),.9);
            AbilityVisuals.burst(c,at.add(0,.35,0),.75);
            NightSkillPresentation.play(c,NightSkillViewPayload.STRIKE,10);
            c.damage(target,c.value(180,70,1.3f),PHYSICAL);
            NightVisuals.executionHit(c,target);
        });
        c.whileActive(WINDUP_TICKS,age->{
            if(resolved[0])return false;
            if(age>0&&!AbilityRuntime.channeling(c.player)){NightSkillPresentation.cancel(c);return false;}
            return true;
        });
    }
}
