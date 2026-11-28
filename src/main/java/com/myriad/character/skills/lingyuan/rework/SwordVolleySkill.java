package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordVolleySkill extends AbilitySkill {
    public static final int SHOTS=3, SHOT_GAP_TICKS=4;
    public static final double RANGE=18, SPEED=1.5, HIT_RADIUS=.7;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 12.5f, .25f).segment("skill.myriad.damage.segment.sword", SHOTS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range", RANGE, "shots", SHOTS, "gap", SHOT_GAP_TICKS / 20.0);
    }

    public SwordVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    for(int i=0;i<SHOTS;i++){
        int shot=i;
        c.actionLater(shot*SHOT_GAP_TICKS,()->{
            c.actionProjectile(RANGE,SPEED,HIT_RADIUS,false,false,(t,back)->c.damage(t,DAMAGE.value(c),DAMAGE.kind()));
            LingyuanHandCues.volley(c,shot);
        });
    }
}
@Override public void present(Cast c){SwordVisuals.volley(c);}
}

