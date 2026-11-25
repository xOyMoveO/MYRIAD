package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class JianQieSkill extends AbilitySkill {
    static final AbilityDamageFormula BONUS = AbilityDamageFormula.physicalFromSpell(55, 15, .50f, .50f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(BONUS);
    }

    public JianQieSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void onHit(AbilityRuntime.Hit hit){ShuoCombat.basicExtra(hit,this);}
}
