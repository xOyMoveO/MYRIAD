package com.myriad.game;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.network.NetworkSetup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** 服务端战争迷雾与草丛判定：每名玩家拥有独立视野结果。 */
public final class VisionSystem {
    private VisionSystem() {}

    public static final double PLAYER_VISION_RADIUS = 40.0;
    public static final int COMBAT_REVEAL_TICKS = 60;
    private static final int SYNC_INTERVAL_TICKS = 1;

    public record BrushInfo(String id, BlockPos min, BlockPos max) {}
    public record BrushDraftInfo(String id, BlockPos pos1, BlockPos pos2) {}

    private static final class BrushDraft {
        BlockPos pos1;
        BlockPos pos2;
        BrushDraft(BlockPos pos1, BlockPos pos2) { this.pos1 = pos1; this.pos2 = pos2; }
        boolean complete() { return pos1 != null && pos2 != null; }
        BrushInfo info(String id) {
            return new BrushInfo(id,
                    new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()),
                            Math.min(pos1.getZ(), pos2.getZ())),
                    new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()),
                            Math.max(pos1.getZ(), pos2.getZ())));
        }
    }

    private static final Map<String, BrushDraft> BRUSHES = new LinkedHashMap<>();
    private static final Map<UUID, Long> REVEALED_UNTIL = new HashMap<>();
    private static final Map<UUID, Set<Integer>> LAST_VISIBLE = new HashMap<>();
    private static MinecraftServer server;
    private static boolean lastEnabled;

    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        loadBrushes();
        REVEALED_UNTIL.clear();
        LAST_VISIBLE.clear();
        lastEnabled = false;
    }

    public static void shutdown() {
        server = null;
        BRUSHES.clear();
        REVEALED_UNTIL.clear();
        LAST_VISIBLE.clear();
        lastEnabled = false;
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % SYNC_INTERVAL_TICKS != 0) return;
        GameManager manager = GameManager.get();
        if (ModState.isEnabled() && (TrainingMode.isEnabled()
                || manager != null && manager.getPhase() == GameManager.Phase.PLAYING)) {
            for (ServerPlayer viewer : level.players()) {
                var team = com.myriad.player.PlayerDataProvider.get(viewer).getFaction();
                var bars = new ArrayList<com.myriad.network.EnemyHealthPayload.Entry>();
                boolean waitingSpectator = HeroRespawnSystem.isWaiting(viewer);
                if (team != null && viewer.isAlive() && (!viewer.isSpectator() || waitingSpectator)) {
                    for (ServerPlayer target : level.players()) {
                        var targetData = com.myriad.player.PlayerDataProvider.get(target);
                        if (target == viewer || targetData.getFaction() == null || !target.isAlive()
                                || target.isSpectator() 
                                || target.isInvisibleTo(viewer) || !viewer.hasLineOfSight(target)) continue;
                        boolean ally = team == targetData.getFaction();
                        if (!ally && !canSee(viewer, target)) continue;
                        bars.add(new com.myriad.network.EnemyHealthPayload.Entry(target.getId(),
                                target.getHealth(), targetData.getModMaxHealth(), ally));
                    }
                }
                PacketDistributor.sendToPlayer(viewer, new com.myriad.network.EnemyHealthPayload(bars));
            }
        }
        boolean enabled = manager != null && manager.getPhase() == GameManager.Phase.PLAYING
                && ModState.isEnabled();
        if (!enabled) {
            if (lastEnabled) {
                for (ServerPlayer viewer : level.players()) send(viewer, false, Set.of());
                LAST_VISIBLE.clear();
            }
            lastEnabled = false;
            return;
        }
        lastEnabled = true;
        long now = level.getGameTime();
        REVEALED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (ServerPlayer viewer : level.players()) {
            Set<Integer> visible = new HashSet<>();
            visible.add(viewer.getId());
            for (ServerPlayer target : level.players()) {
                if (target == viewer || canSee(viewer, target)) visible.add(target.getId());
            }
            Set<Integer> previous = LAST_VISIBLE.get(viewer.getUUID());
            if (!visible.equals(previous)) {
                send(viewer, true, visible);
                LAST_VISIBLE.put(viewer.getUUID(), Set.copyOf(visible));
            }
        }
        LAST_VISIBLE.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    private static void send(ServerPlayer viewer, boolean enabled, Set<Integer> visible) {
        PacketDistributor.sendToPlayer(viewer,
                new NetworkSetup.FogVisibilityPayload(enabled, new ArrayList<>(visible)));
    }

    /** 直接目标类攻击的服务端可见性校验。盲放范围技能仍可命中迷雾中的单位。 */
    public static boolean canSee(ServerPlayer viewer, Entity target) {
        if (!(target instanceof ServerPlayer targetPlayer) || viewer == targetPlayer) return true;
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING || !ModState.isEnabled()) return true;
        if (HeroRespawnSystem.isWaiting(viewer)) {
            Faction team = manager.factionOf(viewer);
            if (team != null && team == manager.factionOf(targetPlayer)) return true;
            if (targetPlayer.isCurrentlyGlowing()) return true;
            for (ServerPlayer source : viewer.level().players()) {
                if (team == null || team != manager.factionOf(source)) continue;
                if (source.isAlive() && !source.isSpectator() && personalCanSee(source, targetPlayer)) return true;
            }
            return false;
        }
        if (viewer.isSpectator()) return true;
        if (viewer.level() != targetPlayer.level()) return false;
        Faction team = manager.factionOf(viewer);
        if (team != null && team == manager.factionOf(targetPlayer)) return true;
        if (targetPlayer.isCurrentlyGlowing()) return true;
        if (viewer.isAlive() && !viewer.isSpectator() && personalCanSee(viewer, targetPlayer)) return true;
        for (ServerPlayer source : viewer.level().players()) {
            if (source == viewer || team == null || team != manager.factionOf(source)) continue;
            if (source.isAlive() && !source.isSpectator() && personalCanSee(source, targetPlayer)) return true;
        }
        return false;
    }

    private static boolean personalCanSee(ServerPlayer viewer, ServerPlayer targetPlayer) {
        GameManager manager = GameManager.get();
        boolean personalVision = viewer.distanceToSqr(targetPlayer) <= PLAYER_VISION_RADIUS * PLAYER_VISION_RADIUS;
        Faction faction = manager.factionOf(viewer);
        List<Vec3> wards = faction == null ? List.of() : TacticalSupplySystem.wardPositions(faction);
        boolean wardVision = wards.stream().anyMatch(pos -> pos.distanceToSqr(targetPlayer.position())
                <= TacticalSupplySystem.wardVisionRadiusSqr());
        if (!personalVision && !wardVision) return false;

        String targetBrush = brushAt(targetPlayer.position());
        if (targetBrush == null) return true;
        long now = targetPlayer.level().getGameTime();
        if (REVEALED_UNTIL.getOrDefault(targetPlayer.getUUID(), 0L) > now) return true;
        if (targetBrush.equals(brushAt(viewer.position()))) return true;
        if (TacticalSupplySystem.hasActiveTrueSight(viewer)
                && viewer.distanceToSqr(targetPlayer) <= TacticalSupplySystem.trueSightRadiusSqr()) return true;
        for (Vec3 ward : wards) {
            if (targetBrush.equals(brushAt(ward))
                    && ward.distanceToSqr(targetPlayer.position()) <= TacticalSupplySystem.wardVisionRadiusSqr()) {
                return true;
            }
        }
        return false;
    }

    /** 非实体战场特效的可见性判定；用于节点、边线等，避免全局粒子泄露迷雾外坐标。 */
    public static boolean canSeePosition(ServerPlayer viewer, Vec3 position) {
        if (viewer == null || position == null) return false;
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING || !ModState.isEnabled()) return true;
        if (viewer.isSpectator()) return true;
        Faction team = manager.factionOf(viewer);
        if (viewer.isAlive() && !viewer.isSpectator() && personalCanSeePosition(viewer, position)) return true;
        for (ServerPlayer source : viewer.level().players()) {
            if (source == viewer || team == null || team != manager.factionOf(source)) continue;
            if (source.isAlive() && !source.isSpectator() && personalCanSeePosition(source, position)) return true;
        }
        return false;
    }

    private static boolean personalCanSeePosition(ServerPlayer viewer, Vec3 position) {
        GameManager manager = GameManager.get();
        boolean personalVision = viewer.position().distanceToSqr(position)
                <= PLAYER_VISION_RADIUS * PLAYER_VISION_RADIUS;
        Faction faction = manager.factionOf(viewer);
        List<Vec3> wards = faction == null ? List.of() : TacticalSupplySystem.wardPositions(faction);
        boolean wardVision = wards.stream().anyMatch(pos -> pos.distanceToSqr(position)
                <= TacticalSupplySystem.wardVisionRadiusSqr());
        if (!personalVision && !wardVision) return false;
        String brush = brushAt(position);
        if (brush == null || brush.equals(brushAt(viewer.position()))) return true;
        if (TacticalSupplySystem.hasActiveTrueSight(viewer)
                && viewer.position().distanceToSqr(position) <= TacticalSupplySystem.trueSightRadiusSqr()) return true;
        return wards.stream().anyMatch(ward -> brush.equals(brushAt(ward))
                && ward.distanceToSqr(position) <= TacticalSupplySystem.wardVisionRadiusSqr());
    }

    /** 造成或受到有效伤害后，草丛隐蔽暂时失效。 */
    public static void revealCombatant(Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) return;
        REVEALED_UNTIL.put(player.getUUID(), player.level().getGameTime() + COMBAT_REVEAL_TICKS);
    }

    /** 小兵没有队友共享视野：隐身或进入另一片草丛就会立即失去其仇恨。 */
    public static boolean concealedFromNpc(Entity observer, ServerPlayer target) {
        if (target.isCurrentlyGlowing()) return false;
        if (target.isInvisible()) return true;
        String brush = brushAt(target.position());
        return brush != null && !brush.equals(brushAt(observer.position()));
    }

    public static String brushAt(Vec3 position) {
        for (Map.Entry<String, BrushDraft> entry : BRUSHES.entrySet()) {
            BrushDraft draft = entry.getValue();
            if (!draft.complete()) continue;
            BrushInfo brush = draft.info(entry.getKey());
            if (position.x >= brush.min.getX() && position.x < brush.max.getX() + 1.0
                    && position.y >= brush.min.getY() && position.y < brush.max.getY() + 1.0
                    && position.z >= brush.min.getZ() && position.z < brush.max.getZ() + 1.0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean setBrushCorner(String id, int corner, BlockPos pos) {
        if (!canEdit() || !validId(id) || (corner != 1 && corner != 2) || pos == null) return false;
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        BrushDraft draft = BRUSHES.computeIfAbsent(normalized, ignored -> new BrushDraft(null, null));
        if (corner == 1) draft.pos1 = pos.immutable();
        else draft.pos2 = pos.immutable();
        // 单个角点也立即写入当前世界存档，服务器重启后可继续完成另一角点。
        saveBrushes();
        return true;
    }

    public static boolean removeBrush(String id) {
        if (!canEdit() || id == null) return false;
        if (BRUSHES.remove(id.toLowerCase(java.util.Locale.ROOT)) == null) return false;
        saveBrushes();
        return true;
    }

    public static List<BrushInfo> brushInfos() {
        List<BrushInfo> result = new ArrayList<>();
        for (Map.Entry<String, BrushDraft> entry : BRUSHES.entrySet()) {
            if (entry.getValue().complete()) result.add(entry.getValue().info(entry.getKey()));
        }
        return List.copyOf(result);
    }

    /** 包含尚未放完两个角点的区域，供地图编辑器继续配置。 */
    public static List<BrushDraftInfo> brushDraftInfos() {
        List<BrushDraftInfo> result = new ArrayList<>();
        for (Map.Entry<String, BrushDraft> entry : BRUSHES.entrySet()) {
            BrushDraft draft = entry.getValue();
            result.add(new BrushDraftInfo(entry.getKey(), draft.pos1, draft.pos2));
        }
        return List.copyOf(result);
    }

    private static boolean canEdit() {
        GameManager manager = GameManager.get();
        return server != null && (manager == null || manager.getPhase() == GameManager.Phase.WAITING);
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,32}");
    }

    private static void saveBrushes() {
        if (server == null) return;
        JsonArray array = new JsonArray();
        for (Map.Entry<String, BrushDraft> entry : BRUSHES.entrySet()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", entry.getKey());
            if (entry.getValue().pos1 != null) {
                writePos(json, "x1", "y1", "z1", entry.getValue().pos1);
            }
            if (entry.getValue().pos2 != null) {
                writePos(json, "x2", "y2", "z2", entry.getValue().pos2);
            }
            array.add(json);
        }
        JsonObject root = new JsonObject();
        root.add("brushes", array);
        try {
            Files.writeString(configPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            com.myriad.Myriad.LOGGER.error("[vision] failed to save brush zones", e);
        }
    }

    private static void loadBrushes() {
        BRUSHES.clear();
        Path path = configPath();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("brushes");
            if (array == null) return;
            for (JsonElement element : array) {
                JsonObject json = element.getAsJsonObject();
                String id = json.get("id").getAsString();
                if (!validId(id)) continue;
                BlockPos pos1 = readOptionalPos(json, "x1", "y1", "z1");
                BlockPos pos2 = readOptionalPos(json, "x2", "y2", "z2");
                if (pos1 == null && pos2 == null) continue;
                BRUSHES.put(id.toLowerCase(java.util.Locale.ROOT), new BrushDraft(pos1, pos2));
            }
        } catch (Exception e) {
            com.myriad.Myriad.LOGGER.error("[vision] failed to load brush zones", e);
        }
    }

    private static void writePos(JsonObject json, String x, String y, String z, BlockPos pos) {
        json.addProperty(x, pos.getX());
        json.addProperty(y, pos.getY());
        json.addProperty(z, pos.getZ());
    }

    private static BlockPos readOptionalPos(JsonObject json, String x, String y, String z) {
        if (!json.has(x) || !json.has(y) || !json.has(z)) return null;
        return new BlockPos(json.get(x).getAsInt(), json.get(y).getAsInt(), json.get(z).getAsInt());
    }

    private static Path configPath() {
        return MatchArena.configPath(server, "myriad_brush_zones.json");
    }
}
