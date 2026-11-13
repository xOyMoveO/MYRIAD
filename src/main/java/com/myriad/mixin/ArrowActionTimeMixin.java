package com.myriad.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.game.ModState;
import com.myriad.combat.VanillaArrowTime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reparameterizes vanilla's discrete trajectory, retaining physical velocity for damage/deflection.
 * Each unfinished vanilla flight step is swept in fractional segments; inertia and gravity run once
 * at their original local-step boundary. No world/entity tick or character gravity is slowed. */
@Mixin(AbstractArrow.class)
public abstract class ArrowActionTimeMixin extends Projectile {
    @Shadow protected abstract boolean isInGround();
    @Shadow private void applyInertia(float inertia) {}
    @Shadow protected abstract float getWaterInertia();
    @Shadow protected abstract float getAirDrag();
    @Shadow public abstract boolean isNoPhysics();
    @Unique private double myriad$phase;
    @Unique private Vec3 myriad$stepMovement = Vec3.ZERO;
    @Unique private double myriad$flightAge;
    @Unique private int myriad$realFlightAge;
    @Unique private boolean myriad$slicing;
    @Unique private double myriad$slice = 1;
    @Unique private boolean myriad$stepWater;

    protected ArrowActionTimeMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }

    @WrapMethod(method="tick")
    private void myriad$flight(Operation<Void> original) {
        // Vanilla entity types only: not tridents (loyalty/return physics) or third-party subclasses.
        boolean supported=getType()==EntityTypes.ARROW||getType()==EntityTypes.SPECTRAL_ARROW;
        if(!supported||isInGround()) {myriad$phase=0;original.call();return;}
        double rate;
        if(level().isClientSide())rate=getData(VanillaArrowTime.RATE);
        else {
            rate=ModState.isEnabled()?KehengTimeFields.projectileRate(getOwner(),position(),level().getGameTime()):1;
            if(getData(VanillaArrowTime.RATE)!=rate)setData(VanillaArrowTime.RATE,rate);
        }
        // Vanilla has no airborne TTL. These are MYRIAD's explicit flight budgets, not its in-ground life.
        if(!level().isClientSide()&&ModState.isEnabled()){
            myriad$flightAge+=rate;
            if(++myriad$realFlightAge>=VanillaArrowTime.REAL_FLIGHT_TICKS||myriad$flightAge>=VanillaArrowTime.FLIGHT_TICKS){discard();return;}
        }
        if(rate==1&&myriad$phase==0){original.call();return;}
        double remaining=rate;
        myriad$slicing=true;
        try {
            while(remaining>1E-9&&!isRemoved()&&!isInGround()) {
                myriad$slice=Math.min(remaining,1-myriad$phase);
                if(myriad$phase<1E-9){
                    myriad$stepMovement=getDeltaMovement();myriad$stepWater=isInWater();
                    if(myriad$stepWater)applyInertia(getWaterInertia());
                }
                boolean endsStep=myriad$phase+myriad$slice>=1-1E-9;
                Vec3 physicalBefore=getDeltaMovement();
                original.call();
                // A vanilla impact/deflection finishes this movement segment; never reuse its old direction.
                boolean changed=!getDeltaMovement().equals(physicalBefore);
                if(endsStep||changed){
                    if(!myriad$stepWater)applyInertia(getAirDrag());
                    if(!isNoPhysics()&&!isInGround())applyGravity();
                }
                remaining-=myriad$slice;
                myriad$phase=endsStep||changed?0:myriad$phase+myriad$slice;
                if(changed)break;
            }
        } finally {myriad$slicing=false;myriad$slice=1;}
        // Projectile ownership, fire/portal/base lifetime bookkeeping advances once in real time.
        super.tick();
        needsSync=true;
    }

    @ModifyVariable(method="tick",at=@At(value="STORE",ordinal=0),ordinal=0)
    private Vec3 myriad$segmentMovement(Vec3 movement) {return myriad$slicing?myriad$stepMovement.scale(myriad$slice):movement;}

    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;applyInertia(F)V"))
    private void myriad$localInertia(AbstractArrow arrow,float inertia) {
        if(!myriad$slicing)applyInertia(inertia);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;applyGravity()V"))
    private void myriad$localGravity(AbstractArrow arrow) {if(!myriad$slicing)applyGravity();}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/Projectile;tick()V"))
    private void myriad$realEntityTick(Projectile arrow) {if(!myriad$slicing)super.tick();}

    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void myriad$saveProgress(ValueOutput output,CallbackInfo ci){output.putDouble("myriad_flight_phase",myriad$phase);
        output.store("myriad_flight_step",Vec3.CODEC,myriad$stepMovement);output.putBoolean("myriad_flight_water",myriad$stepWater);
        output.putDouble("myriad_flight_age",myriad$flightAge);output.putInt("myriad_flight_real_age",myriad$realFlightAge);}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void myriad$loadProgress(ValueInput input,CallbackInfo ci){myriad$phase=input.getDoubleOr("myriad_flight_phase",0);
        myriad$stepMovement=input.read("myriad_flight_step",Vec3.CODEC).orElse(getDeltaMovement());myriad$stepWater=input.getBooleanOr("myriad_flight_water",false);
        myriad$flightAge=input.getDoubleOr("myriad_flight_age",0);myriad$realFlightAge=input.getIntOr("myriad_flight_real_age",0);}
}
