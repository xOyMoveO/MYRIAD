package com.myriad.mixin;
import com.myriad.character.ability.ActionTime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Preserve work already completed in vanilla's melee recovery; only the next tick advances locally. */
@Mixin(Player.class)
public abstract class LivingAttackTimeMixin extends LivingEntity {
    protected LivingAttackTimeMixin(net.minecraft.world.entity.EntityType<? extends LivingEntity> type,net.minecraft.world.level.Level level){super(type,level);}
    @Unique private int myriad$previousAttackWork;
    @Unique private double myriad$attackFraction;
    @Inject(method="resetAttackStrengthTicker",at=@At("TAIL"))
    private void myriad$resetRecovery(CallbackInfo ci){myriad$attackFraction=0;}
    @Inject(method="tick",at=@At("HEAD"))
    private void myriad$beforeRecovery(CallbackInfo ci){myriad$previousAttackWork=attackStrengthTicker;}
    @Inject(method="tick",at=@At("TAIL"))
    private void myriad$localRecovery(CallbackInfo ci){
        if(!((Object)this instanceof ServerPlayer p))return;
        if(attackStrengthTicker<=myriad$previousAttackWork){if(attackStrengthTicker<myriad$previousAttackWork)myriad$attackFraction=0;return;}
        double rate=ActionTime.multiplier(p.getUUID(),p.level().getGameTime());
        myriad$attackFraction+=rate;int whole=(int)myriad$attackFraction;myriad$attackFraction-=whole;
        attackStrengthTicker=myriad$previousAttackWork+whole;
    }
}
