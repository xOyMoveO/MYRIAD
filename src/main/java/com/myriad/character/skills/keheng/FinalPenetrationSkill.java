package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class FinalPenetrationSkill extends KehengSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 330, 100, 1.35f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public FinalPenetrationSkill(Definition d){super(d);}
    @Override public void windupPose(AbilityRuntime.Cast c, boolean active) {
        WeaponPosePayload.windup(c, Move.LINE_READY, 16, active);
    }
    @Override public void release(AbilityRuntime.Cast c){
        var field=KehengTimeFields.corridor(c,c.player.position(),c.forward(1));
        c.windup(16,0,()->{KehengTimeFields.penetrate(field,DAMAGE.value(c));KehengTimeFields.remove(field);});
    }
}
