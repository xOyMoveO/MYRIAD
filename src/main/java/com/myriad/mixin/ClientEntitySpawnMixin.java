package com.myriad.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A server-approved spawn packet must not be rejected again by client difficulty. */
@Mixin(ClientPacketListener.class)
public abstract class ClientEntitySpawnMixin {
    @Redirect(method = "createEntityFromPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
    private Entity myriad$acceptServerSpawn(EntityType<?> type, Level level, EntitySpawnReason reason) {
        return type.create(level, new EntitySpawnRequest(reason, true));
    }
}
