package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordMantleSkill extends AbilitySkill {
    public static final int SHIELD_TICKS=80, GUARD_TICKS=40;
    public static final double INCOMING_RATE=.85;

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 100, 37.5f, .55f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("shield_time", SHIELD_TICKS / 20.0, "guard_time", GUARD_TICKS / 20.0, "guard_pct", (1 - INCOMING_RATE) * 100);
    }

    public SwordMantleSkill(Definition definition){super(definition);}

@Override public void release(Cast c){LingyuanHandCues.release(c);c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);c.memory().until=c.now()+GUARD_TICKS;SwordVisuals.mantle(c);}
@Override public double incomingMultiplier(Cast c){return c.memory().until>c.now()?INCOMING_RATE:1;}
}

