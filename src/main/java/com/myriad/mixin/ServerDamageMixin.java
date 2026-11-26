package com.myriad.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.myriad.combat.DamageContext;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ServerDamageMixin {
    @WrapMethod(method="actuallyHurt")
    private void myriad$damage(ServerLevel level,DamageSource source,float amount,Operation<Void> original) {
        LivingEntity entity=(LivingEntity)(Object)this;
        var transaction=new DamageContext(entity,source,amount);
        PlayerDataProvider.onIncomingDamage(transaction);
        float before=entity.getHealth();
        if(!transaction.isCanceled()&&transaction.getAmount()>0)original.call(level,source,transaction.getAmount());
        PlayerDataProvider.onDamageApplied(transaction,Math.max(0,before-entity.getHealth()));
    }
    @Inject(method="knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",at=@At("HEAD"),cancellable=true)
    private void myriad$knockback(double strength,double x,double z,DamageSource source,float amount,boolean impulse,CallbackInfo ci) {
        if(PlayerDataProvider.blockVanillaKnockback((LivingEntity)(Object)this))ci.cancel();
    }
    @Inject(method="heal",at=@At("HEAD"))
    private void myriad$heal(float amount,CallbackInfo ci) { com.myriad.combat.CombatHealingEvents.heal((LivingEntity)(Object)this,amount); }
}
