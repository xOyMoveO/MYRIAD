package com.myriad.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.firstperson.RivetImpactCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class RivetWorldRollMixin {
    // renderLevel builds its own identity pose here, before vanilla hurt and walking bob.
    // renderItemInHand calls bobHurt with a different stack.
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
            index = 1)
    private PoseStack myriad$rollWorldForRivetSmash(PoseStack stack) {
        RivetImpactCamera.applyToWorld(stack);
        return stack;
    }
}
