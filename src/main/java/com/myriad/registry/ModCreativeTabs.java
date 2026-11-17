package com.myriad.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.myriad.Myriad;

/** 创造模式标签页 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    /** Forces synchronous registration of this class's static entries during mod initialization. */
    public static void initialize() { }
    private static java.util.function.Supplier<CreativeModeTab> register(String name, java.util.function.Supplier<CreativeModeTab> factory) {
        var tab = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB, net.minecraft.resources.Identifier.fromNamespaceAndPath(Myriad.MODID, name), factory.get());
        return () -> tab;
    }

    public static final java.util.function.Supplier<CreativeModeTab> MYRIAD_TAB =
            register("main", () -> net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.myriad"))
                    .icon(() -> new ItemStack(ModItems.SPIRIT_CRYSTAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SPIRIT_CRYSTAL.get());
                        output.accept(ModItems.SPIRIT_CORE_ITEM.get());
                        output.accept(ModItems.DELIVERY_PORTAL_ITEM.get());
                        output.accept(ModItems.BLOOD_PILL.get());
                        output.accept(ModItems.MANA_PILL.get());
                        output.accept(ModItems.SPIRIT_SNARE.get());
                        output.accept(ModItems.SCOUT_TOTEM.get());
                        output.accept(ModItems.TRUE_SIGHT_CHARM.get());
                        output.accept(ModItems.TACTICAL_MAP.get());
                        output.accept(ModItems.SHUO_SWORD.get());
                        output.accept(ModItems.SHUO_SCABBARD.get());
                    })
                    .build());
}
