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
public final class SpiritWindMarkSkill extends AbilitySkill {
    public SpiritWindMarkSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){var target=c.target(14,false);if(target!=null){var m=c.memory(target.getUUID().toString());c.whileActive(80,a->{if(m.until<=c.now()||!target.isAlive())return false;if(a%8==0&&c.player.hasLineOfSight(target))com.myriad.character.ability.AbilityVisuals.following(c,com.myriad.character.ability.AbilityVfxGeometry.SEAL,target,.7,8);return true;});}}
    @Override public boolean canRelease(Cast c){return c.target(14,false)!=null;}
 @Override public void release(Cast c){LivingEntity t=c.target(14,false);if(t==null)return;var m=c.memory(t.getUUID().toString());c.damage(t,c.value(25,10,.25f),PHYSICAL);
 m.until=c.now()+80;c.slow(t,.2f,30);c.ring(t.position(),1,false);}
 @Override public double modifyDamage(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory(h.target().getUUID().toString());
 if(m.until>c.now()){m.until=0;return 1+(5+c.rank)/100.0;}return 1;}
}
