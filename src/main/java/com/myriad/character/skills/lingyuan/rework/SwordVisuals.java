package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Lingyuan's authored cues only. No damage, costs, collision or movement. */
public final class SwordVisuals {
    private SwordVisuals() {}
    static Vec3 passiveSlot(int blade){double a=blade*Math.PI/2;return new Vec3(Math.cos(a)*.8,1.4,Math.sin(a)*.8);}
    public static void passiveBlades(Cast c,int count){
        if(count>0&&c.now()%6==0)AbilityVisuals.following(c,SHIELD,c.player,.8,Math.min(4,count),7);
    }
    public static void resonance(Cast c,int stacks){
        if(stacks>0&&c.now()%5==0)AbilityVisuals.following(c,SHIELD,c.player,.8,Math.min(3,stacks),6);
    }
    static void clearResonance(Cast c){AbilityVisuals.following(c,SHIELD,c.player,.8,0,1);}
    public static void dash(Cast c){
        Vec3 at=c.player.position().add(0,.72,0);
        AbilityVisuals.emit(c,ARC,at,at.add(c.forward(1)),.65,80,5,false);
    }
    static void dashHit(Cast c,Vec3 at){AbilityVisuals.emit(c,BURST,at,at.add(c.forward(1)),.68,0,7,false);}
    public static void mantle(Cast c){
        var generation=c.memory("mantleArt");int token=++generation.count;
        if(!c.ownShield(c.player))return;
        AbilityVisuals.following(c,WALL,c.player,1.1,0,5);
        c.whileActive(80,age->{
            if(token!=generation.count||!c.ownShield(c.player))return false;
            if(age!=0&&age%4==0)AbilityVisuals.following(c,WALL,c.player,1.1,c.memory().until>c.now()?1:2,5);
            return true;
        });
    }
    public static void volley(Cast c){
        AbilityVisuals.emit(c,SEAL,c.player.position().add(0,1.0,0),c.player.position().add(c.forward(1)).add(0,1.0,0),.8,360,9,false);
    }
    public static void array(Cast c){AbilityVisuals.field(c,c.point,4,80);}
    static void arrayPulse(Cast c,Vec3 at,int round){
        Vec3 ground=at.add(0,.075,0);
        AbilityVisuals.emit(c,BURST,ground,ground.add(c.forward(1)),4,round,10,false);
    }
    public static void rain(Cast c){AbilityVisuals.field(c,c.point,10,120);}
    public static void rainPulse(Cast c,Vec3 at,int round){
        // Always show a small shower, including an empty field; authoritative damage stays in the skill.
        for(int i=0;i<3;i++){
            double a=(i/3.0+round*.137)*Math.PI*2,r=2.2+(round%3)*1.4;
            Vec3 feet=at.add(Math.cos(a)*r,.12,Math.sin(a)*r);
            AbilityVisuals.emit(c,PILLAR,feet.add(-Math.cos(a)*1.6,4.2,-Math.sin(a)*1.6),feet,.55,round,9,false);
        }
    }
    public static void heaven(Cast c){
        Vec3 from=c.player.position(),end=from.add(c.forward(HeavenSwordSkill.REACH));
        AbilityVisuals.emit(c,CHARGE,from,end,HeavenSwordSkill.WIDTH/2,0,4,false);
        c.whileActive(HeavenSwordSkill.CHARGE_TICKS,age->{
            if(age>=HeavenSwordSkill.CHARGE_TICKS)return false;
            if(!AbilityRuntime.channeling(c.player)){AbilityVisuals.cancelSkill(c);return false;}
            if(age>0&&age%3==0){
                Vec3 grounded=new Vec3(from.x,c.player.getY(),from.z);
                AbilityVisuals.emit(c,CHARGE,grounded,grounded.add(end.subtract(from)),HeavenSwordSkill.WIDTH/2,age*360.0/HeavenSwordSkill.CHARGE_TICKS,4,false);
            }
            return true;});
    }
    public static void heavenRelease(Cast c,Vec3 origin,Vec3 forward){
        AbilityVisuals.cancelSkill(c);
        AbilityVisuals.emit(c,ARC,origin,origin.add(forward.scale(HeavenSwordSkill.REACH)),HeavenSwordSkill.WIDTH/2,110,14,false);
        c.whileActive(HeavenSwordSkill.CHOP_TICKS,age->{
            if(age>=HeavenSwordSkill.CHOP_TICKS)return false;
            if(!AbilityRuntime.channeling(c.player)){AbilityVisuals.cancelSkill(c);return false;}
            return true;});
        c.player.level().playSound(null,c.player.blockPosition(),com.myriad.registry.ModSounds.LINGYUAN_HEAVEN_SWEEP.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,.95f,1f);
    }
    public static void heavenImpact(Cast c,Vec3 origin,Vec3 forward){
        AbilityVisuals.emit(c,BEAM,origin,origin.add(forward.scale(HeavenSwordSkill.REACH)),HeavenSwordSkill.WIDTH/2,0,12,false);
        c.player.level().playSound(null,net.minecraft.core.BlockPos.containing(origin.add(forward.scale(7.5))),
                com.myriad.registry.ModSounds.impact(com.myriad.combat.CombatImpactProfile.STRUCTURE),
                net.minecraft.sounds.SoundSource.PLAYERS,.9f,.7f);
    }
}
