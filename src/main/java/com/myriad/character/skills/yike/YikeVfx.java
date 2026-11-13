package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.game.VisionSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Original prismatic recipes: explicit stages, owned flights and visibility-safe attached marks. */
public final class YikeVfx {
    public static final int THEME=12;
    public static final int BASIC=1,IMPACT=2,DENIAL_WARNING=3,DENIAL_BURST=4,FIXED=5,MOBILE=6,
        MUZZLE=7,HEAVY=8,HEAVY_WARNING=9,HEAVY_IMPACT=10,BEAM=11,BEAM_HIT=12,BEAM_CHARGE=13,
        MARK1=14,MARK2=15,MARK_BURST=16,BARRIER=17,DEPLOY=18,EXIT=19,DISSIPATE=20,LAST_MUZZLE=21;
    private YikeVfx(){}
    private static int signal(String name){return switch(name){
        case "basic_muzzle","basic_hit"->BASIC;case "impact"->IMPACT;case "denial_warning"->DENIAL_WARNING;case "denial_burst"->DENIAL_BURST;
        case "fixed"->FIXED;case "mobile"->MOBILE;case "muzzle"->MUZZLE;case "last_muzzle"->LAST_MUZZLE;
        case "heavy"->HEAVY;case "heavy_warning"->HEAVY_WARNING;case "heavy_impact"->HEAVY_IMPACT;
        case "beam"->BEAM;case "beam_hit"->BEAM_HIT;case "beam_charge"->BEAM_CHARGE;
        case "mark_1"->MARK1;case "mark_2"->MARK2;case "mark_burst"->MARK_BURST;case "barrier"->BARRIER;
        case "deploy"->DEPLOY;case "exit"->EXIT;default->DISSIPATE;};}
    public static void event(AbilityRuntime.Cast c,String name,Vec3 from,Vec3 to,float radius,int life){
        int signal=signal(name);if(signal==EXIT){cancel(c.player,c.skill.index());YikeAudio.play(c.player,"close",from,.4f,1);return;}
        int kind=signal==BEAM?AbilityVfxGeometry.BEAM:signal==DENIAL_WARNING||signal==HEAVY_WARNING?SEAL:signal==BARRIER?SHIELD:BURST;
        send(c.player,new AbilityVfxPayload(c.player.getId(),-1,THEME,c.skill.index(),kind,from,to,radius,signal,Math.clamp(life,1,120),c.now(),false));
        if(signal==FIXED||signal==MOBILE)YikeAudio.play(c.player,"deploy",from,.5f,signal==MOBILE?1.1f:1);
    }
    public static void target(AbilityRuntime.Cast c,String name,LivingEntity target,float radius,int life){
        var from=target.position().add(0,target.getBbHeight()*.55,0);
        send(c.player,new AbilityVfxPayload(c.player.getId(),target.getId(),THEME,c.skill.index(),SHIELD,from,from.add(c.player.getLookAngle()),radius,signal(name),life,c.now(),false));
    }
    public static void flight(AbilityRuntime.Cast c,Vec3 from,Vec3 to,boolean heavy,int instance){
        send(c.player,new AbilityVfxPayload(c.player.getId(),-1,THEME,c.skill.index(),FLIGHT,from,to,heavy?.36f:.14f,heavy?HEAVY:0,3,c.now(),false,instance));
    }
    public static void basicFlight(AbilityRuntime.Cast c,Vec3 from,Vec3 to,int instance){send(c.player,new AbilityVfxPayload(c.player.getId(),-1,THEME,2,FLIGHT,from,to,.065f,BASIC,2,c.now(),false,instance));}
    public static void finish(AbilityRuntime.Cast c,int instance){sendAll(c.player,new AbilityVfxPayload(c.player.getId(),-1,THEME,c.skill.index(),CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,c.now(),false,instance));}
    public static void cancel(ServerPlayer p,int index){sendAll(p,new AbilityVfxPayload(p.getId(),-1,THEME,index,CANCEL,Vec3.ZERO,Vec3.ZERO,0,1,1,p.level().getGameTime(),false));}
    private static void sendAll(ServerPlayer p,AbilityVfxPayload packet){for(var viewer:p.level().players())PacketDistributor.sendToPlayer(viewer,packet);}
    private static void send(ServerPlayer p,AbilityVfxPayload packet){
        if(!packet.valid())return;
        for(var viewer:p.level().players()){
            if(viewer.position().distanceToSqr(packet.from())>Math.pow(64+packet.radius(),2)&&viewer.position().distanceToSqr(packet.to())>Math.pow(64+packet.radius(),2))continue;
            if(viewer!=p&&!VisionSystem.canSeePosition(viewer,packet.from())&&!VisionSystem.canSeePosition(viewer,packet.to()))continue;
            if(packet.anchor()>=0&&p.level().getEntity(packet.anchor()) instanceof LivingEntity target&&!VisionSystem.canSee(viewer,target))continue;
            PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
}
