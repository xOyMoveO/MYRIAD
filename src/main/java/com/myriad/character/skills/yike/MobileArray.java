package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;

/** Four manually committed shots; deployment is stationary, the resulting stance is mobile. */
public final class MobileArray extends AbilitySkill {
    public MobileArray(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 7; }
    @Override public boolean hasOwnCastSound() { return true; }
    @Override public boolean canRelease(Cast c) { return !YikeStance.active(c.player); }
    @Override public boolean recastAvailable(Cast c) { return YikeStance.recastAvailable(c); }
    @Override public boolean recastWhileRooted() { return true; }
    @Override public boolean recastDuringChannel() { return true; }
    @Override public void recast(Cast c) { YikeStance.recast(c); }
    @Override public void release(Cast c) { YikeStance.enter(c, true); }
}
