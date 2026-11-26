package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.registry.ModSounds.Night.*;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ShadowAssaultSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(70, 20, .8f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public static final double DASH_DISTANCE=9;
    public static final int DASH_TICKS=5;
    @Override public boolean hasOwnCastSound() { return true; }

    public ShadowAssaultSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){NightVisuals.assault(c);
        NightSkillPresentation.play(c,com.myriad.network.NightSkillViewPayload.DASH,10);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void dashPose(Cast c, DashPose phase, Vec3 direction) {
        if (phase == DashPose.START) NightAudio.play(c, c.player.position(), DASH);
    }

    @Override public void release(Cast c) {
        long[] nextContact = {Long.MIN_VALUE};
        c.actionDash(c.forward(DASH_DISTANCE), DASH_TICKS, 1.2, (target, first) -> {
            if (c.damage(target, DAMAGE.value(c), DAMAGE.kind()) && c.now() >= nextContact[0]) {
                NightAudio.impact(c, target, CONTACT);
                nextContact[0] = c.now() + 3;
            }
            if (first && c.behind(target)) c.slow(target, .4f, 20);
        }, () -> {}, false, false);
    }
}
