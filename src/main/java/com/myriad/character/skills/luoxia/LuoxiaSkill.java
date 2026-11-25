package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.combat.SilenceSystem;

abstract class LuoxiaSkill extends AbilitySkill {
    LuoxiaSkill(Definition definition) { super(definition); }
    @Override public boolean hasOwnCastSound() { return true; }

    @Override public boolean canRelease(Cast c) {
        return LuoxiaRework.holdsBow(c.player) && !LuoxiaBow.sequence(c.player);
    }

    @Override public void preparing(Cast c) {
        if (slot() != 1) LuoxiaBow.cancelDraw(c.player);
    }

    static boolean interrupted(Cast c) {
        return !LuoxiaRework.holdsBow(c.player) || c.data.getBuff("g1_stun") > 0
                || SilenceSystem.isSilenced(c.player);
    }
}
