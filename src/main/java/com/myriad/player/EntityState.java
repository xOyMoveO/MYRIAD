package com.myriad.player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** New Fabric data only; the retired loader's player/temporary tags are not imported. */
public final class EntityState {
    private static final AttachmentType<CompoundTag> TAGS = AttachmentRegistry.create(Identifier.parse("myriad:entity_state"), b -> b.initializer(CompoundTag::new).persistent(CompoundTag.CODEC));
    /** Registers saved/synced types before any world or entity data is decoded. */
    public static void initialize() { }

    private EntityState() { }
    public static CompoundTag data(Entity entity) { return ((AttachmentTarget) entity).getAttachedOrCreate(TAGS); }
}
