package com.myriad.character.skills.fenjue.rework;

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
public final class LavaCageSkill extends AbilitySkill {
    public LavaCageSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,3.5,12);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 3.5;}
 @Override public void release(Cast c){FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.CAGE_FIRE,10);
 Vec3 at=c.point;FlameMajorAudio.cageCast(c,at);c.ring(at,3.5,false);c.later(12,false,()->{FlameMajorAudio.cageOpen(c,at);com.myriad.character.ability.AbilityVisuals.field(c,at,3.5,80);
 c.repeat(80,30,i->FlameMajorAudio.burning(c,at));
 c.tickFor(80,age->{if(age%4==0){c.ring(at,3.5,false);for(var t:c.enemies(at,3.5))c.slow(t,.25f,8);}});
 c.repeat(80,20,i->c.pulse(at,3.5,c.value(25,9,.2f),PHYSICAL));});}
}
