package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.DamageCalc;
import java.util.List;

/** Fixed-direction ten-block charge; the first effective enemy is driven until the charge stops. */
public final class BreachCharge extends RivetSkill {
    private static final AbilityDamageFormula DIRECT = AbilityDamageFormula.physicalFromSpell(147.5f, 55, .75f, .60f)
            .segment("skill.myriad.damage.direct_hit", 1);
    private static final AbilityDamageFormula WALL = AbilityDamageFormula.physicalFromSpell(37.5f, 15, .25f, .20f)
            .segment("skill.myriad.damage.wall_collision", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DIRECT, WALL);
    }

    public static final double DISTANCE = 10;
    public BreachCharge(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 14; }
    @Override public boolean canRelease(AbilityRuntime.Cast c) {
        return c.data.getBuff("g1_root") <= 0 && c.data.getBuff("g1_stun") <= 0;
    }
    @Override public void preparing(AbilityRuntime.Cast c) { RivetVfx.chargeWindup(c); }
    @Override public void release(AbilityRuntime.Cast c) {
        var direction = c.forward(1);
        c.actionDashCarrying(direction.scale(DISTANCE), 20, .45, target -> {
            boolean effective = c.damage(target, DIRECT.value(c), DamageCalc.Kind.PHYSICAL);
            RivetVfx.chargeHit(c, target, direction);
            return effective && target.isAlive() ? RivetPush.escort(c, target, direction, WALL.value(c), 16, 20) : null;
        }, () -> { });
        RivetVfx.chargeStart(c);
    }
}
