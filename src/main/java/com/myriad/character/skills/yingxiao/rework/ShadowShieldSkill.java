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
public final class ShadowShieldSkill extends AbilitySkill {
    public ShadowShieldSkill(Definition definition){super(definition);}
    @Override public boolean canCastWhileControlled(){return true;}
    @Override public void present(Cast c){NightVisuals.shield(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.SHIELD,14);}
    @Override public void release(Cast c){
        AbilityRuntime.cleanseControls(c.player);
        c.shield(c.player,c.value(306,119,1.53f),80);
        c.haste(.2f,40);c.buff(getId(),80);
    }
}
