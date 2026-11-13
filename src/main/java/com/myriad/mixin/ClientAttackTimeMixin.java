package com.myriad.mixin;
import com.myriad.client.ClientDataHolder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Own-camera recovery mirrors the server-confirmed local action rate. */
@Mixin(Player.class)
public abstract class ClientAttackTimeMixin extends LivingEntity {
    protected ClientAttackTimeMixin(net.minecraft.world.entity.EntityType<? extends LivingEntity> type,net.minecraft.world.level.Level level){super(type,level);}
    @Unique private int myriad$before;
    @Unique private double myriad$fraction;
    @Inject(method="resetAttackStrengthTicker",at=@At("TAIL"))
    private void myriad$resetRecovery(CallbackInfo ci){myriad$fraction=0;}
    @Inject(method="tick",at=@At("HEAD")) private void myriad$sample(CallbackInfo ci){myriad$before=attackStrengthTicker;}
    @Inject(method="tick",at=@At("TAIL")) private void myriad$recover(CallbackInfo ci){
        if(!((Object)this instanceof LocalPlayer))return;
        if(attackStrengthTicker<=myriad$before){if(attackStrengthTicker<myriad$before)myriad$fraction=0;return;}
        int value=ClientDataHolder.syncedCounter("g1_keheng_rate");double rate=value<=0?1:value/100.0;
        myriad$fraction+=rate;int step=(int)myriad$fraction;myriad$fraction-=step;attackStrengthTicker=myriad$before+step;
    }
}
