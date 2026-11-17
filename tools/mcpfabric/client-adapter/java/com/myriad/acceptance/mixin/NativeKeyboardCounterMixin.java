package com.myriad.acceptance.mixin;

import com.myriad.acceptance.MyriadInputBridge;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observation at the real handler, including native UI key dispatch. */
@Mixin(KeyboardHandler.class)
public abstract class NativeKeyboardCounterMixin {
    @Inject(method="keyPress",at=@At("HEAD"))
    private void myriad$observeKey(CallbackInfo ci) { MyriadInputBridge.observedKey(); }
}
