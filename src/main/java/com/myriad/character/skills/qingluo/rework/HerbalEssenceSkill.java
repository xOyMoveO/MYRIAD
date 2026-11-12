package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class HerbalEssenceSkill extends AbilitySkill {
 public HerbalEssenceSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(c.now()-m.last<4||!h.basic()&&m.tag.equals(h.skillId()+"/"+AbilityRuntime.sourceCast()))return;m.last=c.now();m.tag=h.basic()?"":h.skillId()+"/"+AbilityRuntime.sourceCast();m.count=Math.min(3,m.count+1);c.buff(getId()+"_ready",m.count>0?200:0);}
@Override public void onSupport(Cast c,LivingEntity t){var m=c.memory();int stacks=m.count;if(stacks<=0)return;m.count=0;c.buff(getId()+"_ready",0);c.heal(t,stacks*c.value(12,5,.1f));AbilityVisuals.burst(c,t.position(),.8);}
@Override public void release(Cast c){}
@Override public void tickPassive(Cast c){c.buff(getId()+"_ready",c.memory().count>0?20:0);}
}

