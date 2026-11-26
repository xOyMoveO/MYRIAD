package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.registry.ModSounds.Night.*;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ShadowVanishSkill extends AbilitySkill {
    @Override public boolean hasOwnCastSound() { return true; }

    public ShadowVanishSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.MERGE,14);}
    @Override public void release(Cast c) {
        NightVisuals.vanish(c);
        int ticks = 40 + 5 * (c.rank - 1);
        boolean applied = c.player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
        c.haste(.15f, ticks);
        c.buff(getId(), ticks);
        if (!applied) return;
        NightAudio.personal(c, ENTER);
        var state = c.memory("vanish_audio");
        state.count = c.castId();
        c.whileActive(ticks + 2, age -> {
            if (state.count != c.castId()) return false;
            if (c.player.hasEffect(MobEffects.INVISIBILITY)) return true;
            if (c.player.isAlive() && !c.player.isRemoved()) NightAudio.personal(c, EXIT);
            return false;
        });
    }
}
