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
public final class SealResonanceSkill extends AbilitySkill {
    public static final int MARK_TICKS = 80;
    public static final int INTERNAL_COOLDOWN = 160;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "markSeconds", MARK_TICKS / 20.0,
            "procSeconds", INTERNAL_COOLDOWN / 20.0);
    }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 14.4f, .3f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SealResonanceSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){if(h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target()))return;
 var m=c.memory(h.target().getUUID().toString());if(m.cooldown>c.now())return;
 if(m.until>c.now()&&!m.tag.equals(h.skillId())){m.until=0;m.cooldown=c.now()+INTERNAL_COOLDOWN;com.myriad.character.ability.AbilityVisuals.burst(c,h.target().position(),.75);c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);}
 else if(m.until<=c.now()){m.until=c.now()+MARK_TICKS;m.tag=h.skillId();
     var target=h.target();
     c.whileActive(MARK_TICKS,age->{
         if(m.until<=c.now()||!target.isAlive())return false;
         if(age%5==0)InkVisuals.halfSeal(c,target);
         return true;
     });}}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
