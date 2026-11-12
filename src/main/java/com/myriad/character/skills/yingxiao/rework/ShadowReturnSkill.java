package com.myriad.character.skills.yingxiao.rework;

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
public final class ShadowReturnSkill extends AbilitySkill {
    public static final double DASH_DISTANCE=7;
    public static final int DASH_TICKS=4;
    public ShadowReturnSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.returnAnchor(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.RETURN,9);}
    @Override public boolean requiresStandingPoint(){return true;}
    @Override public boolean needsPoint(){return true;} @Override public double pointRange(Cast c){return DASH_DISTANCE;}
 @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
 @Override public void release(Cast c){var m=c.memory();m.point=c.player.position();m.until=c.now()+60;c.buff(getId()+"_recast",60);
 c.dash(c.point.subtract(c.player.position()),DASH_TICKS,1.2,(t,first)->c.damage(t,c.value(55,18,.65f),PHYSICAL),()->{},false,false);}
 @Override public boolean recastAvailable(Cast c){return c.memory().point!=null&&c.memory().until>c.now();}
 @Override public void recast(Cast c){var m=c.memory();if(c.safe(m.point)){
 com.myriad.character.ability.AbilityVisuals.beam(c,c.player.position().add(0,.45,0),m.point.add(0,.45,0),.15);
 com.myriad.character.ability.AbilityVisuals.burst(c,m.point,.6);
 c.player.teleportTo(m.point.x,m.point.y,m.point.z);m.until=0;m.point=null;c.buff(getId()+"_recast",0);
 NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.RETURN,8);}}
}
