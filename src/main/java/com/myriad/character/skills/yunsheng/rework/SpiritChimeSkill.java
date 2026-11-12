package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritChimeSkill extends AbilitySkill {
 public SpiritChimeSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target())||c.memory().cooldown>c.now())return;c.memory().cooldown=c.now()+120;for(var ally:c.allies(c.player.position(),6)){c.heal(ally,c.value(18,7,.15f));c.slow(ally,-.1f,20);}}
@Override public void release(Cast c){}
}

