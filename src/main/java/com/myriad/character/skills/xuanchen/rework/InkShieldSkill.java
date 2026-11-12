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
    public InkShieldSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){var target=c.target(10,true);if(target!=null)com.myriad.character.ability.AbilityVisuals.beam(c,c.player.position().add(0,.6,0),target.position().add(0,.6,0),.12);}
    @Override public void release(Cast c){LivingEntity target=c.target(10,true);if(target==null)target=c.player;c.shield(target,c.value(90,25,.6f),60);}
}
