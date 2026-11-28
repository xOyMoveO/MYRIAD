package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.registry.ModSounds.Night.*;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ShadowShieldSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 306, 148.75f, 1.53f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    @Override public boolean hasOwnCastSound() { return true; }

    public ShadowShieldSkill(Definition definition){super(definition);}
    public static boolean hasShield(ServerPlayer player) {
        return com.myriad.player.ModShieldSystem.hasSource(player, "g1_yingxiao_ub/" + player.getUUID());
    }

    @Override public boolean canCastWhileControlled(){return true;}
    @Override public void present(Cast c){NightVisuals.shield(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.SHIELD,14);}
    @Override public void release(Cast c){
        AbilityRuntime.cleanseControls(c.player);
        long[] nextAbsorb = {Long.MIN_VALUE};
        c.shield(c.player, SHIELD.value(c), 80,
                () -> NightAudio.play(c, c.player.position(), SHIELD_OPEN),
                hit -> {
                    if (!c.ownShield(c.player)) {
                        NightAudio.play(c, c.player.position(), SHIELD_BREAK);
                    } else if (c.now() >= nextAbsorb[0]) {
                        NightAudio.play(c, c.player.position(), ABSORB);
                        nextAbsorb[0] = c.now() + 3;
                    }
                });
        c.haste(.2f,40);c.buff(getId(),80);
    }
}
