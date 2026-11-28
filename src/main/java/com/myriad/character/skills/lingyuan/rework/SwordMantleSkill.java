package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordMantleSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 100, 37.5f, .55f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public SwordMantleSkill(Definition definition){super(definition);}

@Override public void release(Cast c){LingyuanHandCues.release(c);c.shield(c.player,SHIELD.value(c),80);c.memory().until=c.now()+40;SwordVisuals.mantle(c);}
@Override public double incomingMultiplier(Cast c){return c.memory().until>c.now()?.85:1;}
}

