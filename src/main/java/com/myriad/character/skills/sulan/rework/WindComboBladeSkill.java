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
public final class WindComboBladeSkill extends AbilitySkill {
    public static final int READY_TICKS = 60, COOLDOWN_TICKS = 120, SLOW_TICKS = 20;
    public static final float SLOW_RATE = .15f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(25, 12, .3f, .25f).segment("skill.myriad.damage.segment.bonus_basic", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public WindComboBladeSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();
 if(!h.basic()&&c.hero(h.target())&&m.cooldown<=c.now()){m.until=c.now()+READY_TICKS;m.cooldown=c.now()+COOLDOWN_TICKS;c.buff(getId(),READY_TICKS);c.buff("sulan_passive_ready",1);}
 else if(h.basic()&&m.until>c.now()){m.until=0;WindVisuals.cut(c,h.target(),.8,120);c.buff(getId(),0);c.buff("sulan_passive_ready",0);c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);c.slow(h.target(),SLOW_RATE,SLOW_TICKS);}}
    @Override public void tickPassive(Cast c){if(c.memory().until<=c.now())c.buff("sulan_passive_ready",0);else com.myriad.character.ability.AbilityVisuals.ready(c);}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
