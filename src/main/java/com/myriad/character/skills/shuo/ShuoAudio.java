package com.myriad.character.skills.shuo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import com.myriad.registry.ModSounds;
/** Actual stage callbacks only, never a client-predicted payment or repeated per-frame sound. */
public final class ShuoAudio {
    private ShuoAudio(){}
    public static void play(ServerPlayer p,String cue){p.level().playSound(null,p.blockPosition(),ModSounds.shuo(cue),SoundSource.PLAYERS,cue.equals("sheathe")?.58f:.52f,1);}
}
