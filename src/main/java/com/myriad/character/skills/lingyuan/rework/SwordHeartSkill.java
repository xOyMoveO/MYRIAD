package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordHeartSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 10, 3.6f, .12f).segment("skill.myriad.damage.segment.sword", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SwordHeartSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();m.target=h.target().getUUID();if(m.last==c.now())return;m.last=c.now();m.count+=10;if(m.count>=100){m.count-=100;m.distance=Math.min(4,m.distance+1);}c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);}
@Override public void tickPassive(Cast c){var m=c.memory();c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);SwordVisuals.passiveBlades(c,(int)m.distance);if(m.distance<=0||c.now()<m.cooldown)return;var t=m.target==null?null:c.player.level().getEntity(m.target);if(!(t instanceof LivingEntity target)||!c.enemy(target)||!com.myriad.game.VisionSystem.canSee(c.player,target)||target.distanceToSqr(c.player)>100)return;m.cooldown=c.now()+40;
for(int i=0;i<(int)m.distance;i++){final int blade=i;c.actionLater(i*3,()->{if(!target.isAlive()||!c.enemy(target)||target.distanceToSqr(c.player)>100)return;Vec3 from=c.player.position().add(SwordVisuals.passiveSlot(blade));AbilityVisuals.beam(c,from,target.position().add(0,.8,0),.12);c.damage(target,DAMAGE.value(c),DAMAGE.kind(),false);});}
}
@Override public void release(Cast c){}
}

