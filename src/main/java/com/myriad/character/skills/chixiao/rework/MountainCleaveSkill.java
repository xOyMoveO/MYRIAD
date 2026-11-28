package com.myriad.character.skills.chixiao.rework;

import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.WarSpiritHandCues;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Independent reusable branch. */
public final class MountainCleaveSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.bonusPhysical(98, 28.0125f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }


    public MountainCleaveSkill(Definition definition) {
        super(definition);
    }

    @Override
    public String evaluatedDescription(int rank, double skillPower, double bonusPhysicalAttack) {
        var currentDamage = AbilityFormulaText.number(DAMAGE.value(Math.clamp(rank, 1, maxRank()), 0, skillPower, bonusPhysicalAttack, 0));
        var perStrength = AbilityFormulaText.number(DAMAGE.perPoint(rank));
        return description() + Component.translatableWithFallback("skill.myriad.g2_chixiao_n1.current_damage",
                "当前造成 %s 物理伤害。技能每升一级增加 %s 伤害，每点力量增加 %s 伤害，临时物攻也计入。",
                currentDamage, AbilityFormulaText.number(DAMAGE.rankGrowth()), perStrength).getString();
    }

    @Override
    public void release(Cast cast) {
        WarSpiritHandCues.cast(cast);
        cast.cone(6, 100, DAMAGE.value(cast), DAMAGE.kind());
    }
}

