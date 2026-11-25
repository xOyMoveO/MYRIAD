package com.myriad.mixin;

import com.myriad.MyriadClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MyriadMinecraftClientMixin {
    @Shadow private int missTime;

    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void myriad$authoritativeAttack(CallbackInfoReturnable<Boolean> cir) {
        var input = MyriadClient.attack(missTime <= 0);
        if (input.isMeleeAssist()) cir.setReturnValue(true);
        else if (input.isCanceled()) cir.setReturnValue(false);
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

    @Redirect(method="handleKeybinds",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void myriad$keepLuoxiaDraw(net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode,
            net.minecraft.world.entity.player.Player player) {
        if (!player.getUseItem().is(com.myriad.registry.ModItems.GUIXIA_BOW.get())) gameMode.releaseUsingItem(player);
    }
}
