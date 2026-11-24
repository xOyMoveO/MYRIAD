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
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return 14+c.rank;}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
    @Override public boolean canRelease(Cast c){return c.point!=null&&c.safe(c.point)&&c.safe(c.player.position());}
 @Override public void release(Cast c){
     com.myriad.character.ability.AbilityVisuals.cancelSkill(c);
     InkCastCues.release(c);
     c.portal(c.player.position(),c.point,80+6*(c.rank-1),(from,to)->InkVisuals.portalTravel(c,from,to));
 }
}
