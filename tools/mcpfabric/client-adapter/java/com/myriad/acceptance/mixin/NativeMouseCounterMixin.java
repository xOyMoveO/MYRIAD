package com.myriad.acceptance.mixin;

import com.myriad.acceptance.MyriadInputBridge;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** High priority observes even clicks consumed by gameplay placement interception. */
@Mixin(value=MouseHandler.class,priority=1100)
public abstract class NativeMouseCounterMixin {
    @Inject(method="onButton",at=@At("HEAD"))
    private void myriad$observeMouse(CallbackInfo ci) { MyriadInputBridge.observedMouse(); }
}
