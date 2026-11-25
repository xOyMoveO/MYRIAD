package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class CangFengSkill extends AbilitySkill {
    static final AbilityDamageFormula BONUS = AbilityDamageFormula.physicalFromSpell(40, 12, .40f, .40f)
            .segment("skill.myriad.damage.full_edge", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(BONUS);
    }

    public CangFengSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void tickPassive(AbilityRuntime.Cast c){ShuoCombat.chargeEdge(c.player);}
    @Override public void onHit(AbilityRuntime.Hit hit){ShuoCombat.basicExtra(hit,this);}
}
