package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrostShieldSkill extends AbilitySkill {
    public static final int SHIELD_TICKS=80, SLOW_TICKS=30;
    public static final double RADIUS=3;
    public static final float SLOW=.25f;

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 150, 50, .65f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("shield_time", SHIELD_TICKS / 20.0, "radius", RADIUS, "slow_pct", SLOW * 100, "slow_time", SLOW_TICKS / 20.0);
    }

    public FrostShieldSkill(Definition definition){super(definition);}

@Override public void release(Cast c){StaffHandCues.release(c);c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);for(var t:c.enemies(c.player.position(),RADIUS))c.slow(t,SLOW,SLOW_TICKS);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),RADIUS,false);}
}

