package com.myriad.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.myriad.Myriad;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 创造模式标签页 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Myriad.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MYRIAD_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
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
