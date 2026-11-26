package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.myriad.network.NetworkSetup;
import com.myriad.network.RiftCrabMarker;
import java.util.EnumMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.myriad.network.Packets;

/**
 * 服务端权威小地图。四角按左上、右上、右下、左下设置，支持相对世界 X/Z 轴旋转的矩形。
 * 客户端只收到边界内的同阵营玩家和兵线标记，不能借小地图获取敌方玩家坐标。
 */
public final class MapSystem {
    private MapSystem() {}

    public static final int CORNER_COUNT = 4;
    private static final String FILE_NAME = "myriad_minimap.json";
    private static final Map<Integer, BlockPos> CORNERS = new LinkedHashMap<>();
    private static MinecraftServer server;

    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        ArenaConfigFile.readOptional(configPath(), root -> decodeCorners(root, false)).ifPresent(MapSystem::activateCorners);
    }

    public static void shutdown() {
        server = null;
        CORNERS.clear();
    }

    public static boolean setCorner(int slot, BlockPos pos) {
        if (!canEdit() || slot < 1 || slot > CORNER_COUNT || pos == null) return false;
        var next = cornerEditBase();
        if (next == null) return false;
        next.put(slot, pos.immutable());
        return save(next);
    }

    public static boolean clearCorner(int slot) {
        if (!canEdit() || slot < 1 || slot > CORNER_COUNT) return false;
        var next = cornerEditBase();
        if (next == null) return false;
        next.remove(slot);
        return save(next);
    }

    private static Map<Integer, BlockPos> cornerEditBase() {
        return ArenaConfigFile.readForEdit(configPath(), root -> decodeCorners(root, false),
                () -> new LinkedHashMap<>(CORNERS)).orElse(null);
    }

    public static BlockPos corner(int slot) {
        return CORNERS.get(slot);
    }

    public static boolean configured() {
        return geometry() != null;
    }

    /** 技能落点等服务端逻辑使用的地图边界校验；未配置边界时不额外限制。 */
    public static boolean contains(double x, double z) {
        Geometry geometry = geometry();
        return geometry == null || geometry.contains(x, z);
    }

    /** HUD 要求逐 tick 刷新：每 tick 同步队友、兵线与建筑标记。 */
    public static void tick(ServerLevel level) {
        if (level.players().isEmpty()) return;
        Geometry geometry = geometry();
        if (geometry == null) {
            for (ServerPlayer player : level.players()) sendChanged(player, EMPTY);
            return;
        }
        GameManager manager = GameManager.get();
        if (manager == null) return;
        List<NetworkSetup.MinimapMarker> units = new ArrayList<>();
        for (var unit : LaneSystem.minimapUnits(level)) {
            if (geometry.contains(unit.x(), unit.z())) {
                units.add(new NetworkSetup.MinimapMarker(unit.kind(), NetworkSetup.factionId(unit.faction()),
                        (float) unit.x(), (float) unit.z(), NetworkSetup.MinimapMarker.NO_PLAYER));
            }
        }
        List<NetworkSetup.MinimapMarker> camps = neutralCampMarkers();
        List<NetworkSetup.MinimapPathPoint> paths = LaneSystem.minimapPaths().stream()
                .map(point -> new NetworkSetup.MinimapPathPoint(point.lane(), point.order(),
                        (float) point.x(), (float) point.z()))
                .toList();
        var crabsByTeam = new EnumMap<Faction, List<RiftCrabMarker>>(Faction.class);
        for (ServerPlayer viewer : level.players()) {
            if (manager.isQueuedMatch() && !manager.isParticipant(viewer.getUUID())) {
                sendChanged(viewer, EMPTY);
                continue;
            }
            Faction viewerFaction = manager.factionOf(viewer);
            List<NetworkSetup.MinimapMarker> markers = new ArrayList<>();
            markers.addAll(camps);
            if (viewerFaction != null) {
                for (ServerPlayer candidate : level.players()) {
                    if (!candidate.isAlive() || manager.factionOf(candidate) != viewerFaction
                            || !geometry.contains(candidate.getX(), candidate.getZ())) continue;
                    markers.add(new NetworkSetup.MinimapMarker(candidate == viewer ? 1 : 0,
                            NetworkSetup.factionId(viewerFaction), (float) candidate.getX(),
                            (float) candidate.getZ(), candidate.getUUID()));
                }
            }
            markers.addAll(units);
            List<RiftCrabMarker> crabs = viewerFaction == null || !manager.isParticipant(viewer.getUUID())
                    || TrainingMode.isEnabled(viewer) ? List.of()
                    : crabsByTeam.computeIfAbsent(viewerFaction, team -> MonsterManager.crabMapMarkers(level, team));
            sendChanged(viewer, snapshot(paths, markers, crabs));
        }
    }

    private static void sendChanged(ServerPlayer viewer, NetworkSetup.MinimapSnapshotPayload snapshot) {
        if (com.myriad.network.BattleSnapshots.of(viewer).sendMap(snapshot)) Packets.sendToPlayer(viewer, snapshot);
    }

    /** Fixed configured locations only: no live entities, respawn timers or fog-hidden information. */
    public static List<NetworkSetup.MinimapMarker> neutralCampMarkers() {
        Geometry geometry = geometry();
        if (geometry == null) return List.of();
        List<NetworkSetup.MinimapMarker> result = new ArrayList<>();
        for (MonsterManager.SpawnPointInfo camp : MonsterManager.spawnPointInfos()) {
            float x = camp.pos().getX() + 0.5F;
            float z = camp.pos().getZ() + 0.5F;
            if (!geometry.contains(x, z)) continue;
            result.add(new NetworkSetup.MinimapMarker(camp.boss()
                    ? NetworkSetup.MinimapMarker.BOSS : NetworkSetup.MinimapMarker.CAMP,
                    0, x, z, NetworkSetup.MinimapMarker.NO_PLAYER));
        }
        return List.copyOf(result);
    }

    private static NetworkSetup.MinimapSnapshotPayload snapshot(List<NetworkSetup.MinimapPathPoint> paths,
            List<NetworkSetup.MinimapMarker> markers, List<RiftCrabMarker> crabs) {
        BlockPos p1 = CORNERS.get(1);
        BlockPos p2 = CORNERS.get(2);
        BlockPos p3 = CORNERS.get(3);
        BlockPos p4 = CORNERS.get(4);
        return new NetworkSetup.MinimapSnapshotPayload(true,
                p1.getX() + 0.5F, p1.getZ() + 0.5F,
                p2.getX() + 0.5F, p2.getZ() + 0.5F,
                p3.getX() + 0.5F, p3.getZ() + 0.5F,
                p4.getX() + 0.5F, p4.getZ() + 0.5F,
                paths,
                List.copyOf(markers), crabs);
    }

    private static final NetworkSetup.MinimapSnapshotPayload EMPTY = new NetworkSetup.MinimapSnapshotPayload(
            false, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());

    private static Geometry geometry() {
        return geometry(CORNERS);
    }

    private static Geometry geometry(Map<Integer, BlockPos> corners) {
        BlockPos p1 = corners.get(1);
        BlockPos p2 = corners.get(2);
        BlockPos p3 = corners.get(3);
        BlockPos p4 = corners.get(4);
        if (p1 == null || p2 == null || p3 == null || p4 == null) return null;

        // 对边取平均，四个设置点都会参与矩形坐标系计算，并容忍管理员落点的轻微误差。
        double ux = ((p2.getX() - p1.getX()) + (p3.getX() - p4.getX())) * 0.5;
        double uz = ((p2.getZ() - p1.getZ()) + (p3.getZ() - p4.getZ())) * 0.5;
        double vx = ((p4.getX() - p1.getX()) + (p3.getX() - p2.getX())) * 0.5;
        double vz = ((p4.getZ() - p1.getZ()) + (p3.getZ() - p2.getZ())) * 0.5;
        double det = ux * vz - uz * vx;
        if (ux * ux + uz * uz < 16.0 || vx * vx + vz * vz < 16.0 || Math.abs(det) < 1.0) return null;
        return new Geometry(p1.getX() + 0.5, p1.getZ() + 0.5, ux, uz, vx, vz, det);
    }

    private record Geometry(double originX, double originZ,
            double ux, double uz, double vx, double vz, double determinant) {
        boolean contains(double x, double z) {
            double rx = x - originX;
            double rz = z - originZ;
            double u = (rx * vz - rz * vx) / determinant;
            double v = (ux * rz - uz * rx) / determinant;
            return u >= 0.0 && u <= 1.0 && v >= 0.0 && v <= 1.0;
        }
    }

    private static boolean canEdit() {
        GameManager manager = GameManager.get();
        return server != null && (manager == null || manager.getPhase() == GameManager.Phase.WAITING);
    }

    private static boolean save(Map<Integer, BlockPos> next) {
        JsonArray array = new JsonArray();
        for (int slot = 1; slot <= CORNER_COUNT; slot++) {
            BlockPos pos = next.get(slot);
            if (pos == null) continue;
            JsonObject point = new JsonObject();
            point.addProperty("slot", slot);
            point.addProperty("x", pos.getX());
            point.addProperty("y", pos.getY());
            point.addProperty("z", pos.getZ());
            array.add(point);
        }
        JsonObject root = new JsonObject();
        root.add("corners", array);
        if (!ArenaConfigFile.save(configPath(), root)) return false;
        activateCorners(next);
        return true;
    }

    static void activateCorners(Map<Integer, BlockPos> next) {
        CORNERS.clear();
        CORNERS.putAll(next);
    }

    static Map<Integer, BlockPos> decodeCorners(JsonObject root, boolean duel) {
        var corners = new LinkedHashMap<Integer, BlockPos>();
        var rows = ArenaConfigFile.array(root, "corners");
        for (JsonElement element : rows) {
            JsonObject point = element.getAsJsonObject();
            int slot = point.get("slot").getAsInt();
            if (slot >= 1 && slot <= CORNER_COUNT) {
                corners.put(slot, new BlockPos(point.get("x").getAsInt(), point.get("y").getAsInt(),
                        point.get("z").getAsInt()));
            }
        }
        if (duel && (rows.size() != CORNER_COUNT || geometry(corners) == null))
            throw new IllegalArgumentException("corners must define four distinct slots and a usable map area");
        return corners;
    }

    private static Path configPath() {
        return MatchArena.configPath(server, FILE_NAME);
    }
}
