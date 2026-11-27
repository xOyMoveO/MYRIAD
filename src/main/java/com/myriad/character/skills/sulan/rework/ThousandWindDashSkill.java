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
public final class ThousandWindDashSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(160, 81.25f, 1.2f, .8f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public ThousandWindDashSkill(Definition definition){super(definition);}
    @Override public void windupPose(Cast c,boolean winding){WindCastCues.windup(c,winding);}
    @Override public void dashPose(Cast c,DashPose phase,Vec3 direction){WindCastCues.dash(c,phase,direction);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.charging(c,10,12,.35);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
 @Override public void release(Cast c){c.windup(10,0,()->c.actionDash(c.forward(12),14,1.2,(t,first)->{},
 ()->{Vec3 at=c.player.position();c.area(at,2.5,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(at,2.5))c.slow(t,.3f,30);},true,true));}
}
