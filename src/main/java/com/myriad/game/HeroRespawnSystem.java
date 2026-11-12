package com.myriad.game;

import com.myriad.player.PlayerDataProvider;
import com.myriad.network.SpectatorTargetPayload;
import com.myriad.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 本局死亡等待：临时旁观队友，倒计时结束固定恢复冒险模式。 */
public final class HeroRespawnSystem {
    private record Waiting(long until, Vec3 point, GameType mode, UUID target) {}
    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    private HeroRespawnSystem() {}
    public static void clear() { WAITING.clear(); }
    public static boolean isWaiting(ServerPlayer player) { return WAITING.containsKey(player.getUUID()); }
    public static com.google.gson.JsonElement snapshot() { return MatchProgress.JSON.toJsonTree(WAITING); }
    public static void restore(com.google.gson.JsonElement json) {
        WAITING.clear();
        if(json!=null) WAITING.putAll(MatchProgress.JSON.fromJson(json,
                new com.google.gson.reflect.TypeToken<Map<UUID,Waiting>>(){}.getType()));
    }
    public static void reset(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Waiting waiting = WAITING.remove(player.getUUID());
            if (waiting != null) {
                player.setCamera(player);
                PacketDistributor.sendToPlayer(player, new SpectatorTargetPayload(false, ""));
                player.setGameMode(GameType.ADVENTURE);
                player.setHealth(player.getMaxHealth());
                PlayerDataProvider.get(player).setBuff("hero_respawn", 0);
            }
        }
        WAITING.clear();
    }
    public static void begin(ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        var fountain = LaneSystem.fountainPoint(data.getFaction());
        Vec3 point = fountain == null ? player.position() : Vec3.atBottomCenterOf(fountain).add(0, 1, 0);
        int ticks = (2 * Math.max(1, Math.min(15, data.getLevel())) + 7) * 20;
        if (data.getBuff("rune_realm_respawn") > 0) {
            ticks = Math.max(20, ticks / 2);
            data.setBuff("rune_realm_respawn", 0);
        }
        WAITING.put(player.getUUID(), new Waiting(player.level().getGameTime() + ticks,
                point, GameType.ADVENTURE, null));
        player.setHealth(1);
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
        player.teleportTo(point.x, point.y, point.z);
        data.setBuff("hero_respawn", ticks);
        PlayerDataProvider.syncToClient(player);
        selectFirstTarget(player, true);
    }

    /** 左键切换到下一名仍存活的同阵营玩家；请求仅在死亡等待状态内有效。 */
    public static void cycleTarget(ServerPlayer player) {
        Waiting waiting = WAITING.get(player.getUUID());
        if (waiting == null || !player.isSpectator()) return;
        List<ServerPlayer> allies = eligibleAllies(player);
        if (allies.isEmpty()) {
            attach(player, waiting, null, true);
            return;
        }
        int current = -1;
        for (int i = 0; i < allies.size(); i++) {
            if (allies.get(i).getUUID().equals(waiting.target)) {
                current = i;
                break;
            }
        }
        attach(player, waiting, allies.get((current + 1) % allies.size()), true);
    }

    public static boolean tick(ServerPlayer player) {
        Waiting waiting = WAITING.get(player.getUUID());
        if (waiting == null) return false;
        var data = PlayerDataProvider.get(player);
        long remaining = waiting.until - player.level().getGameTime();
        boolean active = ModState.isEnabled() && (TrainingMode.isEnabled()
                || GameManager.get() != null && GameManager.get().getPhase() == GameManager.Phase.PLAYING);
        ServerPlayer ally = waiting.target == null ? null : player.level().getServer().getPlayerList().getPlayer(waiting.target);
        if (!isEligibleAlly(player, ally)) ally = null;
        if (ally == null) {
            List<ServerPlayer> allies = eligibleAllies(player);
            ally = allies.isEmpty() ? null : allies.getFirst();
        }
        if (remaining > 0 && active) {
            attach(player, waiting, ally, false);
            player.setDeltaMovement(Vec3.ZERO);
        }
        if (remaining <= 0 || !active) {
            WAITING.remove(player.getUUID());
            player.setCamera(player);
            PacketDistributor.sendToPlayer(player, new SpectatorTargetPayload(false, ""));
            player.teleportTo(waiting.point.x, waiting.point.y, waiting.point.z);
            player.setGameMode(GameType.ADVENTURE);
            com.myriad.player.PlayerHealthSystem.fill(player);
            player.clearFire();
            player.resetFallDistance();
            data.setMana((float) player.getAttributeValue(ModAttributes.MAX_MANA));
            data.setBuff("hero_respawn", 0);
        } else {
            data.setBuff("hero_respawn", (int) remaining);
            data.tickCooldowns();
        }
        PlayerDataProvider.syncToClient(player);
        return true;
    }

    private static void selectFirstTarget(ServerPlayer player, boolean forceSync) {
        Waiting waiting = WAITING.get(player.getUUID());
        if (waiting == null) return;
        List<ServerPlayer> allies = eligibleAllies(player);
        attach(player, waiting, allies.isEmpty() ? null : allies.getFirst(), forceSync);
    }

    private static List<ServerPlayer> eligibleAllies(ServerPlayer player) {
        return player.level().players().stream()
                .filter(candidate -> isEligibleAlly(player, candidate))
                .sorted(Comparator.comparing(candidate -> candidate.getGameProfile().name(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean isEligibleAlly(ServerPlayer player, ServerPlayer candidate) {
        GameManager manager = GameManager.get();
        Faction team = manager == null ? null : manager.factionOf(player);
        return candidate != null && candidate != player && candidate.level() == player.level()
                && candidate.isAlive() && !candidate.isSpectator() && team != null
                && team == manager.factionOf(candidate);
    }

    private static void attach(ServerPlayer player, Waiting waiting, ServerPlayer target, boolean forceSync) {
        boolean cameraChanged = player.getCamera() != (target == null ? player : target);
        UUID targetId = target == null ? null : target.getUUID();
        if (!java.util.Objects.equals(waiting.target, targetId)) {
            waiting = new Waiting(waiting.until, waiting.point, waiting.mode, targetId);
            WAITING.put(player.getUUID(), waiting);
            cameraChanged = true;
        }
        if (target == null) {
            player.setCamera(player);
            player.teleportTo(waiting.point.x, waiting.point.y, waiting.point.z);
        } else {
            // 原版 setCamera 直接把客户端相机附着到目标；同步旁观者实体位置以持续发送目标区块。
            player.teleportTo(target.getX(), target.getY(), target.getZ());
            player.setCamera(target);
        }
        if (forceSync || cameraChanged) {
            PacketDistributor.sendToPlayer(player, new SpectatorTargetPayload(true,
                    target == null ? "" : target.getName().getString()));
        }
    }
}
