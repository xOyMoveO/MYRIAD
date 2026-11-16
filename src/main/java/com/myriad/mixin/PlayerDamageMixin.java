package com.myriad.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.myriad.combat.DamageContext;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Player overrides actuallyHurt and does not delegate to LivingEntity. */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
    @WrapMethod(method="actuallyHurt")
    private void myriad$damage(ServerLevel level,DamageSource source,float amount,Operation<Void> original) {
        LivingEntity entity=(LivingEntity)(Object)this;
        var transaction=new DamageContext(entity,source,amount);
        PlayerDataProvider.onIncomingDamage(transaction);
        float before=entity.getHealth();
        if(!transaction.isCanceled()&&transaction.getAmount()>0)original.call(level,source,transaction.getAmount());
        PlayerDataProvider.onDamageApplied(entity,source,Math.max(0,before-entity.getHealth()));
    }
}
