package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrostShieldSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 150, 50, .65f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public FrostShieldSkill(Definition definition){super(definition);}

@Override public void release(Cast c){StaffHandCues.release(c);c.shield(c.player,SHIELD.value(c),80);for(var t:c.enemies(c.player.position(),3))c.slow(t,.25f,30);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),3,false);}
}

