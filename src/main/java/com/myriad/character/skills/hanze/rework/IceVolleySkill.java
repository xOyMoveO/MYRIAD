package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class IceVolleySkill extends AbilitySkill {
    public IceVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){for(int i=0;i<3;i++)c.actionLater(i*5,()->c.actionProjectile(15,1.5,.75,false,false,(t,back)->{c.damage(t,c.value(25,8,.2f),MAGIC);c.slow(t,.2f,20);}));}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.75,false);}
}

