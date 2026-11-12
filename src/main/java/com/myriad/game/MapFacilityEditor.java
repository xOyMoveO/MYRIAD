package com.myriad.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.myriad.network.NetworkSetup;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.PacketDistributor;

/** 服务端权威的地图设施编辑入口。所有页面操作在这里重新校验并写入当前地图存档。 */
public final class MapFacilityEditor {
    private MapFacilityEditor() {}

    public static void open(ServerPlayer player) {
        if (!canEdit(player, true)) return;
        sendSnapshot(player);
    }

    public static void handle(ServerPlayer player, NetworkSetup.MapFacilityActionPayload payload) {
        if (!canEdit(player, true)) return;
        if (payload.category().length() > 24 || payload.id().length() > 64
                || payload.meta().length() > 64 || payload.action().length() > 16) {
            fail(player);
            return;
        }

        BlockPos here = player.blockPosition();
        if (payload.category().equals("arena")) {
            if (!payload.action().equals("select") || MatchArena.Mode.byId(payload.id()) == null) {
                fail(player);
            } else {
                com.myriad.player.PlayerDataProvider.selectArenaByAdmin(player, payload.id());
            }
            sendSnapshot(player);
            return;
        }
        boolean changed = switch (payload.category()) {
            case "attribute_vein", "small_vein" -> editVein(false, payload, here);
            case "large_vein" -> editVein(true, payload, here);
            case "lane_path" -> editPath(payload, here);
            case "lane_corner" -> switch (payload.action()) {
                case "set" -> LaneSystem.setLaneCorner(LaneSystem.Lane.byId(payload.id()), here);
                case "clear" -> LaneSystem.setLaneCorner(LaneSystem.Lane.byId(payload.id()), null);
                default -> false;
            };
            case "tower" -> editTower(payload, here);
            case "crystal" -> editCrystal(payload, here);
            case "fountain" -> editFountain(payload, here);
            case "brush" -> editBrush(payload, here);
            case "monster" -> editMonster(payload, here);
            case "map_bound" -> editMapBound(payload, here);
            default -> false;
        };
        if (changed) {
            player.sendSystemMessage(Component.translatable("message.myriad.map_editor_saved"));
        } else {
            fail(player);
        }
        sendSnapshot(player);
    }

    private static boolean canEdit(ServerPlayer player, boolean notify) {
        boolean admin = player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
        boolean overworld = player.level().getServer() != null
                && player.level() == player.level().getServer().overworld();
        GameManager manager = GameManager.get();
        boolean waiting = manager != null && manager.getPhase() == GameManager.Phase.WAITING;
        if (admin && overworld && waiting) return true;
        if (notify) player.sendSystemMessage(Component.translatable(
                !admin ? "message.myriad.map_editor_admin"
                        : !overworld ? "message.myriad.map_editor_overworld"
                        : "message.myriad.settings_only_before"));
        return false;
    }

    private static boolean editVein(boolean large, NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        GameManager manager = GameManager.get();
        Integer slot = parsePositive(payload.id());
        if (manager == null || slot == null) return false;
        if (payload.action().equals("set")) return manager.setSpiritVeinSpawn(large, slot, here);
        if (payload.action().equals("clear")) return manager.setSpiritVeinSpawn(large, slot, null);
        return false;
    }

    private static boolean editPath(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        String[] parts = payload.id().split(":", -1);
        if (parts.length != 2) return false;
        LaneSystem.Lane lane = LaneSystem.Lane.byId(parts[0]);
        Integer slot = parsePositive(parts[1]);
        if (lane == null || slot == null) return false;
        return switch (payload.action()) {
            case "set" -> LaneSystem.setPathPoint(lane, slot, here);
            case "clear" -> LaneSystem.clearPathPoint(lane, slot);
            default -> false;
        };
    }

    private static boolean editTower(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        String[] parts = payload.id().split(":", -1);
        if (parts.length != 3) return false;
        Faction faction = faction(parts[0]);
        LaneSystem.Lane lane = LaneSystem.Lane.byId(parts[1]);
        Integer tier = parsePositive(parts[2]);
        if (faction == null || lane == null || tier == null) return false;
        return switch (payload.action()) {
            case "set" -> LaneSystem.setTowerPoint(faction, lane, tier, here);
            case "clear" -> LaneSystem.clearTowerPoint(faction, lane, tier);
            default -> false;
        };
    }

    private static boolean editFountain(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        Faction faction = faction(payload.id());
        if (faction == null) return false;
        return switch (payload.action()) {
            case "set" -> LaneSystem.setFountainPoint(faction, here);
            case "clear" -> LaneSystem.clearFountainPoint(faction);
            default -> false;
        };
    }

    private static boolean editCrystal(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        Faction faction = faction(payload.id());
        if (faction == null) return false;
        return switch (payload.action()) {
            case "set" -> LaneSystem.setCrystalPoint(faction, here);
            case "clear" -> LaneSystem.clearCrystalPoint(faction);
            default -> false;
        };
    }

    private static boolean editBrush(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        String id = payload.id().toLowerCase(Locale.ROOT);
        return switch (payload.action()) {
            case "corner1" -> VisionSystem.setBrushCorner(id, 1, here);
            case "corner2" -> VisionSystem.setBrushCorner(id, 2, here);
            case "remove" -> VisionSystem.removeBrush(id);
            default -> false;
        };
    }

    private static boolean editMonster(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        String id = payload.id();
        if (payload.action().equals("remove")) return MonsterManager.removeSpawnPoint(id);
        if (!payload.action().equals("set")) return false;
        boolean large = payload.meta().equals("large_random");
        MonsterType type = large ? null : MonsterType.byId(payload.meta());
        return MonsterManager.setSpawnPoint(id, here, type, large);
    }

    private static boolean editMapBound(NetworkSetup.MapFacilityActionPayload payload, BlockPos here) {
        Integer slot = parsePositive(payload.id());
        if (slot == null || slot > MapSystem.CORNER_COUNT) return false;
        return switch (payload.action()) {
            case "set" -> MapSystem.setCorner(slot, here);
            case "clear" -> MapSystem.clearCorner(slot);
            default -> false;
        };
    }

    private static Integer parsePositive(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Faction faction(String id) {
        return Faction.byId(id);
    }

    private static void fail(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.myriad.map_editor_failed"));
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (!canEdit(player, false)) return;
        PacketDistributor.sendToPlayer(player, new NetworkSetup.MapFacilitySnapshotPayload(
                snapshotEntries(player.level().getServer())));
    }

    /** Same authoritative dataset for the UI and regression checks; no coordinates inferred by clients. */
    public static List<NetworkSetup.MapFacilityEntry> snapshotEntries(MinecraftServer server) {
        GameManager manager = GameManager.get();
        if (manager == null || server == null) return List.of();
        List<NetworkSetup.MapFacilityEntry> entries = new ArrayList<>();
        entries.add(entry("arena", MatchArena.active().id,
                MatchArena.available(server, MatchArena.Mode.DUEL) ? "3v3" : "", null, null));
        for (int slot = 1; slot <= MapSystem.CORNER_COUNT; slot++) {
            entries.add(entry("map_bound", Integer.toString(slot), "", MapSystem.corner(slot), null));
        }
        if (manager.spiritVeinsEnabled()) appendVeins(entries, manager.spiritVeinSpawnSnapshot(false), false);
        for (LaneSystem.Lane lane : List.of(LaneSystem.Lane.TOP, LaneSystem.Lane.BOTTOM)) {
            if (!LaneSystem.enabledLanes().contains(lane)) continue;
            entries.add(entry("lane_corner", lane.id(), "", LaneSystem.laneCorner(lane), null));
        }
        for (LaneSystem.Lane lane : LaneSystem.enabledLanes()) {
            for (int slot = 1; slot <= LaneSystem.MAX_PATH_POINTS; slot++) {
                entries.add(entry("lane_path", lane.id() + ":" + slot, "",
                        LaneSystem.pathPoint(lane, slot), null));
            }
        }
        for (Faction faction : Faction.values()) {
            entries.add(entry("crystal", faction.getName(), "", LaneSystem.crystalPoint(faction), null));
            entries.add(entry("fountain", faction.getName(), "",
                    LaneSystem.fountainPoint(faction), null));
            for (LaneSystem.Lane lane : LaneSystem.enabledLanes()) {
                for (int tier : LaneSystem.enabledTowerTiers()) {
                    entries.add(entry("tower", faction.getName() + ":" + lane.id() + ":" + tier, "",
                            LaneSystem.towerPoint(faction, lane, tier), null));
                }
            }
        }
        for (VisionSystem.BrushDraftInfo brush : VisionSystem.brushDraftInfos()) {
            entries.add(entry("brush", brush.id(), "", brush.pos1(), brush.pos2()));
        }
        for (MonsterManager.SpawnPointInfo point : MonsterManager.spawnPointInfos()) {
            entries.add(entry("monster", point.id(), point.typeId(), point.pos(), null));
        }
        return List.copyOf(entries);
    }

    private static void appendVeins(List<NetworkSetup.MapFacilityEntry> entries,
            Map<Integer, BlockPos> positions, boolean large) {
        int count = large ? GameManager.LARGE_VEIN_CANDIDATE_COUNT : GameManager.ATTRIBUTE_VEIN_COUNT;
        String category = large ? "large_vein" : "attribute_vein";
        for (int slot = 1; slot <= count; slot++) {
            entries.add(entry(category, Integer.toString(slot), "", positions.get(slot), null));
        }
    }

    private static NetworkSetup.MapFacilityEntry entry(String category, String id, String meta,
            BlockPos first, BlockPos second) {
        return new NetworkSetup.MapFacilityEntry(category, id, meta,
                first != null, first == null ? 0 : first.getX(), first == null ? 0 : first.getY(),
                first == null ? 0 : first.getZ(), second != null,
                second == null ? 0 : second.getX(), second == null ? 0 : second.getY(),
                second == null ? 0 : second.getZ());
    }
}
