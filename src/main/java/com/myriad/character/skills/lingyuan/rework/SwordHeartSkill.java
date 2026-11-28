package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordHeartSkill extends AbilitySkill {
    public static final int QI_PER_BASIC=10, QI_PER_SWORD=100, MAX_SWORDS=4, VOLLEY_TICKS=40, SWORD_GAP_TICKS=3;
    public static final double RANGE=10;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 10, 3.6f, .12f).segment("skill.myriad.damage.segment.sword", MAX_SWORDS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("basics", QI_PER_SWORD / QI_PER_BASIC, "swords", MAX_SWORDS, "range", RANGE, "interval", VOLLEY_TICKS / 20.0);
    }

    public SwordHeartSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();m.target=h.target().getUUID();if(m.last==c.now())return;m.last=c.now();m.count+=QI_PER_BASIC;if(m.count>=QI_PER_SWORD){m.count-=QI_PER_SWORD;m.distance=Math.min(MAX_SWORDS,m.distance+1);}c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);}
@Override public void tickPassive(Cast c){var m=c.memory();c.data.setSwordQi(m.count);c.data.setFlyingSwords((int)m.distance);SwordVisuals.passiveBlades(c,(int)m.distance);if(m.distance<=0||c.now()<m.cooldown)return;var t=m.target==null?null:c.player.level().getEntity(m.target);if(!(t instanceof LivingEntity target)||!c.enemy(target)||!com.myriad.game.VisionSystem.canSee(c.player,target)||target.distanceToSqr(c.player)>RANGE*RANGE)return;m.cooldown=c.now()+VOLLEY_TICKS;
for(int i=0;i<(int)m.distance;i++){final int blade=i;c.actionLater(i*SWORD_GAP_TICKS,()->{if(!target.isAlive()||!c.enemy(target)||target.distanceToSqr(c.player)>RANGE*RANGE)return;Vec3 from=c.player.position().add(SwordVisuals.passiveSlot(blade));AbilityVisuals.beam(c,from,target.position().add(0,.8,0),.12);c.damage(target,DAMAGE.value(c),DAMAGE.kind(),false);});}
}
@Override public void release(Cast c){}
}

