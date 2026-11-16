package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.firstperson.ShuoView;
import com.myriad.network.ShuoViewPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ShuoView.class,remap=false)
public abstract class ShuoPresentationMixin {
    @Inject(method="receive",at=@At("HEAD"))
    private static void myriad$observeMotion(ShuoViewPayload payload,CallbackInfo ci) {
        JsonObject detail=new JsonObject();detail.addProperty("action",payload.action());
        detail.addProperty("started",payload.started());
        MyriadInputBridge.observePresentation("shuo_motion",detail);
    }
}
