package com.myriad.character.skills.yike;

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
        final Vec3[] pos={c.player.getEyePosition().add(dir.scale(.45))};final double[] distance={0};final long[] last={c.now()};
        final int natural=(int)Math.ceil(range/(blocksPerSecond/20));
        c.whileActive(2*natural+1,age->{
            long now=c.now();if(now<=last[0])return true;last[0]=now;
            if(age>2*natural){finish(c,visual,flight);return false;}
            double rate=AbilityRuntime.projectileRate(c.player,flight,pos[0],now),step=Math.min(range-distance[0],blocksPerSecond/20*rate);
            Vec3 next=pos[0].add(dir.scale(step));
            Vec3 wind=WindWallSystem.firstEnemyHit(c.player,pos[0],next);
            if(wind!=null){finish(c,visual,flight);YikeVfx.event(c,"dissipate",wind,wind,.25f,4);return false;}
            var wall=c.player.level().clip(new ClipContext(pos[0],next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
            boolean blocked=wall.getType()!=HitResult.Type.MISS;
            if(blocked)next=wall.getLocation();final Vec3 to=next,from=pos[0];
            var target=c.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.35),t->SkillTargets.enemy(c.player,t)
                &&t.getBoundingBox().inflate(.35).clip(from,to).isPresent()).stream()
                .min(Comparator.comparingDouble(t->t.getBoundingBox().inflate(.35).clip(from,to).orElse(to).distanceToSqr(from))).orElse(null);
            if(target!=null){next=target.getBoundingBox().inflate(.35).clip(from,to).orElse(to);YikeVfx.flight(c,from,next,false,visual);explode(c,next,radius,damage,false);finish(c,visual,flight);return false;}
            YikeVfx.flight(c,from,next,false,visual);distance[0]+=from.distanceTo(next);pos[0]=next;
            if(blocked){Vec3 outside=next.add(Vec3.atLowerCornerOf(wall.getDirection().getUnitVec3i()).scale(.035));explode(c,outside,radius,damage,false);finish(c,visual,flight);return false;}
            if(distance[0]+1E-7>=range){explode(c,next,radius,damage,false);finish(c,visual,flight);return false;}return true;
        },()->finish(c,visual,flight));
    }
    public static void heavy(Cast c,Vec3 ground,float damage){
        Vec3 begin=c.player.getEyePosition().add(c.player.getLookAngle().scale(.4)),end=ground.add(0,.10,0);
        double duration=Math.max(8,begin.subtract(end).horizontalDistance()/32*20),height=Math.max(2,begin.subtract(end).horizontalDistance()*.18);
        UUID flight=UUID.randomUUID();int visual=c.newVisualInstance();double[] progress={0};Vec3[] pos={begin};long[] last={c.now()};
        YikeAudio.play(c.player,"heavy_flight",begin,.55f,.95f);
        // Local slowing can extend flight, so the actual landing warning is refreshed along the real flight.
        c.whileActive((int)Math.ceil(2*duration)+1,age->{
            if(c.now()<=last[0])return true;last[0]=c.now();
            if(age>2*duration){finish(c,visual,flight);YikeVfx.cancel(c.player,6);return false;}
            progress[0]+=AbilityRuntime.projectileRate(c.player,flight,pos[0],c.now());double t=Math.min(1,progress[0]/duration);
            Vec3 next=begin.lerp(end,t).add(0,4*height*t*(1-t),0);
            var wall=c.player.level().clip(new ClipContext(pos[0],next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
            Vec3 wind=WindWallSystem.firstEnemyHit(c.player,pos[0],next);
            if(wall.getType()!=HitResult.Type.MISS||wind!=null){
                Vec3 at=wind==null?wall.getLocation():wind;YikeVfx.event(c,"dissipate",at,at,.6f,5);finish(c,visual,flight);YikeVfx.cancel(c.player,6);return false;
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
