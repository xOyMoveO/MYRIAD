package com.myriad.character.skills.xuanchen.rework;

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
public final class ArrayGuardianSkill extends AbilitySkill {
    public ArrayGuardianSkill(Definition definition){super(definition);}
    @Override public void onCast(Cast released){Cast c=AbilityRuntime.context(released.player,this);var m=c.memory();if(m.cooldown>c.now())return;m.cooldown=c.now()+240;
 var ally=c.allies(c.player.position(),8).stream().filter(p->p!=c.player).min(Comparator.comparingDouble(p->p.getHealth()/p.getMaxHealth())).orElse(c.player);
 com.myriad.character.ability.AbilityVisuals.beam(c,c.player.position().add(0,.5,0),ally.position().add(0,.5,0),.06);c.shield(ally,c.value(40,15,.25f),40);}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
