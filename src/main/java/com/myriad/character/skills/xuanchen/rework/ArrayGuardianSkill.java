package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ArrayGuardianSkill extends AbilitySkill {
    public static final double ALLY_RANGE = 8;
    public static final int SHIELD_TICKS = 40;
    public static final int INTERNAL_COOLDOWN = 240;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "allyRange", ALLY_RANGE,
            "shieldSeconds", SHIELD_TICKS / 20.0,
            "procSeconds", INTERNAL_COOLDOWN / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 40, 18, .25f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public ArrayGuardianSkill(Definition definition){super(definition);}
    @Override public void onCast(Cast released){Cast c=AbilityRuntime.context(released.player,this);var m=c.memory();if(m.cooldown>c.now())return;m.cooldown=c.now()+INTERNAL_COOLDOWN;
 var ally=c.allies(c.player.position(),ALLY_RANGE).stream().filter(p->p!=c.player).min(Comparator.comparingDouble(p->p.getHealth()/p.getMaxHealth())).orElse(c.player);
 c.shield(ally,SHIELD.value(c),SHIELD_TICKS,()->InkVisuals.deliver(c,ally,.48));}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
