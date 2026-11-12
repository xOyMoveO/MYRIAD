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
public final class NightBackstabSkill extends AbilitySkill {
    public NightBackstabSkill(Definition definition){super(definition);}
    @Override public double modifyDamage(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);return c.behind(h.target())?1+(10+5*(c.rank-1))/100.0:1;}
    @Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);
        if(c.behind(h.target()))com.myriad.character.ability.AbilityVisuals.arc(c,h.target().position().add(0,h.target().getBbHeight()*.30,0),.65,95);}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
