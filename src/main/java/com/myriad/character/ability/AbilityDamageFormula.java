package com.myriad.character.ability;

import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.combat.DamageCalc.Kind;

/** One repeatable damage segment shared by the authoritative skill and its preview. */
public record AbilityDamageFormula(Kind kind, float base, float rankGrowth, float scale,
        float scaleGrowth, Scaling scaling, float weight, String labelKey, int maxHits) {

    public enum Scaling { FIXED, BONUS_PHYSICAL, PURCHASED_SPELL, TOTAL_PHYSICAL, TOTAL_SPELL }

    private static AbilityDamageFormula of(Kind kind, float base, float growth, float scale, Scaling scaling) {
        return new AbilityDamageFormula(kind, base, growth, scale, 0, scaling, 1, "", 1);
    }

    public static AbilityDamageFormula fixed(Kind kind, float base, float growth) {
        return of(kind, base, growth, 0, Scaling.FIXED);
    }

    public static AbilityDamageFormula physicalAttack(Kind kind, float base, float growth, float scale) {
        return of(kind, base, growth, scale, Scaling.TOTAL_PHYSICAL);
    }

    public static AbilityDamageFormula spellPower(Kind kind, float base, float growth, float scale) {
        return of(kind, base, growth, scale, Scaling.TOTAL_SPELL);
    }

    public static AbilityDamageFormula bonusPhysical(float base, float growth, float scale) {
        return of(Kind.PHYSICAL, base, growth, scale, Scaling.BONUS_PHYSICAL);
    }

    /** Retain the old unbuffed, zero-purchase contribution before applying bonus physical attack. */
    public static AbilityDamageFormula physicalFromSpell(float base, float growth, float oldSpellScale, float attackScale) {
        float baseline = base + oldSpellScale * (float) FirstGroupGrowth.baselineSpellPower();
        return bonusPhysical(baseline, growth, attackScale);
    }

    public AbilityDamageFormula withScaleGrowth(float perRank) {
        return new AbilityDamageFormula(kind, base, rankGrowth, scale, perRank, scaling, weight, labelKey, maxHits);
    }

    public AbilityDamageFormula scaled(float multiplier) {
        return new AbilityDamageFormula(kind, base, rankGrowth, scale, scaleGrowth, scaling, weight * multiplier,
                labelKey, maxHits);
    }

    /** The label describes the segment and the hit count is for one target in the described sequence. */
    public AbilityDamageFormula segment(String key, int hits) {
        return new AbilityDamageFormula(kind, base, rankGrowth, scale, scaleGrowth, scaling, weight, key, hits);
    }

    public float value(AbilityRuntime.Cast cast) {
        float attribute = switch (scaling) {
            case FIXED -> 0;
            case BONUS_PHYSICAL -> cast.bonusPhysicalAttack;
            case PURCHASED_SPELL -> cast.purchasedSpellPower;
            case TOTAL_PHYSICAL -> cast.totalPhysicalAttack;
            case TOTAL_SPELL -> cast.totalSpellPower;
        };
        return value(cast.rank, attribute);
    }

    public float value(int rank, double totalPhysicalAttack, double totalSpellPower, double bonusPhysicalAttack, int purchasedWisdom) {
        float attribute = switch (scaling) {
            case FIXED -> 0;
            case BONUS_PHYSICAL -> (float) bonusPhysicalAttack;
            case PURCHASED_SPELL -> (float) FirstGroupGrowth.purchasedSpellPower(purchasedWisdom);
            case TOTAL_PHYSICAL -> (float) totalPhysicalAttack;
            case TOTAL_SPELL -> (float) totalSpellPower;
        };
        return value(rank, attribute);
    }

    public float perPoint(int rank) {
        float attribute = switch (scaling) {
            case FIXED -> 0;
            case BONUS_PHYSICAL, TOTAL_PHYSICAL -> (float) FirstGroupGrowth.purchasedPhysicalAttack(1);
            case PURCHASED_SPELL, TOTAL_SPELL -> (float) FirstGroupGrowth.purchasedSpellPower(1);
        };
        return weight * scaleAt(rank) * attribute;
    }

    private float scaleAt(int rank) {
        return scale + scaleGrowth * (rank - 1);
    }

    private float value(int rank, float attribute) {
        return (base + rankGrowth * (rank - 1) + scaleAt(rank) * attribute) * weight;
    }
}
