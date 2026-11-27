package com.myriad.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.MyriadClient;
import com.myriad.client.firstperson.FirstPersonArmVisibility;
import com.myriad.client.platform.HandRenderContext;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MyriadHandRendererMixin {
    // Empty hands and both map layouts use this same skin-only draw call.
    @Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
    private void myriad$hideVanillaHand(CallbackInfo ci) {
        if (FirstPersonArmVisibility.hidePlayerArms()) ci.cancel();
    }

    @Inject(method="submitArmWithItem",at=@At("HEAD"),cancellable=true)
    private void myriad$authoredHands(PlayerRenderState player,FirstPersonHandsAndItemsRenderState hands,
            float partial,float pitch,InteractionHand hand,float swing,ItemStack item,float height,
            PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci) {
        if(hands.isScoping||player.avatarRenderState==null)return;
        HandRenderContext event=new HandRenderContext(hand,item,pose,collector,light,partial,height,swing);
        MyriadClient.onRenderHand(event);
        if(event.isCanceled())ci.cancel();
    }
}
