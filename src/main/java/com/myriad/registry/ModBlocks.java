package com.myriad.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.myriad.Myriad;
import com.myriad.game.DeliveryPortalBlock;
import com.myriad.game.SpiritCoreBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 玩法方块注册 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Myriad.MODID);

    /** 灵源核心：中央争夺点 */
    public static final DeferredBlock<SpiritCoreBlock> SPIRIT_CORE =
            BLOCKS.registerBlock("spirit_core", SpiritCoreBlock::new);

    /** 旧版传送阵：玄霄宗交付点（仅旧地图兼容） */
    public static final DeferredBlock<DeliveryPortalBlock> DELIVERY_PORTAL =
            BLOCKS.registerBlock("delivery_portal", DeliveryPortalBlock::new);

    public static Block[] allBlocks() {
        return new Block[]{SPIRIT_CORE.get(), DELIVERY_PORTAL.get()};
    }
}
