package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;

/** A committed warning is independent of the character's later controls. */
public final class RefractionDenial extends AbilitySkill {
    public RefractionDenial(Definition d){super(d);}
    @Override public int inputRecoveryTicks(){return 0;}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 14;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 2.5;}
    @Override public boolean interruptPreparation(AbilityRuntime.Cast c){return AbilityRuntime.preparingSkill(c.player).equals("yike_rainbow_shell");}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return !PrismaticBeam.active(c.player);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        var at=c.point.add(0,.06,0);YikeStance.pauseForW(c.player);
        // Recovery owns movement only, not the independently placed warning.
        c.later(11,true,.5f,()->YikeStance.refreshMovement(c.player));YikeStance.refreshMovement(c.player);
        YikeVfx.event(c,"denial_warning",at,at,2.5f,11);YikeAudio.play(c.player,"warning",at,.4f,1.2f);
        c.later(11,false,()->{
            for(var target:YikeGeometry.sphere(c,at,2.5)){c.damage(target,c.value(70,18,.35f),DamageCalc.Kind.MAGIC);c.slow(target,.30f,25);}
            YikeVfx.event(c,"denial_burst",at,at,2.5f,9);YikeAudio.play(c.player,"impact",at,.65f,1.15f);
        });
    }
}
