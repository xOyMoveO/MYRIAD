package com.myriad.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerDropMixin {
    @Inject(method="drop",at=@At("RETURN"),cancellable=true)
    private void myriad$drop(ItemStack stack,boolean random,Prediction prediction,CallbackInfoReturnable<ItemEntity> cir) {
        if((Object)this instanceof Player player && !player.level().isClientSide() && cir.getReturnValue()!=null && com.myriad.player.PlayerDataProvider.onItemToss(player,cir.getReturnValue()))cir.setReturnValue(null);
    }
}
