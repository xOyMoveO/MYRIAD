package com.myriad.character.skills.keheng;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
public final class KehengAudio {
    private KehengAudio(){}
    public static void play(ServerPlayer p,String cue){p.level().playSound(null,p.blockPosition(),com.myriad.registry.ModSounds.keheng(cue),SoundSource.PLAYERS,cue.equals("pulse")?.22f:.42f,1);}
}
