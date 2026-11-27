package com.myriad.character.skills.yike;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Independently clocked swept flights. Every termination has one explicit outcome. */
public final class YikeProjectiles {
    private YikeProjectiles(){}
    public static void shell(Cast c,Vec3 direction,double range,double blocksPerSecond,double radius,float damage){
        final Vec3 dir=direction.normalize();final UUID flight=UUID.randomUUID();final int visual=c.newVisualInstance();
        final Vec3 launchEye=c.player.getEyePosition();
        final Vec3[] pos={launchEye.add(dir.scale(.45))};final double[] distance={0};final long[] last={c.now()};
        final double speed=blocksPerSecond/20*HeroProjectileTuning.SPEED;
        final double padding=.35*HeroProjectileTuning.ENTITY_PADDING;
        final int natural=(int)Math.ceil(range/speed);
        c.whileActive(4*natural+1,age->{
            long now=c.now();if(now<=last[0])return true;last[0]=now;
            if(age>4*natural){finish(c,visual,flight);return false;}
            if (distance[0] == 0) {
                var muzzle=ProjectileBasicAttack.clip(c.player,launchEye,pos[0]);
                if (muzzle.blocked()) {
                    muzzle.showWindIntercept();
                    if (!muzzle.windBlocked()) {
                        Vec3 outside=muzzle.end().subtract(dir.scale(.035));
                        explode(c,outside,radius,damage,false);
                    }
                    finish(c,visual,flight);
                    return false;
                }
            }
            double remaining=range-distance[0];
            double rate=AbilityRuntime.projectileRate(c.player,flight,pos[0],now),step=Math.min(remaining,speed*rate);
            Vec3 from=pos[0], desired=from.add(dir.scale(step));
            var segment=ProjectileBasicAttack.clip(c.player,from,desired);
            Vec3 next=segment.end();
            var contact=c.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,next).inflate(padding),t->SkillTargets.enemy(c.player,t))
                .stream().map(t->segment.contact(t.getBoundingBox(),padding,remaining))
                .flatMap(Optional::stream).min(Comparator.comparingDouble(from::distanceToSqr));
            if(contact.isPresent()){next=contact.get();YikeVfx.flight(c,from,next,false,visual);explode(c,next,radius,damage,false);finish(c,visual,flight);return false;}
            if(segment.windBlocked()){segment.showWindIntercept();finish(c,visual,flight);YikeVfx.event(c,"dissipate",next,next,.25f,4);return false;}
            YikeVfx.flight(c,from,next,false,visual);distance[0]+=from.distanceTo(next);pos[0]=next;
            if(segment.blocked()){var wall=c.player.level().clip(new ClipContext(from,desired,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
                Vec3 outside=next.add(Vec3.atLowerCornerOf(wall.getDirection().getUnitVec3i()).scale(.035));explode(c,outside,radius,damage,false);finish(c,visual,flight);return false;}
            if(distance[0]+1E-7>=range){explode(c,next,radius,damage,false);finish(c,visual,flight);return false;}return true;
        },()->finish(c,visual,flight));
    }
    private static double arcHeight(Vec3 begin, Vec3 end) {
        return Math.max(2,begin.subtract(end).horizontalDistance()*.18);
    }
    static Vec3 launchAim(Cast c) {
        Vec3 begin=c.player.getEyePosition().add(c.player.getLookAngle().scale(.4));
        Vec3 end=c.point.add(0,.10,0);
        return end.subtract(begin).add(0,4*arcHeight(begin,end),0).normalize();
    }
    public static void heavy(Cast c,Vec3 ground,float damage){
        Vec3 begin=c.player.getEyePosition().add(c.player.getLookAngle().scale(.4)),end=ground.add(0,.10,0);
        double duration=Math.max(8,begin.subtract(end).horizontalDistance()/32*20)/HeroProjectileTuning.SPEED,height=arcHeight(begin,end);
        UUID flight=UUID.randomUUID();int visual=c.newVisualInstance();double[] progress={0};Vec3[] pos={begin};long[] last={c.now()};
        WeaponPosePayload.play(c.player, Move.HEAVY_FIRE, 9);
        YikeAudio.play(c.player,"heavy_flight",begin,.55f,.95f);
        // Local slowing can extend flight, so the actual landing warning is refreshed along the real flight.
        c.whileActive((int)Math.ceil(4*duration)+1,age->{
            if(c.now()<=last[0])return true;last[0]=c.now();
            if(age>4*duration){finish(c,visual,flight);YikeVfx.cancel(c.player,6);return false;}
            progress[0]+=AbilityRuntime.projectileRate(c.player,flight,pos[0],c.now());double t=Math.min(1,progress[0]/duration);
            Vec3 next=begin.lerp(end,t).add(0,4*height*t*(1-t),0);
            var segment=ProjectileBasicAttack.clip(c.player,pos[0],next);
            if(segment.blocked()){
                segment.showWindIntercept();
                Vec3 at=segment.end();YikeVfx.event(c,"dissipate",at,at,.6f,5);finish(c,visual,flight);YikeVfx.cancel(c.player,6);return false;
            }
            YikeVfx.flight(c,pos[0],next,true,visual);YikeVfx.event(c,"heavy_warning",ground.add(0,.06,0),ground.add(0,.06,0),4,3);pos[0]=next;
            if(t>=1){YikeVfx.cancel(c.player,6);explode(c,end,4,damage,true);finish(c,visual,flight);return false;}return true;
        },()->finish(c,visual,flight));
    }
    private static void finish(Cast c,int visual,UUID flight){ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);}
    private static void explode(Cast c,Vec3 center,double radius,float damage,boolean heavy){
        for(var t:YikeGeometry.sphere(c,center,radius))c.damage(t,damage,DamageCalc.Kind.MAGIC);
        YikeVfx.event(c,heavy?"heavy_impact":"impact",center,center,(float)radius,heavy?14:8);
        YikeAudio.play(c.player,heavy?"heavy_impact":"impact",center,heavy?.9f:.6f,1);
    }
}
