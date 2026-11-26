package com.myriad.client;

import com.myriad.game.DefenseTowerEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/** Local range markers. The only world objects considered are tracked, faction-tagged tower entities. */
public final class TowerRangeRings {
    private static final double RANGE = DefenseTowerEntity.ATTACK_RANGE;
    private static final int POINTS = 72, PERIOD = 9, MAX_CIRCLES = 3;
    private static final DustParticleOptions COPPER = new DustParticleOptions(0xBD6549, 1.20f);
    private static final DustParticleOptions AMBER = new DustParticleOptions(0xD59C56, 1.15f);
    private static final Map<DefenseTowerEntity, Ring> rings = new HashMap<>();
    private static ClientLevel world;
    private static Entity camera;
    private static int faction;

    private TowerRangeRings() {}

    public static void tick(Minecraft mc) {
        var data = ClientDataHolder.currentPlayerSnapshot(mc);
        var eye = mc.getCameraEntity();
        boolean follow = mc.player != null && mc.player.isSpectator() && eye != mc.player
                && eye instanceof Player ally && ally.isAlive() && !ally.isSpectator()
                && data != null && data.buffs().getOrDefault("hero_respawn", 0) > 0;
        boolean active = data != null && data.modEnabled() && data.combatActive() && data.factionId() > 0
                && (follow || eye == mc.player && mc.player.isAlive() && !mc.player.isSpectator());
        int team = active ? data.factionId() : 0;
        if (world != mc.level || camera != eye || team != faction || !active || mc.isPaused()) rings.clear();
        world = mc.level;
        camera = eye;
        faction = team;
        if (!active || mc.isPaused() || world.getGameTime() % PERIOD != 0) return;

        var nearby = new ArrayList<DefenseTowerEntity>();
        for (Entity entity : world.entitiesForRendering()) {
            if (!(entity instanceof DefenseTowerEntity tower) || !tower.isAlive()
                    || tower.towerFaction() == 0 || tower.towerFaction() == team) continue;
            double limit = RANGE + (rings.containsKey(tower) ? 10 : 8);
            if (horizontalDistance(tower, eye) <= limit * limit) nearby.add(tower);
        }
        nearby.sort(Comparator.comparingDouble(t -> horizontalDistance(t, eye)));
        if (nearby.size() > MAX_CIRCLES) nearby.subList(MAX_CIRCLES, nearby.size()).clear();
        rings.keySet().retainAll(nearby);
        int stride = mc.options.particles().get() == ParticleStatus.MINIMAL ? 2 : 1;
        for (var tower : nearby) {
            var ring = rings.computeIfAbsent(tower, ignored -> new Ring());
            if (!tower.position().equals(ring.center) || world.getGameTime() >= ring.refresh) {
                ring.sample(world, tower);
            }
            for (int i = 0; i < POINTS; i += stride) {
                var p = ring.points[i];
                if (p == null || !loaded(world, Mth.floor(p.x), Mth.floor(p.z))) continue;
                var dot = mc.particleEngine.createParticle((i / stride) % 4 == 0 ? AMBER : COPPER,
                        p.x, p.y, p.z, 0, 0, 0);
                if (dot != null) {
                    dot.setParticleSpeed(0, 0, 0);
                    dot.setLifetime(PERIOD + 2);
                }
            }
        }
    }

    private static double horizontalDistance(Entity a, Entity b) {
        double x = a.getX() - b.getX(), z = a.getZ() - b.getZ();
        return x * x + z * z;
    }

    private static final class Ring {
        final Vec3[] points = new Vec3[POINTS];
        Vec3 center;
        long refresh;

        void sample(ClientLevel level, DefenseTowerEntity tower) {
            center = tower.position();
            refresh = level.getGameTime() + 54 + Math.floorMod(tower.getId(), 18);
            var ground = reachableGround(level, center);
            for (int i = 0; i < POINTS; i++) {
                double angle = Math.TAU * i / POINTS;
                double x = center.x + RANGE * Math.cos(angle), z = center.z + RANGE * Math.sin(angle);
                Double nearby = ground.get(BlockPos.asLong(Mth.floor(x), 0, Mth.floor(z)));
                double y = nearby == null ? Double.NaN : floor(level, x, z, nearby);
                points[i] = Double.isFinite(y) ? new Vec3(x, y + .075, z) : null;
            }
        }
    }

    // Only follow walkable surfaces connected to the tower's platform. A column heightmap
    // would jump to rooftops and to the tops of tall walls on multi-level maps.
    private static Map<Long, Double> reachableGround(ClientLevel level, Vec3 center) {
        Map<Long, Double> heights = new HashMap<>();
        var queue = new ArrayDeque<BlockPos>();
        var origin = BlockPos.containing(center.x, 0, center.z);
        double start = floor(level, center.x, center.z, center.y);
        if (!Double.isFinite(start)) return heights;
        heights.put(origin.asLong(), start);
        queue.add(origin);
        while (!queue.isEmpty()) {
            var from = queue.removeFirst();
            double y = heights.get(from.asLong());
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var next = from.relative(dir);
                double x = next.getX() + .5, z = next.getZ() + .5;
                if (Math.hypot(x - center.x, z - center.z) > RANGE + 1.5 || heights.containsKey(next.asLong())) continue;
                double h = floor(level, x, z, y);
                if (!Double.isFinite(h) || Math.abs(h - center.y) > 4) continue;
                heights.put(next.asLong(), h);
                queue.addLast(next);
            }
        }
        return heights;
    }

    private static boolean loaded(ClientLevel level, int blockX, int blockZ) {
        return level.getChunkSource().getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false) != null;
    }

    private static double floor(ClientLevel level, double x, double z, double previous) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!loaded(level, bx, bz)) return Double.NaN;
        double best = Double.NaN, error = Double.POSITIVE_INFINITY;
        for (int by = Mth.floor(previous) - 2; by <= Mth.floor(previous) + 1; by++) {
            var pos = new BlockPos(bx, by, bz);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            for (var box : shape.toAabbs()) {
                if (x - bx < box.minX || x - bx >= box.maxX || z - bz < box.minZ || z - bz >= box.maxZ) continue;
                double top = by + box.maxY, delta = Math.abs(top - previous);
                if (delta > 1.001 || delta >= error) continue;
                var clearance = new net.minecraft.world.phys.AABB(x-.02, top+.001, z-.02, x+.02, top+1.8, z+.02);
                if (!level.noBlockCollision(null, clearance)) continue;
                best = top;
                error = delta;
            }
        }
        return best;
    }
}
