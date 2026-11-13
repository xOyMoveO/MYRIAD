package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class RendingWaveSkill extends AbilitySkill {
 public RendingWaveSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.actionProjectile(14,1.2,1.1,true,false,(t,back)->{c.damage(t,c.value(85,22,.55f),PHYSICAL);c.slow(t,.3f,40);});}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),3,110);}
}

