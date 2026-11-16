package com.myriad.mixin;

import com.myriad.MyriadClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MyriadMinecraftClientMixin {
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void myriad$authoritativeAttack(CallbackInfoReturnable<Boolean> cir) {
        if(MyriadClient.attack())cir.setReturnValue(false);
    }
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void myriad$noCustomAttackMining(boolean held,CallbackInfo ci) {
        if(!com.myriad.client.ClientDataHolder.modEnabled()
                || com.myriad.client.ClientDataHolder.characterId().isEmpty())return;
        if(com.myriad.client.RespawnSpectatorView.isActive()
                || com.myriad.client.ClientDataHolder.placementType()!=0
                || com.myriad.client.ClientDataHolder.isChanneling()
                || com.myriad.client.ClientDataHolder.buffTicks("g1_stun")>0
                || !com.myriad.combat.VanillaMeleeAttack.usesVanilla(
                    com.myriad.character.CharacterType.byId(com.myriad.client.ClientDataHolder.characterId())))ci.cancel();
    }
    @Inject(method="renderFrame",at=@At("HEAD"))
    private void myriad$suppressHurtCamera(boolean tick,CallbackInfo ci) { MyriadClient.beforeRenderFrame(); }
}
