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
    private static final SupportAmount SHIELD = new SupportAmount("shield", 60, 20.7f, .35f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public InkProtectionArraySkill(Definition definition){super(definition);}
    @Override public void present(Cast c){InkVisuals.warn(c,c.point,4,12);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 4;}
    @Override public void selectionPose(Cast c,boolean selecting){InkCastCues.select(c,selecting);}
 @Override public void release(Cast c){InkCastCues.release(c);Vec3 at=c.point;c.later(12,false,()->{InkCastCues.activate(c);com.myriad.character.ability.AbilityVisuals.field(c,at,4,80);
 for(var ally:c.allies(at,4))c.shield(ally,SHIELD.value(c),40);
 c.tickFor(80,age->{if(age%4==0){for(var ally:c.allies(at,4))com.myriad.player.PlayerDataProvider.get(ally).setBuff("g1_guard12",6);
 for(var t:c.enemies(at,4))c.slow(t,.25f,8);}});});}
}
