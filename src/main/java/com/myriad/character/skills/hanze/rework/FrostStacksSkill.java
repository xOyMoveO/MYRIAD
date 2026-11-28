package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrostStacksSkill extends AbilitySkill {
    public static final int MIN_HIT_GAP_TICKS=4, STACKS=4, STACK_WINDOW_TICKS=120, IMMUNITY_TICKS=120, ROOT_TICKS=20;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 25, 12, .2f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("stacks", STACKS, "root", ROOT_TICKS / 20.0, "immunity", IMMUNITY_TICKS / 20.0);
    }

    public FrostStacksSkill(Definition definition){super(definition);}

    @Override
    public void onHit(AbilityRuntime.Hit hit) {
        Cast cast = AbilityRuntime.context(hit.player(), this);
        var frost = cast.memory(hit.target().getUUID().toString());
        long now = cast.now();
        if (now < frost.cooldown || now - frost.last < MIN_HIT_GAP_TICKS) return;
        if (!hit.basic()) {
            if (frost.castTokens == null) frost.castTokens = new HashSet<>();
            // Earlier projectiles can arrive after hits from other skills or basic attacks.
            if (!frost.castTokens.add(AbilityRuntime.sourceCast())) return;
        }

        if (now > frost.until) frost.count = 0;
        frost.last = now;
        frost.until = now + STACK_WINDOW_TICKS;
        if (++frost.count < STACKS) {
            AbilityVisuals.following(cast, AbilityVfxGeometry.BURST, hit.target(), .39, 200 + frost.count, 9);
            return;
        }
        frost.count = 0;
        frost.cooldown = now + IMMUNITY_TICKS;
        cast.root(hit.target(), ROOT_TICKS, false);
        cast.damage(hit.target(), DAMAGE.value(cast), DAMAGE.kind(), false);
        AbilityVisuals.pillar(cast, hit.target().position(), 2.2);
    }

    @Override public void release(Cast cast) {}
}

