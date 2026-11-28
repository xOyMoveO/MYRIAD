package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class BeyondOneSecondSkill extends KehengSkill {
    public static final double RANGE = 12, RADIUS = 5;
    public static final int WARNING_TICKS = 13, DURATION_TICKS = 60;
    public static double flightRate(int rank) { return .4 - .05 * (rank - 1); }
    public static double actionRate(int rank) { return flightRate(rank) - .05; }
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 24, 9.2f, .12f)
            .segment("skill.myriad.damage.per_pulse", 6);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public BeyondOneSecondSkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return RANGE;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return RADIUS;}
    @Override public void release(AbilityRuntime.Cast c){WeaponPosePayload.play(c.player, Move.DOME, 12);
        KehengTimeFields.ground(c,c.point,RADIUS,WARNING_TICKS,DURATION_TICKS,actionRate(c.rank),flightRate(c.rank),0,DAMAGE.value(c));}
}
