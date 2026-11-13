package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class GlacierLanceSkill extends AbilitySkill {
    public GlacierLanceSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.actionProjectile(18,1.2,1,true,false,(t,back)->{c.damage(t,c.value(85,22,.55f),MAGIC);c.slow(t,.4f,40);});}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1.3,80);}
}

