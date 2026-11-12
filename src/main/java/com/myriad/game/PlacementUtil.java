package com.myriad.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 服务端统一选点：视线范围内的方块顶面优先；没有有效命中时，取当前朝向最远距离处
 * 向下找到的地面。预览和确认都调用此方法，避免客户端看到的落点与实际释放不一致。
 */
public final class PlacementUtil {
    private static final double STEP = .25;
    private static final int GROUND_SEARCH_DEPTH = 512;

    private PlacementUtil() { }

    public static Vec3 pointOnGround(ServerPlayer player, double range) {
        ServerLevel level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-6) return null;

        for (double distance = STEP; distance <= range; distance += STEP) {
            BlockPos hit = BlockPos.containing(start.add(direction.scale(distance)));
            if (level.isLoaded(hit) && level.getBlockState(hit).isSolid()) return topCenter(hit);
        }

        BlockPos far = BlockPos.containing(start.add(direction.scale(range)));
        for (int y = far.getY(); y > far.getY() - GROUND_SEARCH_DEPTH; y--) {
            BlockPos ground = new BlockPos(far.getX(), y, far.getZ());
            if (level.isLoaded(ground) && level.getBlockState(ground).isSolid()) return topCenter(ground);
        }
        return null;
    }

    private static Vec3 topCenter(BlockPos pos) {
        return new Vec3(pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5);
    }
}
