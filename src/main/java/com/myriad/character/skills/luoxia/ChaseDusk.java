package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityRuntime.Hit;
import com.myriad.combat.DamageCalc;
import java.util.List;

final class ChaseDusk extends LuoxiaSkill {
    ChaseDusk(Definition definition) { super(definition); }
    static final int WINDOW = 24;
    static final float MULTIPLIER = .90f;
    private static final AbilityDamageFormula QUICK_ARROW =
            AbilityDamageFormula.physicalAttack(DamageCalc.Kind.PHYSICAL, 0, 0, MULTIPLIER);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(QUICK_ARROW);
    }

    static double drawTicks(int rank) { return 6 - .4 * Math.clamp(rank, 1, 5); }

    @Override public void release(Cast c) {}
    @Override public void onHit(Hit hit) {
        if (!hit.basic()) return;
        Cast c = hit.passiveContext();
        if (c != null && c.hero(hit.target())) LuoxiaBow.refreshChain(c.player, c.now() + WINDOW);
    }
}
