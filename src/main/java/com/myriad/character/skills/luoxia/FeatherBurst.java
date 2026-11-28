package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;

final class FeatherBurst extends LuoxiaSkill {
    private static final float THIRD_HIT_MULTIPLIER = .50f;
    private static final AbilityDamageFormula FIRST_TWO = AbilityDamageFormula.bonusPhysical(50, 5f / 6 * 1.25f, .20f)
            .segment("skill.myriad.damage.first_two_arrows", 2);
    private static final AbilityDamageFormula THIRD = FIRST_TWO.scaled(THIRD_HIT_MULTIPLIER)
            .segment("skill.myriad.damage.third_arrow", 1);

    @Override public String singleTargetTotalKey() { return "skill.myriad.damage.full_total"; }

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(FIRST_TWO, THIRD);
    }

    FeatherBurst(Definition definition) { super(definition); }

    @Override public void release(Cast c) {
        var contacts = new HashMap<UUID, Integer>();
        LuoxiaBow.volley(c, 3, 5, 0, shot -> {
            LuoxiaBowPose.skill(c, com.myriad.network.LuoxiaBowPosePayload.Phase.RELEASE, shot + 1, c.player.getLookAngle());
            LuoxiaVfx.shot(c, false, 1.20f + .09f * shot);
            LuoxiaArrow.launch(c, c.player.getLookAngle(), 19, 1.65, LuoxiaArrow.Shape.BURST, false, target -> {
                int count = contacts.merge(target.getUUID(), 1, Integer::sum);
                c.damageRapid(target, (count == 3 ? THIRD : FIRST_TWO).value(c), DamageCalc.Kind.PHYSICAL);
                return true;
            });
        });
    }
}
