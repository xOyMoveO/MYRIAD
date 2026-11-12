package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class CloudEscortSkill extends AbilitySkill {
 public CloudEscortSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public boolean requiresStandingPoint(){return true;}
@Override public double pointRange(Cast c){return 8;}
@Override public double previewRadius(Cast c){return 3;}
@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&c.point!=null&&AbilityTargeting.clearSegment(c.player,c.point);}
@Override public void release(Cast c){c.dash(c.point.subtract(c.player.position()),10,0,(t,first)->{},()->{for(var ally:c.allies(c.player.position(),3)){c.shield(ally,c.value(110,25,.5f),60);c.slow(ally,-.2f,30);}AbilityVisuals.burst(c,c.player.position(),2);},false,false);}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1,80);}
}

