package com.myriad.mixin;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void myriad$replaceTab(CallbackInfo ci) {
        if(com.myriad.client.MatchTabOverlay.active()) ci.cancel();
    }
}
