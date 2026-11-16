package com.myriad.gametest.mixin;

import com.myriad.game.GameTestSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observation at the native positional sound broadcast entry. */
@Mixin(ServerLevel.class)
public abstract class ServerSoundObservationMixin {
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void myriadTest$observe(Entity excluded, double x, double y, double z,
            Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        GameTestSounds.played((ServerLevel) (Object) this, new Vec3(x, y, z), sound, source, volume, pitch);
    }
}
