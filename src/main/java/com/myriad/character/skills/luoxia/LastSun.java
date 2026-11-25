package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;

final class LastSun extends LuoxiaSkill {
    LastSun(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 10; }
    @Override public float windupWalkFactor(Cast c) { return .60f; }
    @Override public void windupPose(Cast c, boolean winding) { if (winding) LuoxiaVfx.charge(c, false); }

    @Override public void release(Cast c) {
        LuoxiaVfx.shot(c, true, .90f);
        LuoxiaArrow.launch(c, c.player.getLookAngle(), 28, 3, LuoxiaArrow.Shape.LAST_SUN, false,
                target -> { c.damage(target, damageAtRank(c, 108, 180, .76f), DamageCalc.Kind.PHYSICAL); return true; });
    }
}
