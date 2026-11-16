package com.myriad.mixin;

import com.myriad.platform.ServerCallbacks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class ServerPlayerTickMixin {
    @Inject(method="tick",at=@At("HEAD")) private void myriad$before(CallbackInfo ci){if((Object)this instanceof ServerPlayer player)ServerCallbacks.beforePlayer(player);}
}
