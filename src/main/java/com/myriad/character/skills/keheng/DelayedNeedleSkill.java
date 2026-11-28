package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class DelayedNeedleSkill extends KehengSkill {
    public static final int WINDUP_TICKS = 4;
    public static final double RANGE = 12;
    public static double actionRate(int rank) { return 1 - .05 * (rank + 2); }
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 80, 24.3f, .50f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public DelayedNeedleSkill(Definition d){super(d);}
    @Override public void windupPose(AbilityRuntime.Cast c, boolean active) {
        WeaponPosePayload.windup(c, Move.NEEDLE_READY, 4, active);
    }
    @Override public void release(AbilityRuntime.Cast c){
        c.windup(WINDUP_TICKS,.5f,()->{WeaponPosePayload.play(c.player, Move.NEEDLE, 6);
            c.actionProjectile(RANGE,1.2,.40,false,false,(target,back)->{
            float before=KehengTimeFields.health(target);c.damage(target,DAMAGE.value(c),DamageCalc.Kind.MAGIC);
            if(KehengTimeFields.health(target)<before){KehengTimeFields.needle(c,target,actionRate(c.rank));KehengAudio.play(c.player,"hit");}
        });});
    }
}
