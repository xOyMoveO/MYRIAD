package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityRuntime.Hit;

final class ChaseDusk extends LuoxiaSkill {
    ChaseDusk(Definition definition) { super(definition); }
    static final int WINDOW = 24;
    static final float MULTIPLIER = .90f;

    static double drawTicks(int rank) { return 6 - .4 * Math.clamp(rank, 1, 5); }

    @Override public void release(Cast c) {}
    @Override public void onHit(Hit hit) {
        if (!hit.basic()) return;
        Cast c = hit.passiveContext();
        if (c != null && c.hero(hit.target())) LuoxiaBow.refreshChain(c.player, c.now() + WINDOW);
    }
}
