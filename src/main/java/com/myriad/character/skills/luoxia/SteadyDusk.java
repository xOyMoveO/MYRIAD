package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;

final class SteadyDusk extends LuoxiaSkill {
    SteadyDusk(Definition definition) { super(definition); }
    static final int FULL_TICKS = 15;
    static final double RANGE = 26;
    static float multiplier(int rank) { return 1.21f + .06f * Math.clamp(rank - 1, 0, 4); }
    @Override public void release(Cast c) {}
}
