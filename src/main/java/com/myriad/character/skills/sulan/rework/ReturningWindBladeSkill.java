package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ReturningWindBladeSkill extends AbilitySkill {
    public static final double RANGE = 12, PROJECTILE_SPEED = 1, PROJECTILE_RADIUS = 1;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(45, 18.75f, .45f, .5f / 2).segment("skill.myriad.damage.segment.out_and_back", 2);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public ReturningWindBladeSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.arc(c,c.player.position(),1.6,130);}
    @Override public void release(Cast c){WindCastCues.release(c);c.actionProjectile(RANGE,PROJECTILE_SPEED,PROJECTILE_RADIUS,true,true,(t,back)->c.damage(t,DAMAGE.value(c),DAMAGE.kind()));}
    @Override public void projectileTurned(Cast c, Vec3 end, Vec3 outboundDirection){
        WindVisuals.turn(c,end,outboundDirection);
    }
}
