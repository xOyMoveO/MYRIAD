package com.myriad.mixin;

import com.myriad.game.LaneSystem;
import com.myriad.game.MonsterManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Map combatants have match-owned lifetimes, including on peaceful maps. */
@Mixin(Mob.class)
public abstract class ManagedMobDespawnMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void myriad$keepMapCombatants(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (MonsterManager.isMonster(mob) || LaneSystem.isManaged(mob)) ci.cancel();
    }
}
