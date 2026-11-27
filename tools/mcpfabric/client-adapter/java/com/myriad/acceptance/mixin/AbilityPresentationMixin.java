package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.character.skills.rivet.RivetVfx;
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
        detail.addProperty("angle", payload.angle());
        detail.addProperty("personal", payload.personal());
        MyriadInputBridge.observePresentation("ability_vfx",detail);
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null && client.player.getId() == payload.owner() && payload.valid()
                && RivetVfx.basicContact(payload)) {
            var contact = new JsonObject();
            contact.addProperty("owner", payload.owner());
            contact.addProperty("serverTick", payload.started());
            MyriadInputBridge.observePresentation("rivet_basic_contact", contact);
        }
    }
}
