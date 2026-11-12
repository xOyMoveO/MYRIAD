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
public final class InkProtectionArraySkill extends AbilitySkill {
    public InkProtectionArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,4,12);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 4;}
 @Override public void release(Cast c){Vec3 at=c.point;c.ring(at,4,false);c.later(12,false,()->{com.myriad.character.ability.AbilityVisuals.field(c,at,4,80);
 for(var ally:c.allies(at,4))c.shield(ally,c.value(60,18,.35f),40);
 c.tickFor(80,age->{if(age%4==0){c.ring(at,4,false);for(var ally:c.allies(at,4))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard12",6);
 for(var t:c.enemies(at,4))c.slow(t,.25f,8);}});});}
}
