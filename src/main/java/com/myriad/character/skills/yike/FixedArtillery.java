package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;

/** Five manually committed shots after a stationary, unpaid deployment windup. */
public final class FixedArtillery extends AbilitySkill {
    public FixedArtillery(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 9; }
    @Override public void preparing(Cast c){YikeVfx.event(c,"deploy",c.player.getEyePosition(),c.player.getEyePosition().add(c.player.getLookAngle()),.35f,commitWindupTicks());}
    @Override public boolean hasOwnCastSound() { return true; }
    @Override public boolean canRelease(Cast c) { return !YikeStance.active(c.player); }
    @Override public boolean recastAvailable(Cast c) { return YikeStance.recastAvailable(c); }
    @Override public boolean recastWhileRooted() { return true; }
    @Override public boolean recastDuringChannel() { return true; }
    @Override public void recast(Cast c) { YikeStance.recast(c); }
    @Override public void release(Cast c) { YikeStance.enter(c, false); }
}
