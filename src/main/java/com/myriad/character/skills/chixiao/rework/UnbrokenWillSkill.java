package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class UnbrokenWillSkill extends AbilitySkill {
 public UnbrokenWillSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(c.player.getHealth()>c.player.getMaxHealth()*.4||c.memory().cooldown>c.now())return;c.memory().cooldown=c.now()+240;c.shield(c.player,c.value(80,22,.4f),60);c.haste(.15f,40);}
@Override public void tickPassive(Cast c){if(c.memory().cooldown<=c.now()&&c.player.getHealth()<=c.player.getMaxHealth()*.4)AbilityVisuals.ready(c);}
@Override public void release(Cast c){}
}

