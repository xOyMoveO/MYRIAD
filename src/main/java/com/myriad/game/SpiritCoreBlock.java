package com.myriad.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 灵源核心：地图中央的争夺点。
 * 旧版灵源核心；对称占点模式下保持禁用以兼容旧地图。
 * 注意：Properties 必须由 DeferredRegister 传入（已设置方块 id），不可自行 Properties.of()。
 */
public class SpiritCoreBlock extends Block {
    public SpiritCoreBlock(BlockBehaviour.Properties properties) {
        super(properties
                .mapColor(MapColor.COLOR_CYAN)
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 12)
                .noOcclusion());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // 周天覆阵引导期间无法使用灵源核心
            if (com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill.isChanneling(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component
                    .translatable("message.myriad.spirit_core_disabled"));
        }
        return InteractionResult.SUCCESS;
    }
}
