package com.myriad.character.skills.keheng;

import com.myriad.character.ability.*;
import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.skills.keheng.KehengVfx.*;

/** Real event stages and bounded refresh TTLs; no damage or action timing lives here. */
public final class KehengVisuals {
    private KehengVisuals(){}
    public static AbilityVfxPayload recipe(AbilityRuntime.Cast c,int kind,Vec3 at,Vec3 end,double radius,double stage,int life,int anchor,int instance){
        return new AbilityVfxPayload(c.player.getId(),anchor,THEME,c.skill.index(),kind,at,end,(float)radius,(float)stage,life,c.now(),false,instance);
    }
    public static void send(AbilityRuntime.Cast c,AbilityVfxPayload packet){
        if(!packet.valid())return;
        for(var viewer:c.player.level().players()){
            if(viewer.position().distanceToSqr(packet.from())>Math.pow(64+packet.radius(),2)&&viewer.position().distanceToSqr(packet.to())>Math.pow(64+packet.radius(),2))continue;
            var anchor=c.player.level().getEntity(packet.anchor());
            if(anchor!=null&&!VisionSystem.canSee(viewer,anchor))continue;
            if(viewer==c.player||VisionSystem.canSee(viewer,c.player))PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
    public static void warning(AbilityRuntime.Cast c,Vec3 at,double radius,long elapsed,int instance){
        send(c,recipe(c,SEAL,at.add(0,.04,0),at.add(0,1,0),radius,WARNING+elapsed,5,-1,instance));
    }
    public static void field(AbilityRuntime.Cast c,Vec3 at,double radius,boolean moving,long elapsed,int instance){
        send(c,recipe(c,SEAL,at.add(0,.04,0),at.add(0,1,0),radius,Math.min(100,elapsed),5,moving?c.player.getId():-1,instance));
    }
    public static void corridor(AbilityRuntime.Cast c,Vec3 at,Vec3 end,int instance){
        send(c,recipe(c,CHARGE,at.add(0,.04,0),end.add(0,.04,0),1.5,0,5,-1,instance));
    }
    public static void penetrate(AbilityRuntime.Cast c,Vec3 from,Vec3 to){AbilityVisuals.emit(c,BEAM,from,to,1.5,0,6,false);}
    public static void impact(AbilityRuntime.Cast c,Vec3 at){AbilityVisuals.emit(c,BURST,at,at.add(c.player.getLookAngle()),.55,0,5,false);}
    public static void mark(AbilityRuntime.Cast c,LivingEntity target,int stacks,long remaining){
        var at=target.position().add(0,stacks==3?.9:0,0);
        send(c,recipe(c,stacks==3?BURST:SEAL,at,at.add(c.forward(1)),stacks==3?.45:.38,stacks,stacks==3?5:(int)Math.clamp(remaining,1,5),target.getId(),0));
    }
    public static void pulse(AbilityRuntime.Cast c,Vec3 at,double radius){AbilityVisuals.emit(c,ARC,at.add(0,.05,0),at.add(0,1,0),radius,0,5,false);}
    public static void finish(AbilityRuntime.Cast c,int instance){
        var packet=recipe(c,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,-1,instance);
        for(var viewer:c.player.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
    public static void end(AbilityRuntime.Cast c,Vec3 at,double radius,boolean natural,int instance,boolean clear){
        finish(c,instance);
        if(!clear&&c.skill.index()!=7)AbilityVisuals.emit(c,BURST,at.add(0,.04,0),at.add(0,1,0),radius,natural?NATURAL_END:INTERRUPTED_END,natural?6:4,false);
    }
    /** Refreshes supply the body; a real early break scatters, actual expiry only retires it. */
    public static void watchShield(AbilityRuntime.Cast c,int ticks){
        c.whileActive(ticks,age->{
            if(c.ownShield(c.player))return true;
            send(c,recipe(c,BURST,c.player.position(),c.player.position().add(c.forward(1)),.65,age<ticks?SHIELD_BREAK:SHIELD_EXPIRE,5,c.player.getId(),0));
            return false;
        });
    }
}
