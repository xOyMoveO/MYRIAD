package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.registry.ModSounds;
import net.minecraft.sounds.SoundSource;

/** Two edited recording layers, already containing both strokes at their authored 180ms spacing. */
public final class FlameSlashAudio {
    public static final float BLADE_VOLUME=.70f,FIRE_VOLUME=.32f;
    private FlameSlashAudio(){}
    public static void play(Cast c){
        if(!AbilityRuntime.valid(c.player))return;
        var at=c.player.position();
        c.player.level().playSound(null,at.x,at.y,at.z,ModSounds.FENJUE_SLASH_BLADE.get(),SoundSource.PLAYERS,BLADE_VOLUME,1f);
        c.player.level().playSound(null,at.x,at.y,at.z,ModSounds.FENJUE_SLASH_FIRE.get(),SoundSource.PLAYERS,FIRE_VOLUME,1f);
    }
}
