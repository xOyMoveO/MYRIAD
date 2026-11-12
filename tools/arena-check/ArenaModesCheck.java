package com.myriad.check;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.game.Faction;
import com.myriad.game.LaneSystem;
import com.myriad.game.MatchArena;
import com.myriad.network.NetworkSetup;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.InflaterInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.storage.LevelData;

/** Real Minecraft codecs, actual distributed map, and isolated lane/packet regressions. */
public final class ArenaModesCheck {
    private static int checks;
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); checks++; }
    private static Object field(String name) throws Exception {
        var f = LaneSystem.class.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    private static BlockPos position(JsonObject p) { return new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt()); }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Path world = Path.of(args[0]);
        check(MatchArena.active() == MatchArena.Mode.RIFT, "Legacy default must be 5v5");
        check(MatchArena.Mode.byId("../3v3") == null && MatchArena.Mode.byId("typo") == null, "Unknown modes/path traversal rejected");
        check(!MatchArena.select(null, "typo"), "Invalid network mode rejected without mutation");
        check(MatchArena.Mode.DUEL.teamSize == 3 && MatchArena.Mode.RIFT.teamSize == 5, "Team limits");
        check(MatchArena.configuration(world, MatchArena.Mode.RIFT, "myriad_lane_layout.json").equals(world.resolve("myriad_lane_layout.json")), "Legacy config path unchanged");
        check(MatchArena.singleLaneReady(world), "Distributed combined save is usable");
        check(!MatchArena.singleLaneReady(world.resolve("not-a-world")), "Missing profile rejected");
        var layout = JsonParser.parseString(Files.readString(MatchArena.configuration(world, MatchArena.Mode.DUEL, "myriad_lane_layout.json"))).getAsJsonObject();
        var enabled = LaneSystem.class.getDeclaredMethod("readEnabledLanes", JsonObject.class); enabled.setAccessible(true);
        enabled.invoke(null, layout);
        check(((Set<?>)field("ENABLED_LANES")).equals(Set.of(LaneSystem.Lane.MID)), "Single active lane");
        Map<Faction, BlockPos> fountains = (Map<Faction, BlockPos>)field("FOUNTAIN_POINTS");
        Map<Faction, BlockPos> crystals = (Map<Faction, BlockPos>)field("CRYSTAL_POINTS");
        for (var p : layout.getAsJsonArray("fountains")) fountains.put(Faction.byId(p.getAsJsonObject().get("faction").getAsString()), position(p.getAsJsonObject()));
        for (var p : layout.getAsJsonArray("crystals")) crystals.put(Faction.byId(p.getAsJsonObject().get("faction").getAsString()), position(p.getAsJsonObject()));
        var keyClass = Class.forName("com.myriad.game.LaneSystem$TowerKey");
        var constructor = keyClass.getDeclaredConstructor(Faction.class, LaneSystem.Lane.class, int.class); constructor.setAccessible(true);
        Map<Object, BlockPos> towers = (Map<Object, BlockPos>)field("TOWER_POINTS");
        for (var value : layout.getAsJsonArray("towers")) {
            var p = value.getAsJsonObject();
            towers.put(constructor.newInstance(Faction.byId(p.get("faction").getAsString()), LaneSystem.Lane.MID, p.get("tier").getAsInt()), position(p));
        }
        var routeMethod = LaneSystem.class.getDeclaredMethod("orderedPath", LaneSystem.Lane.class, Faction.class); routeMethod.setAccessible(true);
        for (Faction faction : Faction.values()) {
            check(((List<?>)routeMethod.invoke(null, LaneSystem.Lane.TOP, faction)).isEmpty(), "No fallback top lane in 3v3");
            check(((List<?>)routeMethod.invoke(null, LaneSystem.Lane.BOTTOM, faction)).isEmpty(), "No fallback bottom lane in 3v3");
            var route = (List<BlockPos>)routeMethod.invoke(null, LaneSystem.Lane.MID, faction);
            check(route.size() == layout.getAsJsonArray("towers").size()+2 && route.getFirst().equals(fountains.get(faction)), "Own-base-to-enemy-crystal route");
            int lastX = route.getFirst().getX();
            for (BlockPos p : route.subList(1, route.size())) {
                check(faction == Faction.YOUMING ? p.getX() > lastX : p.getX() < lastX, "No backwards bend in single lane");
                lastX = p.getX();
            }
        }
        var ready = LaneSystem.class.getDeclaredMethod("towerLayoutReady"); ready.setAccessible(true);
        check((boolean)ready.invoke(null), "All configured 3v3 tower tiers are present");
        var expectedTiers = new ArrayList<Integer>();
        for (var value : layout.getAsJsonArray("towerTiers")) expectedTiers.add(value.getAsInt());
        expectedTiers.sort(Integer::compareTo);
        check(LaneSystem.enabledTowerTiers().equals(expectedTiers), "Configured tower tier selection");
        var destroyed = (Set<String>)field("DESTROYED_TOWERS");
        var siege = LaneSystem.class.getDeclaredMethod("siegeUnlocked",Faction.class,LaneSystem.Lane.class); siege.setAccessible(true);
        var vulnerable = LaneSystem.class.getDeclaredMethod("crystalVulnerable",Faction.class); vulnerable.setAccessible(true);
        check(!(boolean)vulnerable.invoke(null,Faction.XUANXIAO), "Crystal protected before towers fall");
        destroyed.add("xuanxiao/mid/1");
        check(!(boolean)vulnerable.invoke(null,Faction.XUANXIAO), "One destroyed tower does not unlock crystal");
        for (int tier : expectedTiers) destroyed.add("xuanxiao/mid/" + tier);
        check((boolean)vulnerable.invoke(null,Faction.XUANXIAO), "All configured towers destroyed unlock 3v3 crystal");
        check(!(boolean)siege.invoke(null,Faction.YOUMING,LaneSystem.Lane.TOP), "Disabled lane cannot unlock siege");
        destroyed.clear();
        check(LaneSystem.minimapPaths().stream().allMatch(p -> p.lane() == LaneSystem.Lane.MID.ordinal()), "Minimap has only active route");
        enabled.invoke(null, new JsonObject());
        check(((Set<?>)field("ENABLED_LANES")).size() == 3, "Switch back to legacy map restores three lanes");
        check(!(boolean)ready.invoke(null), "5v5 readiness still requires all eighteen towers");

        for (String arena : List.of("5v5", "3v3")) for (int mode : List.of(0, 1)) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var payload = new NetworkSetup.StartMatchPayload(mode, arena);
                NetworkSetup.StartMatchPayload.STREAM_CODEC.encode(buf, payload);
                check(NetworkSetup.StartMatchPayload.STREAM_CODEC.decode(buf).equals(payload) && !buf.isReadable(), "Start packet roundtrip");
            } finally { buf.release(); }
        }
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var payload = new NetworkSetup.TeamListPayload(Map.of("tester", 1), "3v3", true);
            NetworkSetup.TeamListPayload.STREAM_CODEC.encode(buf, payload);
            check(NetworkSetup.TeamListPayload.STREAM_CODEC.decode(buf).equals(payload), "Team/availability packet roundtrip");
        } finally { buf.release(); }
        buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var payload = new NetworkSetup.SelectArenaPayload("3v3");
            NetworkSetup.SelectArenaPayload.CODEC.encode(buf, payload);
            check(NetworkSetup.SelectArenaPayload.CODEC.decode(buf).equals(payload), "Selection packet roundtrip");
        } finally { buf.release(); }

        var codec = PalettedContainer.codecRW(BlockState.CODEC,
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState());
        buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var markers = List.of(new NetworkSetup.MinimapMarker(8, 0, 4209.5F, 70.5F, new UUID(0,0)),
                    new NetworkSetup.MinimapMarker(9, 0, 4271.5F, 204.5F, new UUID(0,0)),
                    new NetworkSetup.MinimapMarker(10, 2, 4128.5F, 96.5F, new UUID(0,0)));
            var payload = new NetworkSetup.MinimapSnapshotPayload(true, 4096.5F, .5F, 4447.5F, .5F,
                    4447.5F, 255.5F, 4096.5F, 255.5F, List.of(), markers);
            NetworkSetup.MinimapSnapshotPayload.STREAM_CODEC.encode(buf, payload);
            check(NetworkSetup.MinimapSnapshotPayload.STREAM_CODEC.decode(buf).equals(payload) && !buf.isReadable(),
                    "Camp/boss/fountain coordinates roundtrip without protocol changes");
        } finally { buf.release(); }
        var data = NbtIo.readCompressed(world.resolve("level.dat"), NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
        LevelData.RespawnData.CODEC.parse(NbtOps.INSTANCE, data.getCompoundOrEmpty("spawn")).getOrThrow();
        int chunks = 0, sections = 0;
        try (var file = new RandomAccessFile(world.resolve("dimensions/minecraft/overworld/region/r.8.0.mca").toFile(), "r")) {
            byte[] header = new byte[4096]; file.readFully(header);
            for (int slot = 0; slot < 1024; slot++) {
                int p = slot * 4, offset = ((header[p]&255)<<16) | ((header[p+1]&255)<<8) | (header[p+2]&255);
                file.seek((long)offset*4096); int length = file.readInt(); check(file.readByte() == 2, "Zlib chunk compression");
                byte[] compressed = new byte[length-1]; file.readFully(compressed);
                var chunk = NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed))), NbtAccounter.unlimitedHeap());
                check(chunk.getIntOr("xPos", -1) == 256+slot%32 && chunk.getIntOr("zPos", -1) == slot/32, "Translated chunk coordinates");
                for (Tag tag : chunk.getListOrEmpty("sections")) {
                    codec.parse(NbtOps.INSTANCE, ((CompoundTag)tag).getCompoundOrEmpty("block_states")).getOrThrow(); sections++;
                }
                chunks++;
            }
        }
        check(chunks == 1024 && sections == 24576, "Complete arena and void buffer");
        System.out.println("ARENA_CHECK_PASS checks=" + checks + " chunks=" + chunks + " sections=" + sections);
    }
}
