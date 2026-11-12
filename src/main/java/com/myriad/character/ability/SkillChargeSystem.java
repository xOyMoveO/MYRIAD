package com.myriad.character.ability;

import com.myriad.Myriad;
import com.myriad.game.ModState;
import com.myriad.network.SkillChargePayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared immobilisation only for explicitly registered stationary skill windups. */
@EventBusSubscriber(modid=Myriad.MODID)
public final class SkillChargeSystem {
    private static final Identifier LOCK=Identifier.fromNamespaceAndPath("myriad","skill_charge_lock");
    private record Charge(ServerPlayer player,String role,net.minecraft.server.level.ServerLevel level,String skill,long until,float walkFactor){}
    private static final Map<UUID,Charge> ACTIVE=new HashMap<>();
    private SkillChargeSystem(){}
    public static void start(ServerPlayer p,String skill,int ticks){
        start(p,skill,ticks,0);
    }
    public static void start(ServerPlayer p,String skill,int ticks,float walkFactor){
        walkFactor=Float.isFinite(walkFactor)?Math.clamp(walkFactor,0,1):0;
        clear(p);if(ticks<=0||!ModState.isEnabled())return;
        long now=p.level().getGameTime();
        ACTIVE.put(p.getUUID(),new Charge(p,PlayerDataProvider.get(p).getCharacterId(),p.level(),skill,now+ticks,walkFactor));
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.addTransientModifier(new AttributeModifier(LOCK,walkFactor-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if(walkFactor==0)freeze(p);else p.setSprinting(false);
        PacketDistributor.sendToPlayer(p,new SkillChargePayload(skill,ticks,now,walkFactor));
    }
    public static boolean active(ServerPlayer p){
        Charge c=ACTIVE.get(p.getUUID());return c!=null&&c.player==p&&c.level==p.level()&&c.until>p.level().getGameTime()
                &&p.isAlive()&&!p.isSpectator()&&ModState.isEnabled()
                &&java.util.Objects.equals(c.role,PlayerDataProvider.get(p).getCharacterId());
    }
    private static void freeze(ServerPlayer p){
        // Stationary casting is not knockback immunity; explicit skill impulses keep ownership.
        if(com.myriad.combat.KnockbackBuffer.recentlyForced(p))return;
        Vec3 velocity=p.getDeltaMovement();p.setDeltaMovement(0,Math.min(0,velocity.y),0);p.setSprinting(false);p.hurtMarked=true;
    }
    public static void clear(ServerPlayer p){
        Charge old=ACTIVE.remove(p.getUUID());
        if(old!=null){removeLock(old.player);PacketDistributor.sendToPlayer(old.player,new SkillChargePayload("",0,old.player.level().getGameTime()));}
        removeLock(p);
    }
    public static void clear(UUID id){var c=ACTIVE.get(id);if(c!=null)clear(c.player);}
    private static void removeLock(ServerPlayer p){var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(LOCK);}
    @SubscribeEvent public static void beforeMove(PlayerTickEvent.Pre event){
        if(event.getEntity() instanceof ServerPlayer p&&ACTIVE.containsKey(p.getUUID())){
            if(!active(p))clear(p);else if(ACTIVE.get(p.getUUID()).walkFactor==0)freeze(p);else p.setSprinting(false);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
}
