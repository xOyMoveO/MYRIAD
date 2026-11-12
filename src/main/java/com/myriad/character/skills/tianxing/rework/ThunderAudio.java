package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.sounds.SoundEvent;

/** Short recorded electrical cues, server-authoritative and positional. No tick loops/client echo. */
public final class ThunderAudio {
    private ThunderAudio(){}
    public enum Cue {
        CHARGE(ModSounds.TIANXING_CHARGE,.25f,0), CAST(ModSounds.TIANXING_CAST,.55f,0),
        HIT(ModSounds.TIANXING_HIT,.35f,3), GUARD(ModSounds.TIANXING_GUARD,.45f,0),
        PULSE(ModSounds.TIANXING_PULSE,.28f,8), JUDGMENT(ModSounds.TIANXING_JUDGMENT,.75f,0);
        final DeferredHolder<SoundEvent,SoundEvent> event;
        final float volume;final int spacing;
        Cue(DeferredHolder<SoundEvent,SoundEvent> event,float volume,int spacing){this.event=event;this.volume=volume;this.spacing=spacing;}
    }
    public static void play(Cast c,Cue cue){play(c,cue,c.player.position());}
    public static void play(Cast c,Cue cue,Vec3 at){
        if(!com.myriad.character.ability.AbilityRuntime.valid(c.player))return;
        if(cue.spacing>0){
            // Entity-owned cooldown: bounded contact feedback for piercing/AOE, not one sound per victim.
            String key="myriad_thunder_audio_"+cue.name();
            long last=c.player.getPersistentData().getLongOr(key,Long.MIN_VALUE);
            if(last!=Long.MIN_VALUE&&c.now()>=last&&c.now()-last<cue.spacing)return;
            c.player.getPersistentData().putLong(key,c.now());
        }
        c.player.level().playSound(null,at.x,at.y,at.z,cue.event.get(),SoundSource.PLAYERS,cue.volume,1f);
    }
}
