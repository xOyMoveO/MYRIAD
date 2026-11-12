package com.myriad.player;

import com.myriad.game.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Team-private presentation for an actual XP level increase, never world-broadcast particles/audio. */
public final class LevelUpPresentation {
    public static final int LIFE=24;
    public static final double RANGE=64;
    private LevelUpPresentation(){}
    private static Faction faction(ServerPlayer p){
        if(TrainingMode.isEnabled())return PlayerDataProvider.get(p).getFaction();
        var manager=GameManager.get();return manager==null?null:manager.factionOf(p);
    }
    public static boolean teammate(boolean self,Faction own,Faction other){return self||own!=null&&own==other;}
    public static boolean recipient(ServerPlayer subject,ServerPlayer viewer){
        return subject.level()==viewer.level()&&viewer.distanceToSqr(subject)<=RANGE*RANGE
                &&teammate(viewer==subject,faction(subject),faction(viewer));
    }
    public static AbilityVfxPayload recipe(ServerPlayer p){
        var at=p.position();
        // A reserved visual marker, inside the existing payload schema. No new network type.
        return new AbilityVfxPayload(p.getId(),p.getId(),0,7,
                com.myriad.character.ability.AbilityVfxGeometry.PILLAR,at,at.add(0,4.8,0),.65f,358,LIFE,p.level().getGameTime(),false);
    }
    public static void play(ServerPlayer player){
        if(!ModState.isEnabled()||!player.isAlive()||player.isSpectator()
                ||com.myriad.character.CharacterType.byId(PlayerDataProvider.get(player).getCharacterId())==null)return;
        var packet=recipe(player);long seed=player.getRandom().nextLong();
        for(var viewer:player.level().players())if(recipient(player,viewer))send(player,viewer,packet,seed);
        // Mock/off-list players need no broadcast; ordinary connected players are in level.players().
    }
    private static void send(ServerPlayer subject,ServerPlayer viewer,AbilityVfxPayload packet,long seed){
        PacketDistributor.sendToPlayer(viewer,packet);
        // Both layers are sent only to the approved audience. Positional attenuation stays local.
        viewer.connection.send(new ClientboundSoundPacket(Holder.direct(ModSounds.UI_LEVEL_UP.get()),SoundSource.PLAYERS,
                subject.getX(),subject.getY()+1,subject.getZ(),.55f,1.18f,seed));
        viewer.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME),SoundSource.PLAYERS,
                subject.getX(),subject.getY()+1,subject.getZ(),.38f,1.32f,seed+1));
    }
}
