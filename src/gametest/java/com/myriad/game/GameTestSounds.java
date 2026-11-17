package com.myriad.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Observes native ServerLevel sound broadcasts without replacing or cancelling playback. */
public final class GameTestSounds {
    private static final List<Consumer<PlayedSound>> LISTENERS = new ArrayList<>();
    private GameTestSounds() { }
    public record PlayedSound(ServerLevel getLevel, Vec3 getPosition, Holder<SoundEvent> getSound,
            SoundSource getSource, float getOriginalVolume, float getOriginalPitch) { }
    static void addListener(Consumer<PlayedSound> listener) { LISTENERS.add(listener); }
    static void removeListener(Consumer<PlayedSound> listener) { LISTENERS.remove(listener); }
    public static void played(ServerLevel level, Vec3 position, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch) {
        if (LISTENERS.isEmpty()) return;
        var event = new PlayedSound(level, position, sound, source, volume, pitch);
        for (var listener : List.copyOf(LISTENERS)) listener.accept(event);
    }
}
