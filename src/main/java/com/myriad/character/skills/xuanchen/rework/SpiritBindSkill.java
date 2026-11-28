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
public final class SpiritBindSkill extends AbilitySkill {
    public static final double RANGE = 16;
    public static final double SPEED = 1.25;
    public static final double PADDING = .9;
    public static final int ROOT_BASE = 12;
    public static final int ROOT_GROWTH = 2;

    public static int rootTicks(int rank) { return ROOT_BASE + ROOT_GROWTH * (rank - 1); }
    public static double speedBlocksPerTick() { return SPEED * com.myriad.combat.HeroProjectileTuning.SPEED; }
    public static double entityPaddingBlocks() { return PADDING * com.myriad.combat.HeroProjectileTuning.ENTITY_PADDING; }

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "projectileRange", RANGE,
            "projectileSpeed", speedBlocksPerTick() * 20,
            "projectilePadding", entityPaddingBlocks(),
            "rootSeconds", rootTicks(rank) / 20.0);
    }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 18.75f, .55f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SpiritBindSkill(Definition definition){super(definition);}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.seal(c,c.player.position(),.6,false);}
    @Override public void release(Cast c){InkCastCues.release(c);c.actionProjectile(RANGE,SPEED,PADDING,false,false,(t,back)->{
        c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.root(t,rootTicks(c.rank),false);
        InkVisuals.binding(c,t,10);
    });}
}
