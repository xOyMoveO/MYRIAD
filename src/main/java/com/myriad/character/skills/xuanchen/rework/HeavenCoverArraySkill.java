package com.myriad.character.skills.xuanchen.rework;

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
public final class HeavenCoverArraySkill extends AbilitySkill {
    public HeavenCoverArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.player.position(),6,16);}
    @Override public void release(Cast c){c.later(16,true,()->{Vec3 at=c.player.position();com.myriad.character.ability.AbilityVisuals.field(c,at,6,80);
 c.tickFor(80,age->{if(age%4==0){c.ring(at,6,false);for(var ally:c.allies(at,6))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard15",6);
 for(var t:c.enemies(at,6))c.slow(t,.3f,8);}});
 c.repeat(80,20,i->c.pulse(at,6,c.value(45,20,.3f),MAGIC));});}
}
