package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.MyriadClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MyriadClient.class,remap=false)
public abstract class BufferedMeleeMixin {
    @Inject(method="submitBufferedMelee",at=@At("HEAD"))
    private static void myriad$submitted(Minecraft client,CallbackInfo ci) {
        var detail=new JsonObject();
        detail.addProperty("worldTick",client.level.getGameTime());
        MyriadInputBridge.observePresentation("buffer_submitted",detail);
    }
}
