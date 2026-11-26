package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import net.minecraft.world.phys.Vec3;
import java.util.List;

final class SunsetLine extends LuoxiaSkill {
    private static final AbilityDamageFormula FIRST = AbilityDamageFormula.purchased(DamageCalc.Kind.PHYSICAL, 175, 42.5f, .80f)
            .segment("skill.myriad.damage.hero_first", 1);
    private static final AbilityDamageFormula SECOND = FIRST.scaled(180f / 260)
            .segment("skill.myriad.damage.hero_second", 1);
    private static final AbilityDamageFormula THIRD = FIRST.scaled(125f / 260)
            .segment("skill.myriad.damage.hero_third", 1);
    private static final AbilityDamageFormula NON_HERO = AbilityDamageFormula.purchased(DamageCalc.Kind.PHYSICAL, 40, 10, .80f * .20f / 1.05f)
            .segment("skill.myriad.damage.non_hero", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(FIRST, SECOND, THIRD, NON_HERO);
    }

    SunsetLine(Definition definition) { super(definition); }
    private static final int WINDUP = 17, LOCK_AT = 10;
    private static final double RANGE = 32;
    @Override public int commitWindupTicks() { return WINDUP; }
    @Override public void windupPose(Cast c, boolean winding) {
        var aim = c.memory("aim");
        if (!winding) { LuoxiaBowPose.windup(c, false, null); aim.point = null; return; }
        if (c.windupProgress() == 0) aim.point = null;
        if (c.windupProgress() * WINDUP + 1E-5 >= LOCK_AT) {
            if (aim.point == null) aim.point = c.player.getLookAngle().normalize();
            LuoxiaVfx.warning(c, aim.point, RANGE);
        } else LuoxiaVfx.charge(c, true);
        LuoxiaBowPose.windup(c, true, aim.point);
    }

    private static AbilityDamageFormula heroDamage(int hero) {
        return switch (hero) { case 1 -> FIRST; case 2 -> SECOND; default -> THIRD; };
    }

    @Override public void release(Cast c) {
        var aim = c.memory("aim");
        Vec3 direction = aim.point == null ? c.player.getLookAngle() : aim.point;
        aim.point = null;
        LuoxiaBowPose.skill(c, com.myriad.network.LuoxiaBowPosePayload.Phase.RELEASE, 1, direction);
        LuoxiaVfx.shot(c, true, .72f, direction, 0);
        int[] heroes = {0};
        LuoxiaArrow.launchResolved(c, direction, RANGE, 3.25, LuoxiaArrow.Shape.LANCE, false, 0, target -> {
            boolean hero = c.hero(target);
            var formula = hero ? heroDamage(++heroes[0]) : NON_HERO;
            boolean hit = c.damage(target, formula.value(c), DamageCalc.Kind.PHYSICAL);
            return new LuoxiaArrow.Impact(hero && heroes[0] == 3,
                    hit ? LuoxiaArrow.Feedback.HIT : LuoxiaArrow.Feedback.GRAZE);
        });
    }
}
