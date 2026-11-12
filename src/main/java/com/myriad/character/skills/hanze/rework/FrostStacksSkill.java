package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrostStacksSkill extends AbilitySkill {
    public FrostStacksSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory(h.target().getUUID().toString());if(c.now()<m.cooldown||c.now()-m.last<4)return;if(!h.basic()&&m.tag.equals(h.skillId()+"/"+AbilityRuntime.sourceCast()))return;if(c.now()>m.until)m.count=0;m.last=c.now();m.tag=h.basic()?"":h.skillId()+"/"+AbilityRuntime.sourceCast();m.until=c.now()+120;if(++m.count<4)return;m.count=0;m.cooldown=c.now()+120;c.root(h.target(),20,false);c.damage(h.target(),c.value(25,10,.2f),MAGIC,false);AbilityVisuals.pillar(c,h.target().position(),2.2);}
@Override public void release(Cast c){}
}

