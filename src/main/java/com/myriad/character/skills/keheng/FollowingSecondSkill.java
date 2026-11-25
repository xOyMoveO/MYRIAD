package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class FollowingSecondSkill extends KehengSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 90, 18, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public FollowingSecondSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){c.windup(5,1,()->KehengTimeFields.follow(c,50+3*(c.rank-1),.75-.025*(c.rank-1),DAMAGE.value(c)));}
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return KehengTimeFields.following(c.player);}
    @Override public void recast(AbilityRuntime.Cast c){KehengTimeFields.cancelFollowing(c.player);}
}
