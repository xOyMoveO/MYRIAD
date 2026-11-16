package com.myriad.mixin;

import com.myriad.MyriadClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MyriadNameTagMixin {
    @Inject(method="getNameTag",at=@At("HEAD"),cancellable=true)
    private void myriad$respectSight(Entity entity,CallbackInfoReturnable<Component> cir) {
        if(MyriadClient.hideNameTag(entity))cir.setReturnValue(null);
    }
}
