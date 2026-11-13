package com.myriad.character.ability;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.game.VisionSystem;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Skill-owned recipes, authoritative endpoints, client-side tessellation and fading. */
public final class AbilityVisuals {
    private AbilityVisuals() { }
    public static void cancel(net.minecraft.server.level.ServerPlayer player){
        var packet=new AbilityVfxPayload(player.getId(),-1,0,0,CANCEL,Vec3.ZERO,Vec3.ZERO,0,0,1,
                player.level().getGameTime(),false);
        for(var viewer:player.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
    /** Cancel this skill's visuals only; do not erase other active fields or passive blades. */
    public static void cancelSkill(Cast c){
        var packet=new AbilityVfxPayload(c.player.getId(),-1,theme(c),c.skill.index(),CANCEL,Vec3.ZERO,Vec3.ZERO,0,1,1,c.now(),false);
        for(var viewer:c.player.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
    /** End just this projectile body, including already queued visual frames. No hidden coordinates. */
    public static void finishFlight(Cast c,int instance){
        var packet=new AbilityVfxPayload(c.player.getId(),-1,theme(c),c.skill.index(),CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,c.now(),false,instance);
        for(var viewer:c.player.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
    private static int theme(Cast c){return switch(c.skill.role()){
        case "yingxiao"->0;case "tianxing"->1;case "fenjue"->2;case "xuanchen"->3;case "sulan"->4;
        case "lingyuan"->5;case "hanze"->6;case "qingluo"->7;case "chixiao"->8;case "yunsheng"->9;case "shuo"->10;case "keheng"->11;default->0;};}
    public static void emit(Cast c,int kind,Vec3 from,Vec3 to,double radius,double angle,int life,boolean personal) {
        send(c,kind,from,to,radius,angle,life,personal,-1);
    }
    public static void following(Cast c,int kind,net.minecraft.world.entity.LivingEntity target,double radius,int life){
        following(c,kind,target,radius,0,life);
    }
    /** Role-owned visual state, e.g. blade count or charge stage. Not a combat value. */
    public static void following(Cast c,int kind,net.minecraft.world.entity.LivingEntity target,double radius,double stage,int life){
        send(c,kind,target.position(),target.position().add(c.forward(1)),radius,stage,life,false,target.getId());
    }
    private static void send(Cast c,int kind,Vec3 from,Vec3 to,double radius,double angle,int life,boolean personal,int anchor) {
        send(c,kind,from,to,radius,angle,life,personal,anchor,0);
    }
    private static void send(Cast c,int kind,Vec3 from,Vec3 to,double radius,double angle,int life,boolean personal,int anchor,int instance) {
        if(theme(c)==10&&!com.myriad.character.skills.shuo.ShuoVisuals.allowsSkill(kind))return;
        var packet=new AbilityVfxPayload(c.player.getId(),anchor,theme(c),c.skill.index(),kind,from,to,
                (float)radius,(float)angle,life,c.now(),personal,instance);
        if(!packet.valid())return;
        if(personal){PacketDistributor.sendToPlayer(c.player,packet);return;}
        for(var viewer:c.player.level().players()) {
            if(viewer.position().distanceToSqr(from)>Math.pow(64+radius,2)
                    &&viewer.position().distanceToSqr(to)>Math.pow(64+radius,2))continue;
            var attached=anchor<0?null:c.player.level().getEntity(anchor);
            if(attached!=null&&!VisionSystem.canSee(viewer,attached))continue;
            if(viewer==c.player||VisionSystem.canSee(viewer,c.player))PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
    private static Vec3 facing(Cast c,Vec3 at){return at.add(c.forward(1));}
    public static void reticle(Cast c,Vec3 at,double radius){emit(c,RETICLE,at.add(0,.045,0),facing(c,at),radius,360,2,true);}
    public static void invalidReticle(Cast c,Vec3 at){emit(c,INVALID,at.add(0,.05,0),facing(c,at),.5,360,2,true);}
    public static void seal(Cast c,Vec3 at,double radius,boolean personal){emit(c,SEAL,at.add(0,.055,0),facing(c,at),radius,360,personal?2:5,personal);}
    public static void burst(Cast c,Vec3 at,double radius){emit(c,BURST,at.add(0,.06,0),facing(c,at),radius,360,9,false);}
    public static void dissipate(Cast c,Vec3 at){emit(c,BURST,at,facing(c,at),.3,1,6,false);}
    public static void arc(Cast c,Vec3 at,double reach,double degrees){emit(c,ARC,at.add(0,.65,0),facing(c,at).add(0,.65,0),reach,degrees,8,false);}
    public static void beam(Cast c,Vec3 from,Vec3 to,double width){emit(c,BEAM,from,to,width,0,6,false);}
    public static void pillar(Cast c,Vec3 at,double height){emit(c,PILLAR,at.add(0,.05,0),at.add(0,height,0),.75,0,9,false);}
    public static void shield(Cast c,Vec3 feet){emit(c,SHIELD,feet,feet.add(0,1.7,0),.6,360,12,false);}
    public static void shield(Cast c,net.minecraft.world.entity.LivingEntity target){
        send(c,SHIELD,target.position(),target.position().add(0,1.7,0),.6,360,12,false,target.getId());
    }
    /** Continue presentation only while this exact skill still owns a real shield. */
    public static void maintainShield(Cast c,net.minecraft.world.entity.LivingEntity target,int ticks){
        var state=c.memory("vfxShield/"+target.getUUID());int token=++state.count;
        c.whileActive(ticks,age->{if(token!=state.count||!target.isAlive()||target.level()!=c.player.level()||!c.ownShield(target))return false;
            if(age>0&&age%6==0)following(c,SHIELD,target,.65,8);return true;});
    }
    public static void portalAnchor(Cast c,Vec3 at){emit(c,SEAL,at.add(0,.055,0),facing(c,at),.9,0,8,false);}
    public static void motionTrail(Cast c,Vec3 from,Vec3 to){
        if(from.distanceToSqr(to)<.0025)return;
        emit(c,TRAIL,from.add(0,.4,0),to.add(0,.4,0),.18,0,7,false);
    }
    public static void flight(Cast c,Vec3 from,Vec3 to,int age,boolean returning){
        flight(c,from,to,age,returning,0);
    }
    public static void flight(Cast c,Vec3 from,Vec3 to,int age,boolean returning,int instance){
        send(c,FLIGHT,from,to,c.skill.role().equals("sulan")?.45:.22,returning?180:0,
                c.skill.role().equals("yingxiao")?2:3,false,-1,instance);
    }
    public static void warning(Cast c,Vec3 at,double radius,int ticks){
        emit(c,SEAL,at.add(0,.055,0),facing(c,at),radius,359,5,false);
        c.whileActive(ticks,age->{if(age%3==0){
            if(!c.skill.needsPoint()&&!AbilityRuntime.channeling(c.player))return false;
            double fill=Math.clamp(age/(double)Math.max(1,ticks),.03,1);
            emit(c,SEAL,at.add(0,.07,0),facing(c,at),radius*fill,359,4,false);
            emit(c,SEAL,at.add(0,.055,0),facing(c,at),radius,359,5,false);
        }return true;});
    }
    public static void charging(Cast c,int ticks,double range,double width){
        c.whileActive(ticks,age->{if(!AbilityRuntime.channeling(c.player))return false;
            if(age%3==0){Vec3 from=c.player.position().add(0,.1,0);
                emit(c,CHARGE,from,from.add(c.forward(range)),width,0,4,false);
            }return true;});
    }
    /** Event-confirmed impact; purely visual and rate-limited independently of damage. */
    public static void impact(Cast c,Vec3 at){
        long previous=c.player.getPersistentData().getLongOr("myriad_g1_vfx_hit",-10);
        if(previous<=c.now()&&c.now()-previous<3)return;
        c.player.getPersistentData().putLong("myriad_g1_vfx_hit",c.now());
        emit(c,BURST,at,at.add(c.forward(1)),.55,0,7,false);
    }
    public static void field(Cast c,Vec3 at,double radius,int ticks){
        emit(c,SEAL,at.add(0,.06,0),facing(c,at),radius,360,ticks,false);
    }
    public static void ready(Cast c){
        if(c.player.tickCount%12==0)emit(c,SEAL,c.player.position().add(0,.08,0),c.player.position().add(c.forward(1)),.42,360,13,true);
    }
    public static void wall(Cast c,Vec3 center,Vec3 normal,double halfWidth){
        emit(c,WALL,center,center.add(normal),halfWidth,0,5,false);
    }
}
