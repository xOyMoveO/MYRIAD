package com.myriad.character.skills.yingxiao.rework;

import com.myriad.game.ModState;
import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
import java.util.WeakHashMap;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Confirmed vanilla attacks only. Never creates damage, cooldown, motion or an animation. */
public final class NightBasicVisuals {
    private record Chain(int next,long tick){}
    private static final WeakHashMap<ServerPlayer,Chain> CHAINS=new WeakHashMap<>();
    public static final int STAB=301,HAMMER=302,CUT=303,HIT=304;
    private NightBasicVisuals(){}
    public static boolean eligible(ServerPlayer p){
        return ModState.isEnabled()&&p.isAlive()&&!p.isSpectator()
                &&"yingxiao".equals(PlayerDataProvider.get(p).getCharacterId())
                &&p.getMainHandItem().is(ModItems.YINGCI.get());
    }
    public static AbilityVfxPayload swingRecipe(int owner,Vec3 eyes,Vec3 direction,int stage,long tick){
        Vec3 f=direction.normalize();
        // In front and below the eye, not a full-screen flare at the camera origin.
        Vec3 at=eyes.add(f.scale(1.15)).add(0,-.43,0);
        return new AbilityVfxPayload(owner,-1,0,2,ARC,at,at.add(f),.52f,STAB+Math.floorMod(stage,3),6,tick,false);
    }
    public static AbilityVfxPayload hitRecipe(int owner,Vec3 contact,Vec3 direction,long tick){
        return new AbilityVfxPayload(owner,-1,0,2,BURST,contact,contact.add(direction.normalize()),.38f,HIT,6,tick,false);
    }
    /** Called only after the authoritative vanilla target/readiness checks succeeded. */
    public static void swing(ServerPlayer p){
        if(!eligible(p))return;
        long now=p.level().getGameTime();var old=CHAINS.get(p);
        if(old!=null&&old.tick==now)return;
        double window=Math.clamp(p.getCurrentItemAttackStrengthDelay()*.7,6,14)+28;
        int stage=old==null||now-old.tick>window?0:old.next;
        CHAINS.put(p,new Chain((stage+1)%3,now));
        send(p,swingRecipe(p.getId(),p.getEyePosition(),p.getLookAngle(),stage,now),null);
    }
    /** Post-mitigation life/shield loss, not an incoming roll or a passive proc. */
    public static void confirmed(ServerPlayer p,LivingEntity target){
        if(!eligible(p))return;
        Vec3 center=target.getBoundingBox().getCenter();
        Vec3 normal=p.getEyePosition().subtract(center).normalize();
        double surface=Math.min(.32,Math.max(target.getBbWidth()*.38,.12));
        Vec3 at=center.add(normal.scale(surface));
        send(p,hitRecipe(p.getId(),at,p.getLookAngle(),p.level().getGameTime()),target);
    }
    private static void send(ServerPlayer owner,AbilityVfxPayload packet,LivingEntity target){
        for(var viewer:owner.level().players()){
            if(viewer.position().distanceToSqr(packet.from())>64*64)continue;
            if(viewer!=owner&&!VisionSystem.canSee(viewer,owner))continue;
            if(target!=null&&!VisionSystem.canSee(viewer,target))continue;
            PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
    public static void clear(UUID id){CHAINS.keySet().removeIf(p->p.getUUID().equals(id));}
}
