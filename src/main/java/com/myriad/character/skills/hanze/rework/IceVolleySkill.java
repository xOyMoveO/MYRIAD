package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class IceVolleySkill extends AbilitySkill {
    public static final int SHOTS=3, SHOT_GAP_TICKS=5, SLOW_TICKS=20;
    public static final double RANGE=15, SPEED=1.5, HIT_RADIUS=.75;
    public static final float SLOW=.2f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 25, 10, .2f).segment("skill.myriad.damage.segment.projectile", SHOTS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range", RANGE, "shots", SHOTS, "gap", SHOT_GAP_TICKS / 20.0, "slow_pct", SLOW * 100);
    }

    public IceVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    for(int i=0;i<SHOTS;i++){
        int shot=i;
        c.actionLater(shot*SHOT_GAP_TICKS,()->{
            c.actionProjectile(RANGE,SPEED,HIT_RADIUS,false,false,(t,back)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);});
            StaffHandCues.volley(c,shot);
        });
    }
}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.75,false);}
}

