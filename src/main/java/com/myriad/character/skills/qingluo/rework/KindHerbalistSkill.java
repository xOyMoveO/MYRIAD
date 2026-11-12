package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class KindHerbalistSkill extends AbilitySkill {
 public KindHerbalistSkill(Definition definition){super(definition);}

@Override public void onSupport(Cast c,LivingEntity t){var m=c.memory(t.getUUID().toString());if(t.getHealth()>=t.getMaxHealth()*.6||m.cooldown>c.now())return;m.cooldown=c.now()+80;c.heal(t,c.value(20,8,.18f));c.slow(t,-.1f,40);AbilityVisuals.burst(c,t.position(),.65);}
@Override public void release(Cast c){}
}

