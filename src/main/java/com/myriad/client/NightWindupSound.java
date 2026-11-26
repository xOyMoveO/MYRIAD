package com.myriad.client;

import com.myriad.network.NightWindupPayload;
import com.myriad.registry.ModSounds.Night;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public final class NightWindupSound extends AbstractTickableSoundInstance {
    private static final Map<Integer, NightWindupSound> ACTIVE = new HashMap<>();
    private final ClientLevel world;
    private final int actor;
    private final int cast;

    private NightWindupSound(Entity owner, NightWindupPayload warning, ClientLevel level) {
        super(Night.WINDUP.sound, SoundSource.PLAYERS, RandomSource.create());
        world = level;
        actor = warning.actor();
        cast = warning.cast();
        x = owner.getX();
        y = owner.getY() + .7;
        z = owner.getZ();
        volume = Night.WINDUP.volume;
        pitch = warning.rate();
    }

    public static void receive(NightWindupPayload warning) {
        var mc = Minecraft.getInstance();
        if (!Float.isFinite(warning.rate()) || warning.rate() < 0 || warning.rate() > 1) return;
        var previous = ACTIVE.get(warning.actor());
        if (previous != null && previous.cast == warning.cast() && previous.world == mc.level) {
            if (warning.rate() == 0) previous.finish();
            else previous.pitch = warning.rate();
            return;
        }
        if (warning.rate() == 0 || mc.level == null) return;
        var owner = mc.level.getEntity(warning.actor());
        if (owner == null || owner.isRemoved()) return;
        if (previous != null) previous.finish();
        var sound = new NightWindupSound(owner, warning, mc.level);
        ACTIVE.put(warning.actor(), sound);
        mc.getSoundManager().play(sound);
    }

    public static void reset() {
        for (var sound : java.util.List.copyOf(ACTIVE.values())) sound.finish();
    }

    private void finish() {
        stop();
        ACTIVE.remove(actor, this);
        Minecraft.getInstance().getSoundManager().stop(this);
    }

    @Override public void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level != world || world.getEntity(actor) == null || !ClientDataHolder.modEnabled()) finish();
    }
}
