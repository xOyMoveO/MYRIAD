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
public final class ShadowAssaultSkill extends AbilitySkill {
    public static final double DASH_DISTANCE=9;
    public static final int DASH_TICKS=5;
    public ShadowAssaultSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.assault(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.DASH,10);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
 @Override public void release(Cast c){c.dash(c.forward(DASH_DISTANCE),DASH_TICKS,1.2,(t,first)->{c.damage(t,c.value(70,20,.8f),PHYSICAL);if(first&&c.behind(t))c.slow(t,.4f,20);},()->{},false,false);}
}
