package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.registry.ModSounds;
import net.minecraft.sounds.SoundSource;

/** Short recorded cues; no loop, guessed end timer, client echo or hit-dependent repetition. */
public final class FlameStepAudio {
    public static final float WIND_VOLUME=.50f,SLASH_VOLUME=.70f;
    private FlameStepAudio(){}
    public static void start(Cast c){play(c,false);}
    public static void finish(Cast c){play(c,true);}
    private static void play(Cast c,boolean finish){
        if(!AbilityRuntime.valid(c.player))return;
        var at=c.player.position();
        c.player.level().playSound(null,at.x,at.y,at.z,
            (finish?ModSounds.FENJUE_STEP_SLASH:ModSounds.FENJUE_STEP_WIND).get(),SoundSource.PLAYERS,
            finish?SLASH_VOLUME:WIND_VOLUME,1f);
    }
}
