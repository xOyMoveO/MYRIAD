package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;

/** The shell is paid at the muzzle, never when the Q input begins. */
public final class RainbowShell extends AbilitySkill {
    public RainbowShell(Definition d){super(d);}
    @Override public int commitWindupTicks(){return 5;}
    @Override public float windupWalkFactor(AbilityRuntime.Cast c){return .5f;}
    @Override public boolean cooldownReady(AbilityRuntime.Cast c){return YikeStance.active(c.player)||super.cooldownReady(c);}
    @Override public int cooldownTicks(AbilityRuntime.Cast c){return YikeStance.active(c.player)?0:super.cooldownTicks(c);}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.skillLevel(getId())>0&&!PrismaticBeam.active(c.player)&&YikeStance.canFire(c);}
    @Override public void preparing(AbilityRuntime.Cast c){YikeVfx.event(c,"muzzle_charge",c.player.getEyePosition(),c.player.getEyePosition().add(c.player.getLookAngle()),.35f,commitWindupTicks());}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        var p=c.player;var direction=YikeStance.active(p)?YikeStance.aim(p):p.getLookAngle();
        double range=YikeStance.shotRange(p),speed=YikeStance.shotSpeed(p),radius=YikeStance.shotRadius(p);
        float damage=c.value(95,18,.55f)*YikeStance.multiplier(p);
        boolean artillery=YikeStance.active(p);int remaining=YikeStance.shots(p);
        if(artillery)YikeStance.shotCommitted(c);
        YikeProjectiles.shell(c,direction,range,speed,radius,damage);
        YikeVfx.event(c,remaining==1&&artillery?"last_muzzle":"muzzle",p.getEyePosition(),p.getEyePosition().add(direction),.5f,5);
        YikeAudio.play(p,"shot_"+Math.floorMod(c.castId(),3),p.position(),.65f,1);
    }
}
