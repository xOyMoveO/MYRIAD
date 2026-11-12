package com.myriad.character.skills.fenjue.rework;

import com.myriad.combat.SkillTargets;
import com.myriad.game.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.BURST;

/** Cosmetic only: called after measured health/shield loss, before independent audio throttling. */
public final class FlameHitVisuals {
    public static final int NORMAL=347,CRITICAL=348,EMBER=349,LIFE=8;
    private static final WeakHashMap<ServerPlayer,Cadence> CADENCES=new WeakHashMap<>();
    private FlameHitVisuals(){}
    public static boolean eligible(ServerPlayer p,LivingEntity target,float effective){
        return ModState.isEnabled()&&p.isAlive()&&!p.isSpectator()&&Float.isFinite(effective)&&effective>0
                &&"fenjue".equals(PlayerDataProvider.get(p).getCharacterId())&&p.level()==target.level()
                &&p!=target&&!(target instanceof DefenseTowerEntity)&&!SkillTargets.ally(p,target);
    }
    /** Bounded per-owner cosmetic budget. Does not suppress any damage, proc, status or audio. */
    public static final class Cadence {
        private final LinkedHashMap<UUID,Long> targets=new LinkedHashMap<>();
        private long window=Long.MIN_VALUE;private int count;
        public boolean allow(UUID target,long now,boolean periodic){
            if(window==Long.MIN_VALUE||now<window||now-window>=3){if(now<window)targets.clear();window=now;count=0;}
            var last=targets.get(target);
            if(count>=4||last!=null&&now>=last&&now-last<(periodic?6:3))return false;
            if(targets.size()>=16&&!targets.containsKey(target))targets.remove(targets.keySet().iterator().next());
            targets.put(target,now);count++;return true;
        }
        public int tracked(){return targets.size();}
    }
    public static Vec3 contact(ServerPlayer p,LivingEntity target){
        Vec3 center=target.getBoundingBox().getCenter(),eye=p.getEyePosition();
        // Put the flash on the struck body's front surface, not buried inside its centre.
        return target.getBoundingBox().clip(eye,center).orElse(center)
                .add(eye.subtract(center).normalize().scale(.035));
    }
    public static AbilityVfxPayload recipe(int owner,Vec3 at,Vec3 direction,boolean critical,boolean periodic,long now){
        Vec3 f=direction.lengthSqr()<1E-9?new Vec3(0,0,1):direction.normalize();
        return new AbilityVfxPayload(owner,-1,2,0,BURST,at,at.add(f),periodic?.38f:critical?.82f:.64f,
                periodic?EMBER:critical?CRITICAL:NORMAL,LIFE,now,false);
    }
    public static void confirmed(ServerPlayer p,LivingEntity target,boolean critical,boolean periodic,float effective){
        if(!eligible(p,target,effective))return;
        Vec3 at=contact(p,target);if(!visible(p,target,at))return;
        if(!CADENCES.computeIfAbsent(p,ignored->new Cadence()).allow(target.getUUID(),p.level().getGameTime(),periodic))return;
        var packet=recipe(p.getId(),at,at.subtract(p.getEyePosition()),critical,periodic,p.level().getGameTime());
        PacketDistributor.sendToPlayer(p,packet);
        for(var viewer:p.level().players())if(viewer!=p&&viewer.position().distanceToSqr(at)<=48*48
                &&VisionSystem.canSee(viewer,p)&&visible(viewer,target,at))PacketDistributor.sendToPlayer(viewer,packet);
    }
    private static boolean visible(ServerPlayer viewer,LivingEntity target,Vec3 at){return VisionSystem.canSee(viewer,target)&&VisionSystem.canSeePosition(viewer,at);}
    public static void clear(ServerPlayer p){CADENCES.remove(p);}
}
