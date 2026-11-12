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
public final class EmberBurnSkill extends AbilitySkill {
    public EmberBurnSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){if(h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory(h.target().getUUID().toString());
 boolean fresh=m.until<=c.now();m.until=c.now()+60;if(fresh){com.myriad.character.ability.AbilityVisuals.burst(c,h.target().position(),.45);int token=++m.count;m.last=c.now();c.whileActive(12000,age->{
 if(token!=m.count||!h.target().isAlive()||m.until<c.now())return false;
 if(c.now()-m.last>=20){m.last=c.now();com.myriad.character.ability.AbilityVisuals.burst(c,h.target().position(),.35);c.damage(h.target(),(.05f+.01f*c.rank)*c.strength,TRUE,false);}return true;});}}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
