package com.myriad.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandLayer.class)
public abstract class ShuoHeldLightMixin {
    @SuppressWarnings({"unchecked","rawtypes"})
    @Inject(method="submitArmWithItem",at=@At("HEAD"),cancellable=true)
    private void myriad$closedLeftHand(ArmedEntityRenderState state,ItemStackRenderState rendered,ItemStack item,HumanoidArm arm,PoseStack stack,SubmitNodeCollector collector,int light,CallbackInfo ci){
        var layer=(ItemInHandLayer<?,?>)(Object)this;
        if(com.myriad.client.firstperson.ShuoHeldView.submit((net.minecraft.client.model.ArmedModel)layer.getParentModel(),state,item,arm,stack,collector,light))ci.cancel();
    }
    @Inject(method="submitArmWithItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void myriad$bladeLight(ArmedEntityRenderState state,ItemStackRenderState rendered,ItemStack item,HumanoidArm arm,PoseStack stack,SubmitNodeCollector collector,int light,CallbackInfo ci){
        com.myriad.client.firstperson.ShuoHeldLight.submit(state,item,arm,stack,collector);
    }
}
