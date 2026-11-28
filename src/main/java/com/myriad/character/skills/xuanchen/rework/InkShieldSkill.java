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
public final class InkShieldSkill extends AbilitySkill {
    public static final double ALLY_RANGE = 10;
    public static final int SHIELD_TICKS = 60;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "allyRange", ALLY_RANGE,
            "shieldSeconds", SHIELD_TICKS / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 90, 31.25f, .6f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public InkShieldSkill(Definition definition){super(definition);}
    @Override public void release(Cast c){
        LivingEntity picked=c.target(ALLY_RANGE,true);
        LivingEntity target=picked==null?c.player:picked;
        InkCastCues.release(c,target==c.player);
        c.shield(target,SHIELD.value(c),SHIELD_TICKS,()->InkVisuals.deliver(c,target,.8),
                source->InkVisuals.shieldTorn(c,target,source));
    }
}
