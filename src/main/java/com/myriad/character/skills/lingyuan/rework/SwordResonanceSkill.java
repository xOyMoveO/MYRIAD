package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordResonanceSkill extends AbilitySkill {
    public SwordResonanceSkill(Definition definition){super(definition);}

@Override public void onCast(Cast c){var own=AbilityRuntime.context(c.player,this);own.memory().count=Math.min(3,own.memory().count+1);own.buff(getId()+"_ready",own.memory().count>=3?200:0);}
@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.count<3)return;m.count=0;c.buff(getId()+"_ready",0);c.damage(h.target(),c.value(35,12,.35f),MAGIC,false);c.shield(c.player,c.value(45,15,.25f),60);AbilityVisuals.burst(c,h.target().position(),.8);}
@Override public void release(Cast c){}
@Override public void tickPassive(Cast c){c.buff(getId()+"_ready",c.memory().count>=3?20:0);SwordVisuals.resonance(c,c.memory().count);}
}

