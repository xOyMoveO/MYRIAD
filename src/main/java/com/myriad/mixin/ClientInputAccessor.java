package com.myriad.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reset this tick's movement vector without replacing the player's input implementation. */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector") void myriad$setMoveVector(Vec2 vector);
}
