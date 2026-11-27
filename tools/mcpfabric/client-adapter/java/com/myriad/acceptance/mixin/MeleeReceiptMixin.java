package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.combat.VanillaMeleeAttack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=VanillaMeleeAttack.class,remap=false)
public abstract class MeleeReceiptMixin {
    @Inject(method="prepare",at=@At("RETURN"))
    private static void myriad$accepted(ServerPlayer player,Entity target,CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ()) return;
        var detail=new JsonObject();
        detail.addProperty("serverTick",player.level().getGameTime());
        detail.addProperty("target",target.getId());
        var playerId=player.getUUID();
        var client=Minecraft.getInstance();
        client.execute(()->{
            if (client.player!=null && client.player.getUUID().equals(playerId))
                MyriadInputBridge.observePresentation("melee_accepted",detail);
        });
    }
}
