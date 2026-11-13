package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordVolleySkill extends AbilitySkill {
    public SwordVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){for(int i=0;i<3;i++)c.actionLater(i*4,()->c.actionProjectile(18,1.5,.7,false,false,(t,back)->c.damage(t,c.value(35,10,.25f),MAGIC)));}
@Override public void present(Cast c){SwordVisuals.volley(c);}
}

