package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.particle.AbilityMeshParticle;
import com.myriad.network.AbilityVfxPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AbilityMeshParticle.class,remap=false)
public abstract class AbilityPresentationMixin {
    @Inject(method="receive",at=@At("HEAD"))
    private static void myriad$observeVfx(AbilityVfxPayload payload,CallbackInfo ci) {
        JsonObject detail=new JsonObject();detail.addProperty("theme",payload.theme());
        detail.addProperty("kind",payload.kind());detail.addProperty("variant",payload.variant());
        detail.addProperty("owner",payload.owner());detail.addProperty("started",payload.started());
        detail.addProperty("life",payload.life());
        MyriadInputBridge.observePresentation("ability_vfx",detail);
    }
}
