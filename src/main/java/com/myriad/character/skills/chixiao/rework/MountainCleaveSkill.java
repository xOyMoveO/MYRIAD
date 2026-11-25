package com.myriad.character.skills.chixiao.rework;

import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.WarSpiritHandCues;
import com.myriad.character.rework.FirstGroupGrowth;
import net.minecraft.network.chat.Component;

import static com.myriad.combat.DamageCalc.Kind.PHYSICAL;

/** Independent reusable branch. */
public final class MountainCleaveSkill extends AbilitySkill {
    private static final int BASE_DAMAGE = 93;
    private static final int DAMAGE_PER_RANK = 22;
    private static final double PURCHASED_ATTACK_RATIO = .50;

    public MountainCleaveSkill(Definition definition) {
        super(definition);
    }

    private static double damage(int rank, int purchasedStrength) {
        return BASE_DAMAGE + DAMAGE_PER_RANK * (rank - 1)
                + PURCHASED_ATTACK_RATIO * FirstGroupGrowth.purchasedPhysicalAttack(purchasedStrength);
    }

    @Override
    public String evaluatedDescription(int rank, double skillPower, int purchasedStrength) {
        var currentDamage = AbilityFormulaText.number(damage(Math.clamp(rank, 1, maxRank()), purchasedStrength));
        var perStrength = AbilityFormulaText.number(PURCHASED_ATTACK_RATIO * FirstGroupGrowth.purchasedPhysicalAttack(1));
        return description() + Component.translatableWithFallback("skill.myriad.g2_chixiao_n1.current_damage",
                "当前造成 %s 物理伤害。技能每升一级增加 %s 伤害，每点已购力量增加 %s 伤害。",
                currentDamage, DAMAGE_PER_RANK, perStrength).getString();
    }

    @Override
    public void release(Cast cast) {
        WarSpiritHandCues.cast(cast);
        cast.cone(6, 100, (float)damage(cast.rank, cast.data.firstGroupAttribute("strength")), PHYSICAL);
    }
}

