package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class JadeCanopySkill extends AbilitySkill {
 public JadeCanopySkill(Definition definition){super(definition);}

@Override public void release(Cast c){for(var ally:c.allies(c.player.position(),8)){c.heal(ally,c.value(100,35,.4f));c.shield(ally,c.value(200,65,.65f),100);c.slow(ally,-.2f,60);}}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),8,24);}
}

