package com.myriad.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/** Use is started by the verified primary-attack controller, never the vanilla right-click projectile. */
public final class GuixiaBowItem extends Item {
    public GuixiaBowItem(Properties properties) {
        super(properties.stacksTo(1).component(net.minecraft.core.component.DataComponents.USE_EFFECTS,
                new net.minecraft.world.item.component.UseEffects(true, false, 1)));
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
}
