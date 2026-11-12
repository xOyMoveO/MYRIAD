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
public final class ShadowVanishSkill extends AbilitySkill {
    public ShadowVanishSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.MERGE,14);}
    @Override public void release(Cast c){NightVisuals.vanish(c);int ticks=40+5*(c.rank-1);c.player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,ticks,0,false,false));c.haste(.15f,ticks);c.buff(getId(),ticks);}
}
