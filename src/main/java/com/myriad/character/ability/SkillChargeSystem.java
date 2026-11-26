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
import com.myriad.network.Packets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared immobilisation only for explicitly registered stationary skill windups. */
public final class SkillChargeSystem {
    private static final Identifier LOCK=Identifier.fromNamespaceAndPath("myriad","skill_charge_lock");
    private record Charge(ServerPlayer player,String role,net.minecraft.server.level.ServerLevel level,String skill, int total, long until, float walkFactor, SkillChargePayload sample) {}
    private static final Map<UUID,Charge> ACTIVE=new HashMap<>();
    private SkillChargeSystem(){}
    public static void start(ServerPlayer p,String skill,int ticks){
        start(p,skill,ticks,0);
    }
    public static void start(ServerPlayer p,String skill,int ticks,float walkFactor){
        walkFactor=Float.isFinite(walkFactor)?Math.clamp(walkFactor,0,1):0;
        clear(p);if(ticks<=0||!ModState.isEnabled())return;
        long now=p.level().getGameTime();
        var sample = new SkillChargePayload(skill, ticks, now, walkFactor);
        ACTIVE.put(p.getUUID(), new Charge(p, PlayerDataProvider.get(p).getCharacterId(), p.level(), skill, ticks, now+ticks, walkFactor, sample));
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.addTransientModifier(new AttributeModifier(LOCK,walkFactor-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if(walkFactor==0)freeze(p);else p.setSprinting(false);
        Packets.sendToPlayer(p, sample);
    }
    /** Keep an explicitly local-time windup locked without restarting its presentation. */
    public static void extend(ServerPlayer player, int remainingTicks, double completed, double rate) {
        Charge c = ACTIVE.get(player.getUUID());
        if (c == null) return;
        long now = player.level().getGameTime();
        var sample = c.sample;
        double expected = sample.completed() + (now - sample.started()) * sample.rate();
        if (Math.abs(completed - expected) > .05 || Math.abs(rate - sample.rate()) > .0001) {
            sample = new SkillChargePayload(c.skill, c.total, now, c.walkFactor, (float)completed, (float)rate);
            Packets.sendToPlayer(player, sample);
        }
        ACTIVE.put(player.getUUID(), new Charge(player, c.role, c.level, c.skill, c.total,
                now + remainingTicks, c.walkFactor, sample));
    }
    public static boolean active(ServerPlayer p){
        Charge c=ACTIVE.get(p.getUUID());return c!=null&&c.player==p&&c.level==p.level()&&c.until>p.level().getGameTime()
                &&p.isAlive()&&!p.isSpectator()&&ModState.isEnabled()
                &&java.util.Objects.equals(c.role,PlayerDataProvider.get(p).getCharacterId());
    }
    public record View(String skill, int ticks, float walkFactor) {}
    public static View view(ServerPlayer player) {
        if (!active(player)) return null;
        var charge = ACTIVE.get(player.getUUID());
        return new View(charge.skill, (int)Math.min(Integer.MAX_VALUE, charge.until - player.level().getGameTime()), charge.walkFactor);
    }
    private static void freeze(ServerPlayer p){
        // Stationary casting is not knockback immunity; explicit skill impulses keep ownership.
        if(com.myriad.combat.KnockbackBuffer.recentlyForced(p))return;
        Vec3 velocity=p.getDeltaMovement();p.setDeltaMovement(0,Math.min(0,velocity.y),0);p.setSprinting(false);p.syncVelocity=true;
    }
    public static void clear(ServerPlayer p){
        Charge old=ACTIVE.remove(p.getUUID());
        if(old!=null){removeLock(old.player);Packets.sendToPlayer(old.player,new SkillChargePayload("",0,old.player.level().getGameTime()));}
        removeLock(p);
    }
    public static void clear(UUID id){var c=ACTIVE.get(id);if(c!=null)clear(c.player);}
    private static void removeLock(ServerPlayer p){var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(LOCK);}
    public static void beforeMove(net.minecraft.world.entity.player.Player subject){
        if(subject instanceof ServerPlayer p&&ACTIVE.containsKey(p.getUUID())){
            if(!active(p))clear(p);else if(ACTIVE.get(p.getUUID()).walkFactor==0)freeze(p);else p.setSprinting(false);
        }
    }
    public static void logout(net.minecraft.world.entity.player.Player subject){if(subject instanceof ServerPlayer p)clear(p);}
}
