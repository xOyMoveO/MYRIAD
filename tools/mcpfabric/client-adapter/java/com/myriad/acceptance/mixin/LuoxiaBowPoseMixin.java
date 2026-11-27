package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.LuoxiaPoseClient;
import com.myriad.network.LuoxiaBowPosePayload;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LuoxiaPoseClient.class, remap = false)
public abstract class LuoxiaBowPoseMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private static void myriad$bowPose(LuoxiaBowPosePayload p, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.getUUID().equals(p.owner())) return;
        var detail = new JsonObject();
        detail.addProperty("owner", p.owner().toString());
        detail.addProperty("phase", p.phase().name());
        detail.addProperty("token", p.token());
        detail.addProperty("skill", p.skill());
        detail.addProperty("work", p.work());
        detail.addProperty("minimum", p.minimum());
        detail.addProperty("maximum", p.maximum());
        detail.addProperty("yaw", p.yaw());
        detail.addProperty("pitch", p.pitch());
        detail.addProperty("serverTick", p.tick());
        detail.addProperty("worldTick", player.level().getGameTime());
        MyriadInputBridge.observePresentation("luoxia_bow_pose", detail);
    }
}
