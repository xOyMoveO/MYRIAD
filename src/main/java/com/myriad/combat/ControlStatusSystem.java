package com.myriad.combat;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.game.ModState;
import com.myriad.network.ControlStatusPayload;
import com.myriad.player.PlayerDataProvider;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Read existing authoritative states. Never extends effects, consumes resources or creates an ability Owner. */
public final class ControlStatusSystem {
    private ControlStatusSystem() {}
    private record Sent(ControlStatusPayload payload, long at) {}
    private static final Map<UUID, Sent> LAST = new HashMap<>();
    public static ControlStatusPayload snapshot(ServerPlayer p) {
        var d = PlayerDataProvider.get(p);
        if (!ModState.isEnabled() || !p.isAlive() || p.isSpectator() || !FirstGroupSkills.active(d.getCharacterId()))
            return ControlStatusPayload.empty();
        var charge = SkillChargeSystem.view(p);
        return new ControlStatusPayload(d.getCharacterId(), p.level().dimension().identifier().toString(),
ControlStatusPayload.ticks(d.getBuff("g1_stun")), ControlStatusPayload.ticks(Math.max(d.getBuff("g1_root"), com.myriad.character.skills.rivet.RivetPush.rootRemaining(p))),
                ControlStatusPayload.ticks(SilenceSystem.remaining(p)), (float)ActionTime.multiplier(p.getUUID(), p.level().getGameTime()),
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
