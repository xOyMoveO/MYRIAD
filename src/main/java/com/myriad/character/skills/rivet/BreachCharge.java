package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.DamageCalc;

/** A fixed launch direction, fourteen local-action ticks of warning, then first-enemy contact. */
public final class BreachCharge extends RivetSkill {
    public BreachCharge(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 14; }
    @Override public boolean canRelease(AbilityRuntime.Cast c) {
        return c.data.getBuff("g1_root") <= 0 && c.data.getBuff("g1_stun") <= 0;
    }
    @Override public void preparing(AbilityRuntime.Cast c) { RivetVfx.chargeWindup(c); }
    @Override public void release(AbilityRuntime.Cast c) {
        var direction = c.forward(1);
        c.actionDashFirst(direction.scale(5), 10, .45, target -> {
            boolean effective = c.damage(target, c.value(147.5f, 55, .75f), DamageCalc.Kind.PHYSICAL);
            RivetVfx.chargeHit(c, target);
            if (effective && target.isAlive()) RivetPush.start(c, target, direction, 2, c.value(37.5f, 15, .25f), 16, 20, false);
        }, () -> { });
        RivetVfx.chargeStart(c);
    }
}
