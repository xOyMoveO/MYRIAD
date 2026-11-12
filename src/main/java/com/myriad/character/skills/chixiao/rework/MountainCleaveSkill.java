package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class MountainCleaveSkill extends AbilitySkill {
 public MountainCleaveSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.cone(6,100,c.value(75,22,.6f),PHYSICAL);}
@Override public void present(Cast c){AbilityVisuals.burst(c,c.player.position().add(c.forward(3)),1);}
}

