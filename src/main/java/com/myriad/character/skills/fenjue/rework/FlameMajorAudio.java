package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Skill-owned recorded cues. Positional server playback, no client echo or persistent audio loop. */
public final class FlameMajorAudio {
    private FlameMajorAudio(){}
    public static void cageCast(Cast c,Vec3 at){play(c,at,ModSounds.FENJUE_CAGE_CAST.get(),.48f);}
    public static void cageOpen(Cast c,Vec3 at){play(c,at,ModSounds.FENJUE_CAGE_OPEN.get(),.64f);}
    public static void burstCharge(Cast c){play(c,c.player.position(),ModSounds.FENJUE_BURST_CHARGE.get(),.48f);}
    public static void burstRelease(Cast c){play(c,c.player.position(),ModSounds.FENJUE_BURST_RELEASE.get(),.74f);}
    public static void seaJump(Cast c){play(c,c.player.position(),ModSounds.FENJUE_SEA_JUMP.get(),.52f);}
    public static void seaImpact(Cast c,Vec3 at){play(c,at,ModSounds.FENJUE_SEA_IMPACT.get(),.78f);}
    public static void armor(Cast c){play(c,c.player.position(),ModSounds.FENJUE_ARMOR_IGNITE.get(),.58f);}
    public static void burning(Cast c,Vec3 at){play(c,at,SoundEvents.FIRE_AMBIENT,.22f);}
    private static void play(Cast c,Vec3 at,SoundEvent sound,float volume){
        if(!AbilityRuntime.valid(c.player))return;
        c.player.level().playSound(null,at.x,at.y,at.z,sound,SoundSource.PLAYERS,volume,1f);
    }
}
