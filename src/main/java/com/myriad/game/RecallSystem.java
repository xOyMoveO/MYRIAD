package com.myriad.game;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import com.myriad.player.PlayerDataProvider;

/** Eight-second server-authoritative recall; no teleport destination is accepted from clients. */
public final class RecallSystem {
    public static final int DURATION = 160;
    private record Channel(Vec3 origin, long until, Faction faction) { }
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private RecallSystem() { }
    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && ModState.isEnabled()
                && p.level() == p.level().getServer().overworld()
                && (TrainingMode.isEnabled() || GameManager.get()!=null && GameManager.get().getPhase()==GameManager.Phase.PLAYING)
                && com.myriad.character.CharacterType.byId(PlayerDataProvider.get(p).getCharacterId()) != null;
    }
    public static void request(ServerPlayer p) {
        if (CHANNELS.containsKey(p.getUUID())) { cancel(p); return; }
        if (!eligible(p)||com.myriad.character.ability.SkillChargeSystem.active(p)) return;
        Faction faction = GameManager.get()==null ? PlayerDataProvider.get(p).getFaction() : GameManager.get().factionOf(p);
        if (LaneSystem.respawnPoint(faction)==null) {
            p.sendSystemMessage(Component.translatable("message.myriad.recall_no_base"));return;
        }
        CHANNELS.put(p.getUUID(),new Channel(p.position(),p.level().getGameTime()+DURATION,faction));
        PlayerDataProvider.get(p).setBuff("recall",DURATION);
        PlayerDataProvider.syncToClient(p);
    }
    public static void tick(ServerPlayer p) {
        Channel c=CHANNELS.get(p.getUUID());if(c==null)return;
        Faction faction=GameManager.get()==null?PlayerDataProvider.get(p).getFaction():GameManager.get().factionOf(p);
        if(!eligible(p)||faction!=c.faction||p.position().distanceToSqr(c.origin)>.0225){cancel(p);return;}
        long remaining=c.until-p.level().getGameTime();
        if(remaining<=0){
            var base=LaneSystem.respawnPoint(c.faction);
            cancel(p);
            if(base==null)return;
            Vec3 destination=Vec3.atBottomCenterOf(base).add(0,1,0);
            if(!p.level().noCollision(p,p.getBoundingBox().move(destination.subtract(p.position())))) {
                p.sendSystemMessage(Component.translatable("message.myriad.recall_blocked"));return;
            }
            p.teleportTo(destination.x,destination.y,destination.z);
            p.setDeltaMovement(Vec3.ZERO);p.resetFallDistance();
            p.level().playSound(null,p.blockPosition(),com.myriad.registry.ModSounds.foley("wind"),net.minecraft.sounds.SoundSource.PLAYERS,.5F,1F);
            return;
        }
        PlayerDataProvider.get(p).setBuff("recall",(int)remaining);
        if(remaining%5==0){
            com.myriad.client.particle.ParticleEffects.ring(p.level(),p.position(),.7,12,.35F,.8F,.9F);
            PlayerDataProvider.syncToClient(p);
        }
    }
    public static void cancel(Entity entity) {
        if(entity instanceof ServerPlayer p && CHANNELS.remove(p.getUUID())!=null){
            PlayerDataProvider.get(p).setBuff("recall",0);PlayerDataProvider.syncToClient(p);
        }
    }
    public static void clear(){CHANNELS.clear();}
}
