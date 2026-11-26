package com.myriad.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

/** Mutable server damage transaction, entered once at vanilla's accepted damage boundary. */
public final class DamageContext {
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;
    private boolean cancelled;
    private boolean shieldBreakSound;
    public DamageContext(LivingEntity entity, DamageSource source, float amount) { this.entity=entity; this.source=source; this.amount=amount; }
    public LivingEntity getEntity() { return entity; }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount=amount; }
    public boolean hasShieldBreakSound() { return shieldBreakSound; }
    public void markShieldBreakSound() { shieldBreakSound = true; }
    public boolean isCanceled() { return cancelled; }
    public void setCanceled(boolean value) { cancelled=value; }
}
