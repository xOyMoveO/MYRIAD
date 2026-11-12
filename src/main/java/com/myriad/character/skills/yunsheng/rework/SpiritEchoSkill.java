package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritEchoSkill extends AbilitySkill {
 public SpiritEchoSkill(Definition definition){super(definition);}

@Override public void onSupport(Cast c,LivingEntity t){var m=c.memory(t.getUUID().toString());if(m.cooldown>c.now()||c.now()-m.last<4)return;if(m.until<=c.now())m.count=0;m.last=c.now();m.until=c.now()+120;if(++m.count<2)return;m.count=0;m.cooldown=c.now()+160;c.shield(t,c.value(45,15,.25f),60);}
@Override public void release(Cast c){}
}

