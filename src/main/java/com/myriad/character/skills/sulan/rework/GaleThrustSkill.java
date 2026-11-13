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
public final class GaleThrustSkill extends AbilitySkill {
    public GaleThrustSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.arc(c,c.player.position(),1.4,55);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;} @Override public void release(Cast c){
 c.actionDash(c.forward(6),7,1.2,(t,first)->c.damage(t,c.value(50,15,.6f),PHYSICAL),()->{},false,false);}
}
