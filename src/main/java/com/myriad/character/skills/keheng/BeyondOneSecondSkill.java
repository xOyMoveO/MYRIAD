package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class BeyondOneSecondSkill extends KehengSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 24, 9.2f, .12f)
            .segment("skill.myriad.damage.per_pulse", 6);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public BeyondOneSecondSkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 12;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 4;}
    @Override public void release(AbilityRuntime.Cast c){WeaponPosePayload.play(c.player, Move.DOME, 12);
        KehengTimeFields.ground(c,c.point,4,13,60,.4-.05*(c.rank-1),0,DAMAGE.value(c));}
}
