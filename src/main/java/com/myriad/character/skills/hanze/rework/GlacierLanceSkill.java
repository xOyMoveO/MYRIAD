package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class GlacierLanceSkill extends AbilitySkill {
    public static final int SLOW_TICKS=40;
    public static final double RANGE=18, SPEED=1.2, HIT_RADIUS=1;
    public static final float SLOW=.4f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 85, 27.5f, .55f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range", RANGE, "slow_pct", SLOW * 100, "slow_time", SLOW_TICKS / 20.0);
    }

    public GlacierLanceSkill(Definition definition){super(definition);}

@Override public void release(Cast c){StaffHandCues.release(c);c.actionProjectile(RANGE,SPEED,HIT_RADIUS,true,false,(t,back)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);});}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1.3,80);}
}

