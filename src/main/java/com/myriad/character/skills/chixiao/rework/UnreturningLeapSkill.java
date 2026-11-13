package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class UnreturningLeapSkill extends AbilitySkill {
 public UnreturningLeapSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return 12;}
@Override public double previewRadius(Cast c){return 4.5;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearArc(c.player,c.point,14,2);}
@Override public void release(Cast c){Vec3 at=c.point;c.actionLeap(at,14,2,()->{c.area(c.player.position(),4.5,c.value(170,65,.85f),PHYSICAL);for(var t:c.enemies(c.player.position(),4.5))c.slow(t,.4f,40);});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,4.5,14);}
}

