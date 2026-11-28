package com.myriad.combat;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.game.ModState;
import com.myriad.network.ControlStatusPayload;
import com.myriad.network.EnemyHealthPayload.Controls;
import com.myriad.character.skills.rivet.RivetPush;
import com.myriad.player.PlayerDataProvider;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Shared overlap policy and observation of authoritative control states. */
public final class ControlStatusSystem {
    private ControlStatusSystem() {}
    private record Sent(ControlStatusPayload payload, long at) {}
    private static final Map<UUID, Sent> LAST = new HashMap<>();

    /** Both arguments use the same time origin, so either deadlines or remaining ticks can be compared. */
    public static long laterExpiry(long current, long incoming) {
        return Math.max(current, incoming);
    }

    public static Controls controls(ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        return new Controls(ControlStatusPayload.ticks(data.getBuff("g1_stun")),
                ControlStatusPayload.ticks(SilenceSystem.remaining(player)),
                ControlStatusPayload.ticks(Math.max(data.getBuff("g1_root"), RivetPush.rootRemaining(player))));
    }

    public static ControlStatusPayload snapshot(ServerPlayer p) {
        var d = PlayerDataProvider.get(p);
        if (!ModState.isEnabled() || !p.isAlive() || p.isSpectator() || !FirstGroupSkills.active(d.getCharacterId()))
            return ControlStatusPayload.empty();
        var charge = SkillChargeSystem.view(p);
        var controls = controls(p);
        return new ControlStatusPayload(d.getCharacterId(), p.level().dimension().identifier().toString(),
                controls.stun(), controls.root(), controls.silence(), (float)ActionTime.multiplier(p.getUUID(), p.level().getGameTime()),
                charge == null ? "" : charge.skill(), charge == null ? 0 : ControlStatusPayload.ticks(charge.ticks()),
                charge == null ? 1 : charge.walkFactor(), ControlStatusPayload.ticks(AbilityRuntime.motionRemaining(p)));
    }
    private static boolean same(Sent old, ControlStatusPayload next, long now) {
        long age = now - old.at;
        return age >= 0 && age < 5 && old.payload.elapsed((int)age).equals(next);
    }
    public static void tick(MinecraftServer server) {
        for (var p : server.getPlayerList().getPlayers()) {
            if (p.connection == null || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, ControlStatusPayload.TYPE))
                continue;
            var next = snapshot(p); long now = p.level().getGameTime(); var old = LAST.get(p.getUUID());
            if (old == null || !same(old, next, now) && (next.active() || old.payload.active() || !next.equals(old.payload))) {
                com.myriad.network.Packets.sendToPlayer(p, next);
                LAST.put(p.getUUID(), new Sent(next, now));
            }
        }
        LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
    public static void forget(UUID id) { LAST.remove(id); }
    public static void clearAll() { LAST.clear(); }
}
