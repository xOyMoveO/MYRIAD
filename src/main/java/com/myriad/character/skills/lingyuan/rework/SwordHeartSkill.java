package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordHeartSkill extends AbilitySkill {
    public SwordHeartSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();m.target=h.target().getUUID();if(m.last==c.now())return;m.last=c.now();m.count+=10;if(m.count>=100){m.count-=100;m.distance=Math.min(4,m.distance+1);}c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);}
@Override public void tickPassive(Cast c){var m=c.memory();c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);SwordVisuals.passiveBlades(c,(int)m.distance);if(m.distance<=0||c.now()<m.cooldown)return;var t=m.target==null?null:c.player.level().getEntity(m.target);if(!(t instanceof LivingEntity target)||!c.enemy(target)||!com.myriad.game.VisionSystem.canSee(c.player,target)||target.distanceToSqr(c.player)>100)return;m.cooldown=c.now()+40;
for(int i=0;i<(int)m.distance;i++){final int blade=i;c.later(i*3,false,()->{if(!target.isAlive()||!c.enemy(target)||target.distanceToSqr(c.player)>100)return;Vec3 from=c.player.position().add(Math.cos(blade*1.57)*.8,1.4,Math.sin(blade*1.57)*.8);AbilityVisuals.beam(c,from,target.position().add(0,.8,0),.12);c.damage(target,c.value(10,3,.12f),MAGIC,false);});}
}
@Override public void release(Cast c){}
}

