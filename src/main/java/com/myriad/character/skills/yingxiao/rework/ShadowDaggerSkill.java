package com.myriad.character.skills.yingxiao.rework;

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
public final class ShadowDaggerSkill extends AbilitySkill {
    public ShadowDaggerSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.daggerRelease(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.THROW,9);}
    @Override public void release(Cast c){c.actionProjectile(15,1.5,.8,false,false,(t,back)->{c.damage(t,c.value(45,15,.55f),PHYSICAL);c.slow(t,.25f,30);});}
}
