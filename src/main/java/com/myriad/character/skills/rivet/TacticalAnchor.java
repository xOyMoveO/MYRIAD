package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.game.GameManager;
import com.myriad.game.TrainingMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A side-face attachment, never a block entity and never a chunk ticket. */
public final class TacticalAnchor extends RivetSkill {
    public record Anchor(ServerLevel level, BlockPos block, BlockState attached, Vec3 point, Direction normal, long until) { }
    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    private static final Map<UUID, Long> RECOVERY = new HashMap<>();
    public TacticalAnchor(Definition definition) { super(definition); }
    @Override public boolean baseAvailable() { return true; }
    @Override public int getCooldownTicks(int paidRank) { return 160 - 8 * Math.clamp(paidRank, 0, 5); }
    @Override public int cooldownTicks(AbilityRuntime.Cast c) { return getCooldownTicks(c.data.skillLevel(getId())); }
    /** Read-only collision context: even shape-dependent neighbour lookups must not create chunk tickets. */
    static net.minecraft.world.level.BlockGetter loadedBlocks(ServerLevel level) {
        return new net.minecraft.world.level.BlockGetter() {
            private net.minecraft.world.level.chunk.LevelChunk chunk(BlockPos position) {
                return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            }
            @Override public BlockState getBlockState(BlockPos position) {
                var chunk = chunk(position);
                // Unloaded space is opaque to a targeting ray; it cannot select a distant loaded island.
                return chunk == null ? net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState()
                        : chunk.getBlockState(position);
            }
            @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
                var chunk = chunk(position);
                return chunk == null ? net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState()
                        : chunk.getFluidState(position);
            }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) {
                var chunk = chunk(position);
                return chunk == null ? null : chunk.getBlockEntity(position,
                        net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
            }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinY() { return level.getMinY(); }
        };
    }
    private static Anchor aimed(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        var blocks = loadedBlocks(player.level());
        var hit = blocks.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(8)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection().getAxis() == Direction.Axis.Y
                || eye.distanceToSqr(hit.getLocation()) > 64.0001
                || !player.level().getWorldBorder().isWithinBounds(hit.getBlockPos())) return null;
        var chunk = player.level().getChunkSource().getChunkNow(hit.getBlockPos().getX() >> 4, hit.getBlockPos().getZ() >> 4);
        if (chunk == null) return null;
        var block = chunk.getBlockState(hit.getBlockPos());
        if (block.getCollisionShape(blocks, hit.getBlockPos()).isEmpty()) return null;
        Vec3 outward = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(.035);
        return new Anchor(player.level(), hit.getBlockPos().immutable(), block,
                hit.getLocation().add(outward), hit.getDirection(), player.level().getGameTime() + 360);
    }
    @Override public boolean canRelease(AbilityRuntime.Cast c) { return aimed(c.player) != null; }
    @Override public void release(AbilityRuntime.Cast c) {
        Anchor anchor = aimed(c.player);
        if (anchor == null) return;
        ANCHORS.put(c.player.getUUID(), anchor);
        RivetVfx.anchor(c, anchor.point(), Vec3.atLowerCornerOf(anchor.normal().getUnitVec3i()), anchorReady(c.player));
    }
    public static Anchor anchor(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player.getUUID());
        if (anchor == null) return null;
        if (anchor.level() != player.level() || player.level().getGameTime() >= anchor.until()) {
            ANCHORS.remove(player.getUUID()); return null;
        }
        // Level.getBlockState refreshes an UNKNOWN ticket even after hasChunkAt succeeds.
        // Read the already loaded chunk directly, retaining the original lifetime while inactive.
        var chunk = player.level().getChunkSource().getChunkNow(anchor.block().getX() >> 4, anchor.block().getZ() >> 4);
        if (chunk == null) return null;
        var block = chunk.getBlockState(anchor.block());
        if (block.getBlock() != anchor.attached().getBlock()
                || block.getCollisionShape(loadedBlocks(player.level()), anchor.block()).isEmpty()) {
            ANCHORS.remove(player.getUUID()); return null;
        }
        return anchor;
    }
    public static int remaining(ServerPlayer player) {
        var anchor = ANCHORS.get(player.getUUID());
        return anchor == null ? 0 : (int) Math.max(0, anchor.until() - player.level().getGameTime());
    }
    public static int recovery(ServerPlayer player) {
        return (int) Math.max(0, RECOVERY.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime());
    }
    public static boolean anchorReady(ServerPlayer player) {
        return anchor(player) != null && recovery(player) == 0 && (TrainingMode.isEnabled()
                || GameManager.get() != null && GameManager.get().getPhase() == GameManager.Phase.PLAYING);
    }
    public static boolean consumeStrike(ServerPlayer player, Vec3 wallContact) {
        var anchor = anchor(player);
        if (anchor == null || !anchorReady(player) || anchor.point().distanceToSqr(wallContact) > 2.250001) return false;
        RECOVERY.put(player.getUUID(), player.level().getGameTime() + 100);
        return true;
    }
    public static void tick(ServerPlayer player) {
        var anchor = anchor(player);
        var data = com.myriad.player.PlayerDataProvider.get(player);
        data.setBuff("g1_rivet_anchor_remaining", remaining(player));
        data.setBuff("g1_rivet_anchor_recovery", recovery(player));
        data.setBuff("g1_rivet_anchor_active", anchor == null ? 0 : 1);
        if (anchor == null) { RivetVfx.anchorInactive(player); return; }
        if (player.level().getGameTime() % 4 != 0) return;
        var skill = FirstGroupSkills.node("rivet", 3);
        if (skill != null) RivetVfx.anchor(AbilityRuntime.context(player, skill), anchor.point(),
                Vec3.atLowerCornerOf(anchor.normal().getUnitVec3i()), anchorReady(player));
    }
    public static void clear(ServerPlayer player) {
        ANCHORS.remove(player.getUUID()); RECOVERY.remove(player.getUUID());
        var data = com.myriad.player.PlayerDataProvider.get(player);
        data.setBuff("g1_rivet_anchor_remaining", 0);
        data.setBuff("g1_rivet_anchor_recovery", 0);
        data.setBuff("g1_rivet_anchor_active", 0);
    }
}
