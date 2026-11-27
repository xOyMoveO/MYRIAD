package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.firstperson.RivetHammerContact;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RivetHammerContact.class, remap = false)
public abstract class RivetHammerContactMixin {
    @Shadow private double start, opens, closes, scheduled, impact, previousLatestStamp, lastSample;

    private JsonObject myriad$window() {
        var detail = new JsonObject();
        if (Double.isFinite(start)) {
            detail.addProperty("swingLevelTick", start);
            detail.addProperty("opensAt", opens);
            detail.addProperty("closesAt", closes);
        }
        if (Double.isFinite(previousLatestStamp)) detail.addProperty("previousLatestStamp", previousLatestStamp);
        if (Double.isFinite(lastSample)) detail.addProperty("lastSample", lastSample);
        if (Double.isFinite(scheduled)) detail.addProperty("scheduledAt", scheduled);
        if (Double.isFinite(impact)) detail.addProperty("appliedAt", impact);
        return detail;
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void myriad$started(double now, double opensAt, double closesAt, CallbackInfo ci) {
        MyriadInputBridge.observePresentation("rivet_hammer_start", myriad$window());
    }

    @Inject(method = "receive", at = @At("RETURN"))
    private void myriad$receipt(long serverTick, double now, CallbackInfo ci) {
        var detail = myriad$window();
        detail.addProperty("serverTick", serverTick);
        detail.addProperty("receivedAt", now);
        MyriadInputBridge.observePresentation("rivet_hammer_receipt", detail);
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void myriad$cancelled(CallbackInfo ci) {
        if (Double.isFinite(start)) MyriadInputBridge.observePresentation("rivet_hammer_cancel", myriad$window());
    }

    @Inject(method = "beginImpact", at = @At("RETURN"))
    private void myriad$applied(double at, long serverTick, CallbackInfo ci) {
        var detail = new JsonObject();
        detail.addProperty("serverTick", serverTick);
        detail.addProperty("impactLevelTick", at);
        MyriadInputBridge.observePresentation("rivet_hammer_hitstop", detail);
    }
}
