package com.myriad.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.myriad.Myriad;
import com.myriad.game.DeliveryPortalBlock;
import com.myriad.game.SpiritCoreBlock;

/** 玩法方块注册 */
public final class ModBlocks {
    private ModBlocks() {}

    /** Forces synchronous registration of this class's static entries during mod initialization. */
    public static void initialize() { }
    private static <T extends Block> java.util.function.Supplier<T> registerBlock(String id, java.util.function.Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, T> factory) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath(Myriad.MODID, id));
        T block = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK, key, factory.apply(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(key)));
        return () -> block;
    }

    /** 灵源核心：中央争夺点 */
    public static final java.util.function.Supplier<SpiritCoreBlock> SPIRIT_CORE =
            registerBlock("spirit_core", SpiritCoreBlock::new);

    /** 旧版传送阵：守护交付点（仅旧地图兼容） */
    public static final java.util.function.Supplier<DeliveryPortalBlock> DELIVERY_PORTAL =
            registerBlock("delivery_portal", DeliveryPortalBlock::new);

    public static Block[] allBlocks() {
        return new Block[]{SPIRIT_CORE.get(), DELIVERY_PORTAL.get()};
    }
}
