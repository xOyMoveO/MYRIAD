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
public final class WindDomainSkill extends AbilitySkill {
    public WindDomainSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.following(c,com.myriad.character.ability.AbilityVfxGeometry.SEAL,c.player,5,80);}
    @Override public void release(Cast c){WindCastCues.release(c);c.area(c.player.position(),5,c.value(90,35,.65f),PHYSICAL);
 c.tickFor(80,age->{if(age%4==0){Vec3 at=c.player.position();
     for(var ally:c.allies(at,5)){c.slow(ally,-.15f,8);if(age%8==0)WindVisuals.domainWind(c,ally,false);}
     for(var t:c.enemies(at,5)){c.slow(t,.25f,8);if(age%8==0)WindVisuals.domainWind(c,t,true);}
 }});}
}
