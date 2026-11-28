package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class FollowingSecondSkill extends KehengSkill {
    public static final int WINDUP_TICKS = 5;
    public static int durationTicks(int rank) { return 50 + 3 * (rank - 1); }
    public static double actionRate(int rank) { return .75 - .025 * (rank - 1); }
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 90, 22.5f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public FollowingSecondSkill(Definition d){super(d);}
    @Override public void windupPose(AbilityRuntime.Cast c, boolean active) {
        WeaponPosePayload.windup(c, Move.FOLLOW_READY, 5, active);
    }
    @Override public void release(AbilityRuntime.Cast c){c.windup(WINDUP_TICKS,1,()->KehengTimeFields.follow(c,durationTicks(c.rank),actionRate(c.rank),DAMAGE.value(c)));}
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return KehengTimeFields.following(c.player);}
    @Override public void recast(AbilityRuntime.Cast c){KehengTimeFields.cancelFollowing(c.player);}
}
