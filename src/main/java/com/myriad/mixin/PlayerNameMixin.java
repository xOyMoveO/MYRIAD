package com.myriad.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerNameMixin {
    @Inject(method="getTabListDisplayName",at=@At("HEAD"),cancellable=true)
    private void myriad$name(CallbackInfoReturnable<Component> cir) {
        var display=com.myriad.player.PlayerDataProvider.tabListName((ServerPlayer)(Object)this);
        if(display!=null)cir.setReturnValue(display);
    }
}
