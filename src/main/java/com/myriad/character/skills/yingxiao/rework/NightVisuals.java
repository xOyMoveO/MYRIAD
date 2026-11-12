package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Night-only event composition. Each independent skill decides when these cues occur. */
public final class NightVisuals {
    private NightVisuals(){}
    public static void daggerRelease(Cast c){
        Vec3 at=c.player.getEyePosition().add(c.player.getLookAngle().scale(.9)).add(0,-.42,0);
        AbilityVisuals.emit(c,ARC,at,at.add(c.forward(1)),.42,70,6,false);
    }
    public static void vanish(Cast c){AbilityVisuals.burst(c,c.player.position().add(0,.3,0),.85);}
    public static void assault(Cast c){
        AbilityVisuals.arc(c,c.player.position(),.9,55);
        AbilityVisuals.burst(c,c.player.position().add(0,.3,0),.35);
    }
    public static void execution(Cast c,LivingEntity target){
        AbilityVisuals.following(c,SEAL,target,.9,ShadowExecutionSkill.WINDUP_TICKS);
        AbilityVisuals.emit(c,CHARGE,c.player.position(),c.player.position().add(c.forward(1)),.7,0,ShadowExecutionSkill.WINDUP_TICKS,false);
    }
    public static void executionHit(Cast c,LivingEntity target){
        Vec3 at=target.position().add(0,target.getBbHeight()*.48,0);
        AbilityVisuals.emit(c,ARC,at,at.add(c.forward(1)),.8,110,7,false);
        AbilityVisuals.emit(c,BURST,at,at.add(c.forward(1)),.72,0,9,false);
    }
    public static void returnAnchor(Cast c){
        var m=c.memory();
        c.whileActive(60,age->{if(m.until<=c.now()||m.point==null)return false;
            if(age%6==0)AbilityVisuals.emit(c,SEAL,m.point.add(0,.055,0),m.point.add(c.forward(1)),.65,0,8,false);
            return true;});
    }
    public static void shield(Cast c){
        c.whileActive(80,age->{if(!c.ownShield(c.player))return false;
            if(age%6==0)AbilityVisuals.following(c,SHIELD,c.player,.7,8);return true;});
    }
    public static void fizzle(Cast c,Vec3 point){AbilityVisuals.emit(c,BURST,point,point.add(c.forward(1)),.3,1,6,false);}
}
