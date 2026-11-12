package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class LifeThreadSkill extends AbilitySkill {
 public LifeThreadSkill(Definition definition){super(definition);}

@Override public void release(Cast c){LivingEntity picked=c.target(12,true);final LivingEntity ally=picked==null?c.player:picked;c.shield(ally,c.value(150,35,.7f),80);c.whileActive(80,a->{if(!ally.isAlive()||ally.level()!=c.player.level()||ally.distanceToSqr(c.player)>196||!c.player.hasLineOfSight(ally))return false;if(a>0&&a%20==0)c.heal(ally,c.value(20,7,.15f));if(a%5==0)AbilityVisuals.beam(c,c.player.position().add(0,.8,0),ally.position().add(0,.8,0),.1);return true;});}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),1,false);}
}

