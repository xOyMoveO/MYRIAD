package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class RimeSkinSkill extends AbilitySkill {
    public static final int COOLDOWN_TICKS=240, SHIELD_TICKS=60, SLOW_TICKS=20;
    public static final double RADIUS=3;
    public static final float SLOW=.2f;

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 65, 24, .35f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("shield_time", SHIELD_TICKS / 20.0, "cooldown", COOLDOWN_TICKS / 20.0, "radius", RADIUS, "slow_pct", SLOW * 100);
    }

    public RimeSkinSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(c.now()<c.memory().cooldown)return;c.memory().cooldown=c.now()+COOLDOWN_TICKS;c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);for(var t:c.enemies(c.player.position(),RADIUS))c.slow(t,SLOW,SLOW_TICKS);}
@Override public void tickPassive(Cast c){if(c.now()>=c.memory().cooldown)AbilityVisuals.ready(c);}
@Override public void release(Cast c){}
}

