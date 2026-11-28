package com.myriad.character.skills.fenjue.rework;

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
public final class FlameArmorSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 180, 87.5f, .8f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(30, 13.8f, .2f, .8f / 4).segment("skill.myriad.damage.segment.shield_pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public FlameArmorSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.seal(c,c.player.position(),3,false);}
    @Override public void release(Cast c){FlameMajorAudio.armor(c);FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.ARMOR,18);
 c.shield(c.player,SHIELD.value(c),100);c.buff(getId(),100);
 c.repeat(100,20,i->{if(c.ownShield(c.player))c.pulse(c.player.position(),3,DAMAGE.value(c),DAMAGE.kind());});}
}
