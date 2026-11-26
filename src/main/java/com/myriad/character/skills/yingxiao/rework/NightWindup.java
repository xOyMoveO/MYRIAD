package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.ActionTime;
import com.myriad.network.NightWindupPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModSounds.Night;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/** One audible warning per eligible viewer, driven by the same action clock as the attack. */
final class NightWindup {
    private final Cast cast;
    private final List<ServerPlayer> listeners;
    private float rate;

    NightWindup(Cast cast) {
        this.cast = cast;
        listeners = new ArrayList<>(NightAudio.listeners(cast, cast.player.position()));
        rate = actionRate();
        listeners.forEach(viewer -> update(viewer, rate));
    }

    void track() {
        cast.whileActive(Integer.MAX_VALUE, age -> {
            if (cast.phase() != AbilityRuntime.Phase.WINDUP) {
                stop();
                if (cast.phase() == AbilityRuntime.Phase.CANCELLED) NightAudio.personal(cast, Night.CANCEL);
                return false;
            }
            float next = actionRate();
            listeners.removeIf(viewer -> {
                if (viewer.isRemoved() || !NightAudio.canHear(cast.player, viewer, cast.player.position())) {
                    update(viewer, 0);
                    return true;
                }
                if (rate != next) update(viewer, next);
                return false;
            });
            rate = next;
            return true;
        }, this::stop);
    }

    void stop() {
        listeners.forEach(viewer -> update(viewer, 0));
        listeners.clear();
    }

    private float actionRate() {
        return (float) ActionTime.multiplier(cast.player.getUUID(), cast.now());
    }

    private void update(ServerPlayer viewer, float value) {
        if (viewer.connection != null && !viewer.hasDisconnected())
            Packets.sendToPlayer(viewer, new NightWindupPayload(cast.player.getId(), cast.castId(), value));
    }
}
