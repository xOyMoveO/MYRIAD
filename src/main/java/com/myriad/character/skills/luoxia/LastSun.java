package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.HeroProjectileTuning;
import java.util.List;
import java.util.Map;

public final class LastSun extends LuoxiaSkill {
    public static final double RANGE = 28;
    private static final double BASE_FLIGHT_SPEED = 3;

    public static double speedBlocksPerActionTick() {
        return BASE_FLIGHT_SPEED * HeroProjectileTuning.SPEED;
    }

    public static double entityPaddingBlocks() {
        return LuoxiaArrow.entityPadding(LuoxiaArrow.Shape.LAST_SUN);
    }
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.purchased(DamageCalc.Kind.PHYSICAL, 150, 6.75f, .50f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range_blocks", RANGE, "windup_seconds", commitWindupTicks() / 20.0);
    }

    LastSun(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 10; }
    @Override public float windupWalkFactor(Cast c) { return .60f; }
    @Override public void windupPose(Cast c, boolean winding) {
        LuoxiaBowPose.windup(c, winding, null);
        if (winding) LuoxiaVfx.charge(c, false);
    }

    @Override public void release(Cast c) {
        LuoxiaBowPose.skill(c, com.myriad.network.LuoxiaBowPosePayload.Phase.RELEASE, 1, c.player.getLookAngle());
        LuoxiaVfx.shot(c, true, .90f);
        LuoxiaArrow.launch(c, c.player.getLookAngle(), RANGE, BASE_FLIGHT_SPEED, LuoxiaArrow.Shape.LAST_SUN, false,
                target -> { c.damage(target, DAMAGE.value(c), DamageCalc.Kind.PHYSICAL); return true; });
    }
}
