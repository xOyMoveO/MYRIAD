package com.myriad.character.skills.xuanchen.rework;

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
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class HeavenCoverArraySkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 23, .3f).segment("skill.myriad.damage.segment.pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public HeavenCoverArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.player.position(),6,16);}
    @Override public void windupPose(Cast c,boolean winding){InkCastCues.charge(c,winding);}
    @Override public void release(Cast c){c.windup(16,0,()->{InkCastCues.release(c);Vec3 at=c.player.position();com.myriad.character.ability.AbilityVisuals.field(c,at,6,80);
 c.tickFor(80,age->{if(age%4==0){for(var ally:c.allies(at,6))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard15",6);
 for(var t:c.enemies(at,6))c.slow(t,.3f,8);}});
 c.repeat(80,20,i->{
     for(var target:c.enemies(at,6))c.damage(target,DAMAGE.value(c),DAMAGE.kind(),false);
     InkVisuals.arrayPulse(c,at,(i-1)*90);
 });});}
}
