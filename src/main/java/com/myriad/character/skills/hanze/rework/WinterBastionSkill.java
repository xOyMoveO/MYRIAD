package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class WinterBastionSkill extends AbilitySkill {
    public static final int SHIELD_TICKS=100, ROOT_TICKS=20;
    public static final double ALLY_RADIUS=6, ENEMY_RADIUS=5;

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 230, 87.5f, .7f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 90, 34.5f, .4f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("ally_radius", ALLY_RADIUS, "enemy_radius", ENEMY_RADIUS, "shield_time", SHIELD_TICKS / 20.0, "root", ROOT_TICKS / 20.0);
    }

    public WinterBastionSkill(Definition definition){super(definition);}

@Override public void release(Cast c){StaffHandCues.release(c);for(var ally:c.allies(c.player.position(),ALLY_RADIUS))c.shield(ally,SHIELD.value(c),SHIELD_TICKS);c.area(c.player.position(),ENEMY_RADIUS,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(c.player.position(),ENEMY_RADIUS))c.root(t,ROOT_TICKS,false);}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),ENEMY_RADIUS,24);}
}

