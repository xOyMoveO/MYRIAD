package com.myriad.mixin;

import com.myriad.MyriadClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MyriadNameTagMixin {
    @Inject(method="extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",at=@At("HEAD"),cancellable=true)
    private void myriad$respectSight(Entity entity,EntityRenderState state,float partial,double nameRange,double scoreRange,CallbackInfo ci) {
        if(MyriadClient.hideNameTag(entity)){
            state.nameTag=null;state.scoreText=null;state.nameTagAttachment=null;ci.cancel();
        }
    }
}
