package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordResonanceSkill extends AbilitySkill {
    public static final int CASTS_TO_CHARGE=3, SHIELD_TICKS=60;

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 45, 18, .25f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 14.4f, .35f).segment("skill.myriad.damage.segment.bonus_basic", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("casts", CASTS_TO_CHARGE, "shield_time", SHIELD_TICKS / 20.0);
    }

    public SwordResonanceSkill(Definition definition){super(definition);}

@Override public void onCast(Cast c){var own=AbilityRuntime.context(c.player,this);own.memory().count=Math.min(CASTS_TO_CHARGE,own.memory().count+1);own.buff(getId()+"_ready",own.memory().count>=CASTS_TO_CHARGE?200:0);}
@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.count<CASTS_TO_CHARGE)return;m.count=0;SwordVisuals.clearResonance(c);c.buff(getId()+"_ready",0);c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);AbilityVisuals.burst(c,h.target().position(),.8);}
@Override public void release(Cast c){}
@Override public void tickPassive(Cast c){c.buff(getId()+"_ready",c.memory().count>=CASTS_TO_CHARGE?20:0);SwordVisuals.resonance(c,c.memory().count);}
}

