package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import net.minecraft.world.phys.Vec3;

final class SunsetLine extends LuoxiaSkill {
    SunsetLine(Definition definition) { super(definition); }
    private static final int WINDUP = 17, LOCK_AT = 10;
    private static final double RANGE = 32;
    @Override public int commitWindupTicks() { return WINDUP; }
    @Override public void windupPose(Cast c, boolean winding) {
        var aim = c.memory("aim");
        if (!winding) { aim.point = null; return; }
        if (c.windupProgress() == 0) aim.point = null;
        if (c.windupProgress() * WINDUP + 1E-5 >= LOCK_AT) {
            if (aim.point == null) aim.point = c.player.getLookAngle().normalize();
            LuoxiaVfx.warning(c, aim.point, RANGE);
        } else LuoxiaVfx.charge(c, true);
    }

    static float falloff(int hero) { return switch (hero) { case 1 -> 1; case 2 -> 180f / 260; default -> 125f / 260; }; }

    @Override public void release(Cast c) {
        var aim = c.memory("aim");
        Vec3 direction = aim.point == null ? c.player.getLookAngle() : aim.point;
        aim.point = null;
        LuoxiaVfx.shot(c, true, .72f);
        int[] heroes = {0};
        LuoxiaArrow.launch(c, direction, RANGE, 3.25, LuoxiaArrow.Shape.LANCE, false, target -> {
            if (c.hero(target)) {
                c.damage(target, damageAtRank(c, 175, 260, 1.05f) * falloff(++heroes[0]), DamageCalc.Kind.PHYSICAL);
                return heroes[0] == 3;
            }
            c.damage(target, damageAtRank(c, 40, 60, .20f), DamageCalc.Kind.PHYSICAL);
            return false;
        });
    }
}
