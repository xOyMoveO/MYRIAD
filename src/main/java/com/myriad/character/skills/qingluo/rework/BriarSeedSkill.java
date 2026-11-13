package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class BriarSeedSkill extends AbilitySkill {
 public BriarSeedSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.actionProjectile(15,1.2,.8,false,false,(t,back)->{c.damage(t,c.value(50,16,.45f),MAGIC);c.root(t,14,false);for(var ally:c.allies(t.position(),3))c.heal(ally,c.value(35,12,.25f));});}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1,65);}
}

