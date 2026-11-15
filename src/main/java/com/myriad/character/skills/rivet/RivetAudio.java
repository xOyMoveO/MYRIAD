package com.myriad.character.skills.rivet;
import com.myriad.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
/** Material-based world cues reuse the project's licensed Foley recordings. */
public final class RivetAudio {
    private RivetAudio(){}
    public static void play(ServerPlayer p,String cue){playAt(p,p.position(),cue);}
    public static void playAt(ServerPlayer p,Vec3 at,String cue){
        p.level().playSound(null,BlockPos.containing(at),ModSounds.rivet(cue),SoundSource.PLAYERS,
                cue.equals("charge")?.6f:cue.equals("wall_hit")||cue.equals("anchor_hit")?.65f:.38f,1);
    }
}
