package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class DelayedBoundarySkill extends KehengSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 80, 18, .45f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public DelayedBoundarySkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 10;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 3.5;}
    @Override public void release(AbilityRuntime.Cast c){WeaponPosePayload.play(c.player, Move.BOUNDARY, 8);
        KehengTimeFields.ground(c,c.point,3.5,9,60+5*(c.rank-1),.65-.025*(c.rank-1),DAMAGE.value(c),0);}
}
