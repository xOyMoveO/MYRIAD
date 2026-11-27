package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.LuoxiaPoseClient;
import com.myriad.network.LuoxiaBowPosePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LuoxiaPoseClient.class, remap = false)
public abstract class LuoxiaPresentationMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private static void myriad$observeBow(LuoxiaBowPosePayload pose, CallbackInfo ci) {
        var detail = new JsonObject();
        detail.addProperty("token", pose.token());
        detail.addProperty("skill", pose.skill());
        detail.addProperty("phase", pose.phase().name());
        detail.addProperty("work", pose.work());
        detail.addProperty("minimum", pose.minimum());
        detail.addProperty("maximum", pose.maximum());
        MyriadInputBridge.observePresentation("luoxia_bow", detail);
    }
}
