package com.myriad.character.skills.yike;
import com.myriad.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
/** Original synthesized samples are registered assets; the beam loop follows the confirmed snapshot. */
public final class YikeAudio {
    private YikeAudio(){}
    public static void play(ServerPlayer p,String cue,Vec3 at,float volume,float pitch){p.level().playSound(null,at.x,at.y,at.z,ModSounds.yike(cue),SoundSource.PLAYERS,volume,pitch);}
    public static void loop(ServerPlayer p,boolean active){YikeState.sync(p);}
}
