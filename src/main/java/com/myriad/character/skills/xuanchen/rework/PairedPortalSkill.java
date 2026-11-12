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
public final class PairedPortalSkill extends AbilitySkill {
    public PairedPortalSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.portalAnchor(c,c.player.position());com.myriad.character.ability.AbilityVisuals.portalAnchor(c,c.point);com.myriad.character.ability.AbilityVisuals.beam(c,c.player.position().add(0,.1,0),c.point.add(0,.1,0),.08);}
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return 14+c.rank;}
    @Override public boolean canRelease(Cast c){return c.point!=null&&c.safe(c.point)&&c.safe(c.player.position());}
 @Override public void release(Cast c){c.portal(c.player.position(),c.point,80+6*(c.rank-1));}
}
