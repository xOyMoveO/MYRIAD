package com.myriad.game;

import com.myriad.registry.ModAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Stationary, full-height combat target; its building is drawn by an ItemDisplay. */
public final class DefenseTowerEntity extends PathfinderMob {
    public DefenseTowerEntity(EntityType<? extends DefenseTowerEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 1024)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(ModAttributes.PHYSICAL_DEFENSE).add(ModAttributes.MAGIC_RESISTANCE);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) { return false; }
    @Override public void setDeltaMovement(net.minecraft.world.phys.Vec3 motion) {
        super.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public void push(Entity other) { }
    @Override public void push(double x, double y, double z) { }
    @Override public void travel(net.minecraft.world.phys.Vec3 input) {
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    @Override public void knockback(double strength, double x, double z,
            net.minecraft.world.damagesource.DamageSource source, float damage, boolean fromEffect) { }
}
