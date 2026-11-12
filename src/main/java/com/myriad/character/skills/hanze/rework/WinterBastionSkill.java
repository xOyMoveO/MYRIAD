package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class WinterBastionSkill extends AbilitySkill {
    public WinterBastionSkill(Definition definition){super(definition);}

@Override public void release(Cast c){for(var ally:c.allies(c.player.position(),6))c.shield(ally,c.value(230,70,.7f),100);c.area(c.player.position(),5,c.value(90,30,.4f),MAGIC);for(var t:c.enemies(c.player.position(),5))c.root(t,20,false);}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),5,24);}
}

