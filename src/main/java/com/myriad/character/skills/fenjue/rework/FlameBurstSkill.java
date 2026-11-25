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
public final class FlameBurstSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(110, 25, 1.1f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public static final double RADIUS=6;
    public FlameBurstSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){
        c.whileActive(13,age->{if(!AbilityRuntime.channeling(c.player))return false;
            if(age%3==0){com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.CHARGE,
                    c.player.position().add(0,.04,0),c.player.position().add(c.forward(1)),RADIUS,0,4,false);
                com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.SEAL,
                    c.player.position().add(0,.055,0),c.player.position().add(c.forward(1)),RADIUS,359,4,false);}return true;});
    }
    @Override public void release(Cast c){FlameMajorAudio.burstCharge(c);FlameSkillPresentation.mobileBurst(c,()->{FlameMajorAudio.burstRelease(c);double lost=1-c.player.getHealth()/c.player.getMaxHealth();double bonus=Math.min(.16,Math.floor(lost*10)*.02);
 c.area(c.player.position(),RADIUS,(float)(DAMAGE.value(c)*(1+bonus)),DAMAGE.kind());});}
}
