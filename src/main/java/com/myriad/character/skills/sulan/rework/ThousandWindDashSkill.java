package com.myriad.character.skills.sulan.rework;

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
public final class ThousandWindDashSkill extends AbilitySkill {
    public ThousandWindDashSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.charging(c,10,12,.35);}
    @Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0;}
 @Override public void release(Cast c){c.windup(10,0,()->c.actionDash(c.forward(12),14,1.2,(t,first)->{},
 ()->{Vec3 at=c.player.position();c.area(at,2.5,c.value(160,65,1.2f),PHYSICAL);for(var t:c.enemies(at,2.5))c.slow(t,.3f,30);},true,true));}
}
