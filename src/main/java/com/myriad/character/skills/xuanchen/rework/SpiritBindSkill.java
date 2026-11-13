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
public final class SpiritBindSkill extends AbilitySkill {
    public SpiritBindSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.seal(c,c.player.position(),.6,false);}
    @Override public void release(Cast c){c.actionProjectile(16,1.25,.9,false,false,(t,back)->{c.damage(t,c.value(45,15,.55f),MAGIC);c.root(t,12+2*(c.rank-1),false);});}
}
