package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class BattleIntentSkill extends AbilitySkill {
 public BattleIntentSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target()))return;var m=c.memory();if(!h.target().getUUID().equals(m.target)||c.now()>m.until)m.count=0;if(c.now()-m.last<4)return;if(!h.basic()&&m.tag.equals(h.skillId()+"/"+AbilityRuntime.sourceCast()))return;m.target=h.target().getUUID();m.last=c.now();m.until=c.now()+120;m.tag=h.basic()?"":h.skillId()+"/"+AbilityRuntime.sourceCast();if(m.count>=3&&!h.basic()){m.count=0;c.damage(h.target(),c.value(25,10,.25f),TRUE,false);AbilityVisuals.burst(c,h.target().position(),.8);}else m.count=Math.min(3,m.count+1);c.buff(getId()+"_ready",m.count>=3?120:0);}
@Override public void release(Cast c){}
}

