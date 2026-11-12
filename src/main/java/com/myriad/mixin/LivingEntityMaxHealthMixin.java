package com.myriad.mixin;

import com.myriad.game.ModState;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 服务端玩家最大生命改读模组生命池，不再受原版 RangedAttribute 1024 上限约束。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMaxHealthMixin {
    @Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true)
    private void myriad$useModPlayerMaxHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof ServerPlayer player && ModState.isEnabled()) {
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null && data.isModHealthInitialized() && data.getModMaxHealth() > 0.0F) {
                cir.setReturnValue(data.getModMaxHealth());
            }
        }
    }
}
