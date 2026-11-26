package com.myriad.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native doTick simulates then restores the last accepted client position. Observe only after that restore. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerConnectionTickMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="tickPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V",shift=At.Shift.AFTER))
    private void myriad$acceptedPlayerTick(CallbackInfoReturnable<Boolean> cir) {
        com.myriad.player.PlayerDataProvider.onPlayerTick(player);
        com.myriad.combat.VanillaMeleeAttack.tickBuffered(player);
    }
}
