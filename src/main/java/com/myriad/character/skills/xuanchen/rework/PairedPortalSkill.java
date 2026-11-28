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
    public static final int RANGE_BASE = 14;
    public static final int DURATION_BASE = 80;
    public static final int DURATION_GROWTH = 6;

    public static int range(int rank) { return RANGE_BASE + rank; }
    public static int durationTicks(int rank) { return DURATION_BASE + DURATION_GROWTH * (rank - 1); }

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", range(rank),
            "durationSeconds", durationTicks(rank) / 20.0,
            "reuseSeconds", AbilityRuntime.PORTAL_TRAVEL_REUSE_TICKS / 20.0);
    }

    public PairedPortalSkill(Definition definition){super(definition);}
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return range(c.rank);}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
    @Override public boolean canRelease(Cast c){return c.point!=null&&c.safe(c.point)&&c.safe(c.player.position());}
 @Override public void release(Cast c){
     com.myriad.character.ability.AbilityVisuals.cancelSkill(c);
     InkCastCues.release(c);
     c.portal(c.player.position(),c.point,durationTicks(c.rank),(from,to)->InkVisuals.portalTravel(c,from,to));
 }
}
