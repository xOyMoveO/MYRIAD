package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class CloudEscortSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 110, 31.25f, .5f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public CloudEscortSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return 8;}
@Override public double previewRadius(Cast c){return 3;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearSegment(c.player,c.point);}
@Override public void release(Cast c){c.actionDash(c.point.subtract(c.player.position()),10,0,(t,first)->{},()->{for(var ally:c.allies(c.player.position(),3)){c.shield(ally,SHIELD.value(c),60);c.slow(ally,-.2f,30);}AbilityVisuals.burst(c,c.player.position(),2);},false,false);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.69,false);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void dashPose(Cast c,DashPose phase,Vec3 direction){WarSpiritHandCues.dash(c,phase,false);}
}

