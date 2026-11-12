package com.myriad.character.skills.sulan.rework;

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
public final class WindBarrierSkill extends AbilitySkill {
    public WindBarrierSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.arc(c,c.player.position(),2,100);}
    @Override public void release(Cast c){com.myriad.character.skills.sulan.FengQiangSkill.createWall(c.player,c.data,50+4*(c.rank-1),false);}
}
