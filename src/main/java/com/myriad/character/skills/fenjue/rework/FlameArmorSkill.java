package com.myriad.character.skills.fenjue.rework;

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
public final class FlameArmorSkill extends AbilitySkill {
    public FlameArmorSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.seal(c,c.player.position(),3,false);}
    @Override public void release(Cast c){FlameMajorAudio.armor(c);FlameSkillPresentation.play(c,com.myriad.network.FlameSkillViewPayload.ARMOR,18);
 c.shield(c.player,c.value(180,70,.8f),100);c.buff(getId(),100);
 c.repeat(100,20,i->{if(c.ownShield(c.player))c.pulse(c.player.position(),3,c.value(30,12,.2f),PHYSICAL);});}
}
