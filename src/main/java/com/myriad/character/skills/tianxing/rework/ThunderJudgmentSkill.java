package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.combat.DamageCalc.Kind.PHYSICAL;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Interruptible blade wipe, followed by one 120-degree / eight-block thunder judgment. */
public final class ThunderJudgmentSkill extends AbilitySkill {
    public static final int WINDUP=12,STUN=16;
    public static final double RANGE=8,ANGLE=120;
    public ThunderJudgmentSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    public static boolean inside(Vec3 delta,Vec3 facing){
        if(delta.lengthSqr()>RANGE*RANGE+1E-7)return false;
        var flat=delta.multiply(1,0,1);
        return flat.lengthSqr()<1E-8||flat.normalize().dot(facing)>=.5-1E-7;
    }
    @Override public void present(Cast c){
        c.whileActive(WINDUP,age->{
            if(!AbilityRuntime.channeling(c.player))return false;
            if(age%3==0){var at=c.player.position().add(0,.07,0);AbilityVisuals.emit(c,CHARGE,at,at.add(c.forward(1)),RANGE,ANGLE,4,false);}
            return true;
        });
    }
    @Override public void release(Cast c){
        ThunderSkillPresentation.charged(c,WINDUP,JUDGMENT_READY,JUDGMENT_FIRE,()->{
            ThunderAudio.play(c,ThunderAudio.Cue.JUDGMENT);
            Vec3 origin=c.player.position(),facing=c.forward(1);
            AbilityVisuals.emit(c,ARC,origin.add(0,.14,0),origin.add(facing).add(0,.14,0),RANGE,ANGLE,12,false);
            // Shared enemies() excludes buildings/allies and requires LOS. No hidden centre-line-only stun.
            // Broad phase includes floating-point boundary rounding; inside() remains the exact sector filter.
            for(var target:c.enemies(origin,RANGE+1E-6))if(inside(target.position().subtract(origin),facing)){
                c.damage(target,c.value(200,80,1.5f),PHYSICAL);c.root(target,STUN,true);
            }
        });
    }
}
