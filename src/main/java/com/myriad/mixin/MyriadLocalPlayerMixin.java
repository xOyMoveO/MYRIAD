package com.myriad.mixin;

import com.myriad.MyriadClient;
import com.myriad.client.SkillChargeClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MyriadLocalPlayerMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void myriad$beforeMovement(CallbackInfo ci) { MyriadClient.beforePlayerTick((LocalPlayer)(Object)this); }
    @Inject(method="tick",at=@At("TAIL"))
    private void myriad$afterMovement(CallbackInfo ci) { MyriadClient.afterPlayerTick((LocalPlayer)(Object)this); }
    @Inject(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/ClientInput;tick()V",shift=At.Shift.AFTER))
    private void myriad$chargeMovement(CallbackInfo ci) { SkillChargeClient.input(((LocalPlayer)(Object)this).input); }
}
