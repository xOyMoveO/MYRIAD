package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class UnreturningLeapSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(170, 65, .85f, .8f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public UnreturningLeapSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return 12;}
@Override public double previewRadius(Cast c){return 4.5;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearArc(c.player,c.point,14,2);}
@Override public void release(Cast c){Vec3 at=c.point;c.actionLeap(at,14,2,()->{WarSpiritHandCues.landed(c);c.area(c.player.position(),4.5,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(c.player.position(),4.5))c.slow(t,.4f,40);});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,4.5,14);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void leapPose(Cast c,DashPose phase,double progress){WarSpiritHandCues.leap(c,phase,progress);}
}

