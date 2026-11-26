package com.myriad.network;

import com.myriad.network.NetworkSetup.MinimapSnapshotPayload;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Last complete snapshots delivered to this player entity. The cache is never saved or sent. */
public final class BattleSnapshots {
    // The client considers owner data stale after 12 ticks. Idle heroes still need a fresh heartbeat.
    private static final int HUD_HEARTBEAT = 5;
    private PlayerDataPayload hud;
    private int hudTick;
    private MinimapSnapshotPayload map;
    private MatchTabPayload tab;

    private static class Storage {
        static final AttachmentType<BattleSnapshots> TYPE = AttachmentRegistry.create(
                Identifier.parse("myriad:battle_snapshot_delivery"), b -> b.initializer(BattleSnapshots::new));
    }

    static void initialize() {
        var type = Storage.TYPE;
        // Even an unchanged map or roster is new to the client's replacement level.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, previous, next) -> ((AttachmentTarget)player).removeAttached(type));
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, player, alive) -> ((AttachmentTarget)player).removeAttached(type));
    }

    public static BattleSnapshots of(ServerPlayer player) {
        return ((AttachmentTarget)player).getAttachedOrCreate(Storage.TYPE);
    }

    public java.util.Map<String,Double> lastAttributes() {
        return hud == null ? java.util.Map.of() : hud.attrs();
    }

    public boolean sendHud(PlayerDataPayload next, int tick, boolean force) {
        // Some effects deliberately refresh the same remaining duration every tick. Clients
        // extrapolate these countdowns, so an equal nonzero timer still needs correction.
        if (!force && hud != null && tick >= hudTick && tick - hudTick < HUD_HEARTBEAT
                && hud.remainTicks() == next.remainTicks() && next.buffs().isEmpty()
                && next.basicAttackCooldown() == 0 && next.cdQ() == 0 && next.cdW() == 0
                && next.cdE() == 0 && next.cdR() == 0 && next.cdF() == 0 && next.equals(hud)) return false;
        hud = next;
        hudTick = tick;
        return true;
    }

    public boolean sendMap(MinimapSnapshotPayload next) {
        if (next.equals(map)) return false;
        map = next;
        return true;
    }

    public boolean sendTab(MatchTabPayload next) {
        // Tab renders elapsed time in whole seconds. Entry changes, including K/D/A, are immediate.
        if (tab != null && displaySecond(next.elapsedTicks()) == displaySecond(tab.elapsedTicks())
                && next.players().equals(tab.players())) return false;
        tab = next;
        return true;
    }

    private static long displaySecond(long elapsedTicks) {
        return elapsedTicks < 0 ? -1 : elapsedTicks / 20;
    }
}
