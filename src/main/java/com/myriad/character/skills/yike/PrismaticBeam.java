package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.combat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import java.util.*;

/** Charge follows local action time; the six damage opportunities use real ticks only. */
public final class PrismaticBeam extends AbilitySkill {
    private static final class Beam {AbilityRuntime.Cast cast;Vec3 direction,origin;long start,last,impulse;int jumps;}
    private static final Map<UUID,Beam> BEAMS=new HashMap<>();
    public PrismaticBeam(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return active(c.player)||AbilityRuntime.preparingSkill(c.player).equals(getId())||SkillChargeSystem.active(c.player)&&YikeState.beamCharging(c.player);}
    @Override public boolean recastDuringChannel(){return true;}
    @Override public boolean recastWhileRooted(){return true;}
    @Override public void recast(AbilityRuntime.Cast c){AbilityRuntime.cancel(c.player);clear(c.player);}
    @Override public void release(AbilityRuntime.Cast c){
        YikeStance.exit(c.player,"ultimate");YikeState.chargingBeam(c.player,true);
        YikeVfx.event(c,"beam_charge",c.player.getEyePosition(),c.player.getEyePosition().add(c.player.getLookAngle().scale(2)),.625f,12);
        YikeAudio.play(c.player,"beam_charge",c.player.position(),.5f,1);
        c.windup(12,0,()->{
            YikeState.chargingBeam(c.player,false);var b=new Beam();b.cast=c;b.direction=c.player.getLookAngle().normalize();b.start=c.now();b.last=b.start;b.origin=c.player.position();b.impulse=KnockbackBuffer.impulseToken(c.player);
            BEAMS.put(c.player.getUUID(),b);SkillChargeSystem.start(c.player,getId(),61,0);
            YikeAudio.loop(c.player,true);
        });
    }
    public static boolean active(ServerPlayer p){return BEAMS.containsKey(p.getUUID());}
    public static Vec3 aim(ServerPlayer p){var b=BEAMS.get(p.getUUID());return b==null?p.getLookAngle():b.direction;}
    public static int remaining(ServerPlayer p){var b=BEAMS.get(p.getUUID());return b==null?0:(int)Math.max(0,60-(p.level().getGameTime()-b.start));}
    public static int jumps(ServerPlayer p){var b=BEAMS.get(p.getUUID());return b==null?0:b.jumps;}
    public static void tick(ServerPlayer p){
        var b=BEAMS.get(p.getUUID());if(b==null)return;long now=p.level().getGameTime();var c=b.cast;
        if(!AbilityRuntime.valid(p)||c.data.getBuff("g1_stun")>0||SilenceSystem.isSilenced(p)
                ||KnockbackBuffer.impulseToken(p)!=b.impulse||p.position().subtract(b.origin).lengthSqr()>.64){clear(p);return;}
        if(now<=b.last)return;b.last=now;b.direction=YikeGeometry.rotate(b.direction,p.getLookAngle(),Math.toRadians(45.0/20));
        Vec3 from=p.getEyePosition(),to=YikeGeometry.end(c,from,b.direction,22);long age=now-b.start;
        if(age>60){clear(p);return;}
        if(age>0&&age%10==0&&b.jumps<6){
            b.jumps++;
            for(var t:p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(from,to).inflate(.625),t->SkillTargets.enemy(p,t)
                    &&AbilityTargeting.unobstructedHit(p,t.getBoundingBox(),from,to,.625).isPresent())){
                c.damage(t,c.value(70,20,.25f),DamageCalc.Kind.MAGIC);YikeVfx.target(c,"beam_hit",t,.4f,4);
            }
            YikeAudio.play(p,"beam_tick",to,.25f,1+(b.jumps%2)*.06f);
        }
        YikeVfx.event(c,"beam",from,to,.625f,2);
        if(age==60)clear(p);
    }
    public static void clear(ServerPlayer p){
        var old=BEAMS.remove(p.getUUID());boolean charge=YikeState.beamCharging(p);YikeState.chargingBeam(p,false);
        if(old!=null||charge){SkillChargeSystem.clear(p);YikeVfx.cancel(p,7);YikeAudio.loop(p,false);if(old!=null)YikeAudio.play(p,"beam_end",p.position(),.25f,1);}
    }
}
