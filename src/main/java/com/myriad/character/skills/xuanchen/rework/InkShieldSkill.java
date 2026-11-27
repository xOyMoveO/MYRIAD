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
    private static final SupportAmount SHIELD = new SupportAmount("shield", 90, 31.25f, .6f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public InkShieldSkill(Definition definition){super(definition);}
    @Override public void release(Cast c){
        LivingEntity picked=c.target(10,true);
        LivingEntity target=picked==null?c.player:picked;
        InkCastCues.release(c,target==c.player);
        c.shield(target,SHIELD.value(c),60,()->InkVisuals.deliver(c,target,.8),
                source->InkVisuals.shieldTorn(c,target,source));
    }
}
