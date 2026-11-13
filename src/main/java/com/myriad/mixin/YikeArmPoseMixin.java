package com.myriad.mixin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class YikeArmPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
    private void myriad$yikeGrip(HumanoidRenderState state,CallbackInfo ci){com.myriad.client.firstperson.YikeWeaponView.pose((HumanoidModel<?>)(Object)this,state);}
}
