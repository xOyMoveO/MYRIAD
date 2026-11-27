package com.myriad.character.skills.xuanchen.rework;

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
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class SealedBoundarySkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 170, 74.75f, 1.1f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SealedBoundarySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){InkVisuals.warn(c,c.point,4,14);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 4;}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
 @Override public void release(Cast c){InkCastCues.release(c);Vec3 at=c.point;c.later(14,false,()->{InkCastCues.activate(c);com.myriad.character.ability.AbilityVisuals.field(c,at,4,60);InkVisuals.closeBoundary(c,at);
 for(var t:c.enemies(at,4))c.damage(t,DAMAGE.value(c),DAMAGE.kind());
 for(var t:c.enemies(at,4)){c.root(t,20,false);InkVisuals.binding(c,t,12);}
 c.tickFor(60,age->{if(age%4==0){for(var t:c.enemies(at,4))c.slow(t,.2f,8);}});});}
}
