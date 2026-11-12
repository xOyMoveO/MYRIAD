package com.myriad.character.skills.tianxing.rework;

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
public final class ThunderGuardSkill extends AbilitySkill {
    public ThunderGuardSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){c.whileActive(80,age->{if(c.data.getBuff(getId())<=0)return false;
        if(age%6==0)com.myriad.character.ability.AbilityVisuals.following(c,com.myriad.character.ability.AbilityVfxGeometry.SHIELD,c.player,.65,8);return true;});}
    @Override public void release(Cast c){ThunderSkillPresentation.play(c,com.myriad.network.ThunderSkillViewPayload.GUARD,14);c.buff("g1_guard15",80);c.buff(getId(),80);
 ThunderAudio.play(c,ThunderAudio.Cue.GUARD);
 c.repeat(80,20,i->{ThunderAudio.play(c,ThunderAudio.Cue.PULSE);c.pulse(c.player.position(),4,c.value(20,8,.15f),MAGIC);});}
}
