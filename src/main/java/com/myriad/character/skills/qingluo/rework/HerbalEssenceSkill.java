package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class HerbalEssenceSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 12, 6, .1f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

 public HerbalEssenceSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(c.now()-m.last<4||!h.basic()&&m.tag.equals(h.skillId()+"/"+AbilityRuntime.sourceCast()))return;m.last=c.now();m.tag=h.basic()?"":h.skillId()+"/"+AbilityRuntime.sourceCast();m.count=Math.min(3,m.count+1);c.buff(getId()+"_ready",m.count>0?200:0);}
@Override public void onSupport(Cast c,LivingEntity t){var m=c.memory();int stacks=m.count;if(stacks<=0)return;m.count=0;c.buff(getId()+"_ready",0);c.heal(t,stacks*HEAL.value(c));AbilityVisuals.cancelSkill(c);HerbVisuals.restoration(c,t,.8);}
@Override public void release(Cast c){}
@Override public void tickPassive(Cast c){int count=c.memory().count;c.buff(getId()+"_ready",count>0?20:0);if(count>0&&c.now()%6==0)HerbVisuals.essence(c,count);}
}

