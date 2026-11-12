package com.myriad.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Short-lived simulation tickets: only active combatants keep nearby chunks ticking. */
public final class CombatChunks {
    private CombatChunks() {}
    public static final DeferredRegister<TicketType> TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, "myriad");
    private static final java.util.function.Supplier<TicketType> COMBAT = TYPES.register("combat",
            () -> new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static void hold(ServerLevel level, BlockPos pos) {
        level.getChunkSource().addTicketWithRadius(COMBAT.get(), new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), 2);
    }
    public static void prepare(ServerLevel level, BlockPos pos) {
        hold(level, pos);
        level.getChunk(pos);
    }

    /**
     * 对局期间持续保持一整条兵线路径处于加载和模拟状态。
     * 路段按最多12格取样并去重到区块，半径1足够覆盖道路两侧且不会加载整片地图。
     */
    public static void holdLaneRoute(ServerLevel level, java.util.List<BlockPos> route) {
        if (route == null || route.isEmpty()) return;
        java.util.Set<ChunkPos> chunks = new java.util.HashSet<>();
        if (route.size() == 1) {
            BlockPos point = route.getFirst();
            chunks.add(new ChunkPos(point.getX() >> 4, point.getZ() >> 4));
        }
        for (int index = 1; index < route.size(); index++) {
            BlockPos from = route.get(index - 1);
            BlockPos to = route.get(index);
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 12.0));
            for (int sample = 0; sample <= samples; sample++) {
                double t = sample / (double) samples;
                int x = (int) Math.floor(from.getX() + dx * t);
                int z = (int) Math.floor(from.getZ() + dz * t);
                chunks.add(new ChunkPos(x >> 4, z >> 4));
            }
        }
        for (ChunkPos chunk : chunks) {
            level.getChunkSource().addTicketWithRadius(COMBAT.get(), chunk, 1);
        }
    }
}
