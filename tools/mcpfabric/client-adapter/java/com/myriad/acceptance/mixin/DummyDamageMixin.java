package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.game.JiarenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=JiarenManager.class,remap=false)
public abstract class DummyDamageMixin {
    @Inject(method="damage",at=@At("RETURN"))
    private static void myriad$damage(LivingEntity target,float amount,CallbackInfo ci) {
        var detail=new JsonObject();
        detail.addProperty("serverTick",target.level().getGameTime());
        detail.addProperty("target",target.getId());
        detail.addProperty("damage",amount);
        Minecraft.getInstance().execute(()->MyriadInputBridge.observePresentation("training_damage",detail));
    }
}
