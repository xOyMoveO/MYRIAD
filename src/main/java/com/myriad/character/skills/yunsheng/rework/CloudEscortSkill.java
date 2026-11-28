package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class CloudEscortSkill extends AbilitySkill {
    public static final double RANGE = 8;
    public static final double RADIUS = 3;
    public static final int DASH_TICKS = 10;
    public static final int SHIELD_TICKS = 60;
    public static final float HASTE = .2f;
    public static final int HASTE_TICKS = 30;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", RANGE,
            "radius", RADIUS,
            "shieldSeconds", SHIELD_TICKS / 20.0,
            "hastePercent", Math.round(100 * HASTE),
            "hasteSeconds", HASTE_TICKS / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 110, 31.25f, .5f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public CloudEscortSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearSegment(c.player,c.point);}
@Override public void release(Cast c){c.actionDash(c.point.subtract(c.player.position()),DASH_TICKS,0,(t,first)->{},()->{for(var ally:c.allies(c.player.position(),RADIUS)){c.shield(ally,SHIELD.value(c),SHIELD_TICKS);c.slow(ally,-HASTE,HASTE_TICKS);}AbilityVisuals.burst(c,c.player.position(),2);},false,false);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.69,false);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void dashPose(Cast c,DashPose phase,Vec3 direction){WarSpiritHandCues.dash(c,phase,false);}
}

