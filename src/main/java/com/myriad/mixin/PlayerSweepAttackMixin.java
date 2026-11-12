package com.myriad.mixin;

import com.myriad.game.ModState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仙凡劫玩法启用时禁用原版剑横扫；角色自定义穿透和范围技能仍由各自逻辑处理。 */
@Mixin(Player.class)
public abstract class PlayerSweepAttackMixin {
    @Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
    private void myriad$disableVanillaSweep(boolean fullStrengthAttack, boolean criticalAttack,
            boolean knockbackAttack, CallbackInfoReturnable<Boolean> callback) {
        if (ModState.isEnabled()) {
            callback.setReturnValue(false);
        }
    }
}
