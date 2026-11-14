package com.myriad.character.skills.rivet;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;

/** Its reward is consumed only by a real basic-attack damage event, after the ordinary critical. */
public final class BreachRhythm extends RivetSkill {
    public BreachRhythm(Definition definition) { super(definition); }
    @Override public void release(AbilityRuntime.Cast c) { }
    public static void wallHit(ServerPlayer player) {
        var skill = FirstGroupSkills.node("rivet", 1);
        var data = PlayerDataProvider.get(player);
        if (skill == null || data.skillLevel(skill.getId()) <= 0) return;
        var c = AbilityRuntime.context(player, skill);
        if (c.now() < c.memory().cooldown) return;
        c.memory().cooldown = c.now() + 100;
        c.memory().until = c.now() + 80;
        c.buff("g1_rivet_rhythm", 80);
        PlayerDataProvider.syncToClient(player);
    }
    @Override public void onHit(AbilityRuntime.Hit hit) {
        if (!hit.basic() || !hit.direct() || hit.amount() <= 0) return;
        var c = AbilityRuntime.context(hit.player(), this);
        if (c.memory().until <= c.now()) return;
        c.memory().until = 0;
        c.buff("g1_rivet_rhythm", 0);
        // direct=false selects the uncritical skill path and cannot dispatch this passive again.
        c.damage(hit.target(), c.value(22.5f, 8, .25f), DamageCalc.Kind.PHYSICAL, false);
        var major = FirstGroupSkills.resolve(CharacterType.byId(c.data.getCharacterId()), 2, c.data.getSkillLevels());
        if (major != null) c.data.setCooldown(major.getId(), Math.max(0, c.data.getCooldown(major.getId()) - 40));
        PlayerDataProvider.syncToClient(c.player);
    }
}
