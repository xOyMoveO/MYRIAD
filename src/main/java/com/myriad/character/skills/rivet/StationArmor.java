package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.player.ModShieldSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Grounded time is cumulative inside the station; damage exhaustion owns the ten-second lock. */
public final class StationArmor extends RivetSkill {
    private static final class State {
        long last = Long.MIN_VALUE, outside = -1, brokenUntil;
        int grounded;
        boolean initialized, effectiveLeave, fullRecovery;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();
    public StationArmor(Definition definition) { super(definition); }
    @Override public void release(AbilityRuntime.Cast c) { }
    public static String source(ServerPlayer owner) { return "rivet_station_armor/" + owner.getUUID(); }
    @Override public void tickPassive(AbilityRuntime.Cast c) {
        var state = STATES.computeIfAbsent(c.player.getUUID(), ignored -> new State());
        if (state.last == c.now()) return;
        state.last = c.now();
        var anchor = TacticalAnchor.anchor(c.player);
        boolean inside = anchor != null && c.player.position().distanceToSqr(anchor.point()) <= 16.0001;
        if (!inside) {
            if (state.outside < 0) state.outside = c.now();
            if (c.now() - state.outside >= 40) {
                ModShieldSystem.removeSource(c.player, source(c.player));
                state.effectiveLeave = true;
                state.fullRecovery = false;
                state.grounded = 0;
            }
            return;
        }
        state.outside = -1;
        if (c.player.onGround()) state.grounded++;
        if (state.grounded < 40 || c.now() < state.brokenUntil) return;
        float full = c.value(68, 18, .4f);
        if (!state.initialized || state.fullRecovery) {
            ModShieldSystem.grant(c.player, source(c.player), full, Integer.MAX_VALUE);
            state.initialized = true;
            state.fullRecovery = false;
            state.effectiveLeave = false;
            RivetVfx.shield(c, c.player, .7);
        } else if (state.effectiveLeave) {
            ModShieldSystem.grantMaxSource(c.player, source(c.player), full * .2f, Integer.MAX_VALUE);
            state.effectiveLeave = false;
            RivetVfx.shield(c, c.player, .5);
        }
    }
    /** Called only when real damage consumes this exact source, never on expiry or lifecycle removal. */
    public static void shieldBroken(LivingEntity target, String source) {
        if (!(target instanceof ServerPlayer player) || !source.equals(source(player))) return;
        var state = STATES.get(player.getUUID());
        if (state == null) return;
        state.brokenUntil = player.level().getGameTime() + 200;
        state.grounded = 0;
        state.fullRecovery = !state.effectiveLeave;
    }
    public static void clear(ServerPlayer player) { STATES.remove(player.getUUID()); ModShieldSystem.removeSource(player, source(player)); }
}
