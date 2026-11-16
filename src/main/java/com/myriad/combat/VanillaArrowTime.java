package com.myriad.combat;

import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Fabric attachment tracking includes the current flight rate for late observers. */
public final class VanillaArrowTime {
    /** Registers saved/synced types before any world or entity data is decoded. */
    public static void initialize() { }

    private VanillaArrowTime() { }
    public static final AttachmentType<Double> RATE = AttachmentRegistry.create(Identifier.parse("myriad:arrow_action_rate"), b -> b.initializer(() -> 1.0).syncWith(ByteBufCodecs.DOUBLE, AttachmentSyncPredicate.all()));
    public static double rate(Entity entity) { return ((AttachmentTarget) entity).getAttachedOrCreate(RATE); }
    public static void rate(Entity entity,double value) { ((AttachmentTarget) entity).setAttached(RATE,value); }
    public static final int FLIGHT_TICKS=1200, REAL_FLIGHT_TICKS=2400;
}
