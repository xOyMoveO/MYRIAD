package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.DamageCalc;

/** Fixed-direction ten-block charge; the first effective enemy is driven until the charge stops. */
public final class BreachCharge extends RivetSkill {
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
            boolean effective = c.damage(target, c.value(147.5f, 55, .75f), DamageCalc.Kind.PHYSICAL);
            RivetVfx.chargeHit(c, target, direction);
            return effective && target.isAlive() ? RivetPush.escort(c, target, direction, c.value(37.5f, 15, .25f), 16, 20) : null;
        }, () -> { });
        RivetVfx.chargeStart(c);
    }
}
