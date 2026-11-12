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
public final class SealedBoundarySkill extends AbilitySkill {
    public SealedBoundarySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,4,14);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 4;}
 @Override public void release(Cast c){Vec3 at=c.point;c.ring(at,4,false);c.later(14,false,()->{com.myriad.character.ability.AbilityVisuals.field(c,at,4,60);
 c.area(at,4,c.value(170,65,1.1f),MAGIC);for(var t:c.enemies(at,4))c.root(t,20,false);
 c.tickFor(60,age->{if(age%4==0){c.ring(at,4,false);for(var t:c.enemies(at,4))c.slow(t,.2f,8);}});});}
}
