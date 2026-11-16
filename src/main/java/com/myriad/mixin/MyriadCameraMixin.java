package com.myriad.mixin;

import com.myriad.MyriadClient;
import com.myriad.client.HanzeFrostView;
import com.myriad.client.ZhoutianChannelView;
import com.myriad.client.platform.CameraAngles;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MyriadCameraMixin {
    @ModifyArgs(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void myriad$channelCamera(Args args) {
        CameraAngles event=new CameraAngles(args.get(0),args.get(1));
        ZhoutianChannelView.onCamera(event);
        HanzeFrostView.onCamera(event);
        args.set(0,event.getYaw());args.set(1,event.getPitch());
    }
    @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
    private void myriad$dashFov(float partial,CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MyriadClient.computeFov(cir.getReturnValue()));
    }
}
