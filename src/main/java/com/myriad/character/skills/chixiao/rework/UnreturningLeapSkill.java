package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class UnreturningLeapSkill extends AbilitySkill {
    public static final double RANGE = 12, RADIUS = 4.5, HEIGHT = 2;
    public static final int LEAP_TICKS = 14, SLOW_TICKS = 40;
    public static final float SLOW = .40f;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(170, 81.25f, .85f, .8f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public UnreturningLeapSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearArc(c.player,c.point,LEAP_TICKS,HEIGHT);}
@Override public void release(Cast c){Vec3 at=c.point;c.actionLeap(at,LEAP_TICKS,HEIGHT,()->{WarSpiritHandCues.landed(c);c.area(c.player.position(),RADIUS,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(c.player.position(),RADIUS))c.slow(t,SLOW,SLOW_TICKS);});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,RADIUS,LEAP_TICKS);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void leapPose(Cast c,DashPose phase,double progress){WarSpiritHandCues.leap(c,phase,progress);}
}

