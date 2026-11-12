package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class RimeSkinSkill extends AbilitySkill {
    public RimeSkinSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(c.now()<c.memory().cooldown)return;c.memory().cooldown=c.now()+240;c.shield(c.player,c.value(65,20,.35f),60);for(var t:c.enemies(c.player.position(),3))c.slow(t,.2f,20);}
@Override public void tickPassive(Cast c){if(c.now()>=c.memory().cooldown)AbilityVisuals.ready(c);}
@Override public void release(Cast c){}
}

