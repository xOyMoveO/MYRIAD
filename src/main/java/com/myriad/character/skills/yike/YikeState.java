package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.game.VisionSystem;
import com.myriad.network.YikeStatePayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** A confirmed pose/sight snapshot. These values are presentation, never client authority. */
public final class YikeState {
    private static final Set<UUID> BEAM_CHARGE=new HashSet<>();
    private YikeState(){}
    public static boolean beamCharging(ServerPlayer p){return BEAM_CHARGE.contains(p.getUUID());}
    public static void chargingBeam(ServerPlayer p,boolean b){if(b)BEAM_CHARGE.add(p.getUUID());else BEAM_CHARGE.remove(p.getUUID());}
    public static void sync(ServerPlayer p){
        if(beamCharging(p)&&!SkillChargeSystem.active(p))BEAM_CHARGE.remove(p.getUUID());
        int mode=PrismaticBeam.active(p)?3:YikeStance.active(p)?YikeStance.mobile(p)?2:1:0;
        var winding=AbilityRuntime.winding(p);var skill=winding==null?null:winding.skill;
        YikeVfx.preparingFrame(winding);
        int windup=skill==null?-1:skill.index();
        if(beamCharging(p))windup=7;
        var packet=new YikeStatePayload(p.getId(),mode,YikeStance.shots(p),mode==3?PrismaticBeam.remaining(p):YikeStance.remaining(p),
            YikeStance.nextShotTicks(p),windup,PrismaticBeam.jumps(p),mode==3?PrismaticBeam.aim(p):YikeStance.aim(p),
            SteadyBarrier.cooldown(p),SteadyBarrier.ready(p),winding==null?0:winding.windupProgress(),p.level().getGameTime());
        for(var viewer:p.level().players()){
            if(viewer==p||viewer.position().distanceToSqr(p.position())<64*64&&VisionSystem.canSee(viewer,p))PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
    public static void clear(ServerPlayer p){BEAM_CHARGE.remove(p.getUUID());
        var packet=new YikeStatePayload(p.getId(),0,0,0,0,-1,0,p.getLookAngle(),0,false,0,p.level().getGameTime());
        for(var viewer:p.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
}
