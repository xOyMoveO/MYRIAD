package com.myriad.mixin;

import com.myriad.game.ModState;
import com.myriad.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Apply tenacity to control duration once, before storing the effect. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityControlMixin {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance myriad$tenacity(MobEffectInstance effect) {
        if (!ModState.isEnabled() || !((Object)this instanceof ServerPlayer player) || effect.isInfiniteDuration()
                || !(effect.is(MobEffects.SLOWNESS) || effect.is(MobEffects.BLINDNESS)
                || effect.is(MobEffects.LEVITATION) || effect.is(MobEffects.MINING_FATIGUE))) return effect;
        double reduction = Math.clamp(player.getAttributeValue(ModAttributes.TENACITY), 0, 60) / 100;
        if (reduction <= 0) return effect;
        return new MobEffectInstance(effect.getEffect(), Math.max(1, (int)Math.ceil(effect.getDuration() * (1 - reduction))),
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }
}
