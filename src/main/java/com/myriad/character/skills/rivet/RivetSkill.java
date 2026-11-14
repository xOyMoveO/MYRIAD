package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilitySkill;

/** Shared presentation policy; each candidate owns its combat rules. */
abstract class RivetSkill extends AbilitySkill {
    RivetSkill(Definition definition) { super(definition); }
    @Override public boolean hasOwnCastSound() { return true; }
}
