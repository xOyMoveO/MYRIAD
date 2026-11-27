package com.myriad.character.skills.sulan.rework;

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
public final class SpiritWindMarkSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(25, 13.5f, .25f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SpiritWindMarkSkill(Definition definition){super(definition);}
    @Override public boolean canRelease(Cast c){return c.target(14,false)!=null;}
 @Override public void release(Cast c){LivingEntity t=c.target(14,false);if(t==null)return;
 WindCastCues.release(c,t.position().add(0,t.getBbHeight()*.6,0).subtract(c.player.getEyePosition()));
 var m=c.memory(t.getUUID().toString());c.damage(t,DAMAGE.value(c),DAMAGE.kind());
 m.until=c.now()+80;c.slow(t,.2f,30);
 long active=m.until;
 c.whileActive(80,a->{
     if(m.until!=active||m.until<=c.now()||!t.isAlive())return false;
     if(a%5==0&&c.player.hasLineOfSight(t))WindVisuals.mark(c,t);
     return true;
 });}
 @Override public double modifyDamage(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory(h.target().getUUID().toString());
 if(m.until>c.now()){m.until=0;WindVisuals.consumeMark(c,h.target());return 1+(5+c.rank)/100.0;}return 1;}
}
