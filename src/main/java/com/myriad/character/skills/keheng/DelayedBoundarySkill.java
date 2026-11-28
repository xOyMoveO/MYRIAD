package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class DelayedBoundarySkill extends KehengSkill {
    public static final double RANGE = 10, RADIUS = 3.5;
    public static final int WARNING_TICKS = 9;
    public static int durationTicks(int rank) { return 60 + 5 * (rank - 1); }
    public static double actionRate(int rank) { return .65 - .025 * (rank - 1); }
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 80, 22.5f, .45f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public DelayedBoundarySkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return RANGE;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return RADIUS;}
    @Override public void release(AbilityRuntime.Cast c){WeaponPosePayload.play(c.player, Move.BOUNDARY, 8);
        KehengTimeFields.ground(c,c.point,RADIUS,WARNING_TICKS,durationTicks(c.rank),actionRate(c.rank),DAMAGE.value(c),0);}
}
