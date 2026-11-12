package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SoulPursuitSkill extends AbilitySkill {
 public SoulPursuitSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&AbilityTargeting.clearSweep(c.player,c.forward(.875));}
@Override public void release(Cast c){var m=c.memory();m.point=c.player.position();m.until=c.now()+60;c.buff(getId()+"_recast",60);c.dash(c.forward(7),8,1.2,(t,first)->{c.damage(t,c.value(50,15,.4f),PHYSICAL);c.slow(t,.3f,30);},()->{},false,false);}
@Override public boolean recastAvailable(Cast c){return c.memory().point!=null&&c.memory().until>c.now();}
@Override public void recast(Cast c){Vec3 to=c.memory().point;if(to==null||!c.safe(to)||!AbilityTargeting.clearSegment(c.player,to))return;c.memory().until=0;c.memory().point=null;c.buff(getId()+"_recast",0);c.dash(to.subtract(c.player.position()),8,0,(t,first)->{},()->{},false,false);}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1.5,75);}
}

