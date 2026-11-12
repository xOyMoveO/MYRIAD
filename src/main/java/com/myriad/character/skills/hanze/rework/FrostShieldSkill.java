package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrostShieldSkill extends AbilitySkill {
    public FrostShieldSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.shield(c.player,c.value(150,40,.65f),80);for(var t:c.enemies(c.player.position(),3))c.slow(t,.25f,30);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),3,false);}
}

