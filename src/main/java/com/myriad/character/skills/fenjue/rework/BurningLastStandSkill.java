package com.myriad.character.skills.fenjue.rework;

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
public final class BurningLastStandSkill extends AbilitySkill {
    public BurningLastStandSkill(Definition definition){super(definition);}
    @Override public double modifyDamage(AbilityRuntime.Hit h){if(h.basic())return 1;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();
 String cast=h.skillId()+"/"+AbilityRuntime.sourceCast();
 if(m.tag.equals(cast))return 1+(10+4*(c.rank-1))/100.0;
 if(c.player.getHealth()>c.player.getMaxHealth()*.2f||m.cooldown>c.now())return 1;
 m.cooldown=c.now()+160;m.tag=cast;com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),1);return 1+(10+4*(c.rank-1))/100.0;}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
