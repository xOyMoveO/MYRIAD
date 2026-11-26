package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;

final class ThousandFeathers extends LuoxiaSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.purchased(DamageCalc.Kind.PHYSICAL, 54, 13, .80f / 3)
            .segment("skill.myriad.damage.per_round", 3);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    ThousandFeathers(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 7; }
    @Override public float windupWalkFactor(Cast c) { return .8f; }
    @Override public void windupPose(Cast c, boolean winding) {
        LuoxiaBowPose.windup(c, winding, null);
        if (winding) LuoxiaVfx.charge(c, false);
    }

    @Override public void release(Cast c) {
        var initial = c.player.getLookAngle().normalize();
        LuoxiaBow.volley(c, 3, 12, 0, round -> {
            var center = LuoxiaArrow.limitTurn(initial, c.player.getLookAngle(), Math.toRadians(12));
            var roundHits = new HashSet<UUID>();
            LuoxiaBowPose.skill(c, com.myriad.network.LuoxiaBowPosePayload.Phase.RELEASE, round + 1, center);
            LuoxiaVfx.shot(c, false, 1f + .09f * round, center, round + 1);
            for (int lane = -1; lane <= 1; lane++) {
                LuoxiaArrow.launchResolved(c, center.yRot((float)Math.toRadians(lane * 8)), 22, 2,
                        LuoxiaArrow.Shape.VOLLEY, false, lane, target -> {
                            if (!roundHits.add(target.getUUID())) return new LuoxiaArrow.Impact(true, LuoxiaArrow.Feedback.NONE);
                            boolean hit = c.damageRapid(target, DAMAGE.value(c), DamageCalc.Kind.PHYSICAL);
                            return new LuoxiaArrow.Impact(true, hit ? LuoxiaArrow.Feedback.HIT : LuoxiaArrow.Feedback.GRAZE);
                        });
            }
        });
    }
}
