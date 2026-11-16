package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundPresentationMixin {
    @Inject(method="play",at=@At("RETURN"))
    private void myriad$observeSound(SoundInstance sound,CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if(!sound.getIdentifier().getNamespace().equals("myriad"))return;
        JsonObject detail=new JsonObject();detail.addProperty("sound",sound.getIdentifier().toString());
        detail.addProperty("volume",sound.getVolume());detail.addProperty("pitch",sound.getPitch());
        detail.addProperty("source",sound.getSource().getName());detail.addProperty("attenuation",sound.getAttenuation().name());
        detail.addProperty("result",cir.getReturnValue().toString());
        MyriadInputBridge.observePresentation("sound_submission",detail);
    }
}
