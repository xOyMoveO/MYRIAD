package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import java.util.List;

final class SteadyDusk extends LuoxiaSkill {
    SteadyDusk(Definition definition) { super(definition); }
    static final int FULL_TICKS = 20;
    static final double RANGE = 26;
    private static final float INITIAL_MULTIPLIER = 1.21f;
    private static final float MULTIPLIER_PER_RANK = .06f;
    private static final AbilityDamageFormula FULL_DRAW =
            AbilityDamageFormula.physicalAttack(DamageCalc.Kind.PHYSICAL, 0, 0, INITIAL_MULTIPLIER)
                    .withScaleGrowth(MULTIPLIER_PER_RANK);

    static float multiplier(int rank) {
        return INITIAL_MULTIPLIER + MULTIPLIER_PER_RANK * Math.clamp(rank - 1, 0, 4);
    }

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(FULL_DRAW);
    }
    @Override public void release(Cast c) {}
}
