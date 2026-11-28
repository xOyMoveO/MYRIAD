package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class CovenantShelterSkill extends AbilitySkill {
    public static final double ALLY_RANGE = 8;
    public static final int SHIELD_TICKS = 100;
    public static final float HASTE = .25f;
    public static final int HASTE_TICKS = 60;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "allyRange", ALLY_RANGE,
            "shieldSeconds", SHIELD_TICKS / 20.0,
            "hastePercent", Math.round(100 * HASTE),
            "hasteSeconds", HASTE_TICKS / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 210, 81.25f, .65f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public CovenantShelterSkill(Definition definition){super(definition);}

@Override public void release(Cast c){for(var ally:c.allies(c.player.position(),ALLY_RANGE)){c.shield(ally,SHIELD.value(c),SHIELD_TICKS);c.slow(ally,-HASTE,HASTE_TICKS);}}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.field(c,c.player.position(),ALLY_RANGE,24);}
}

