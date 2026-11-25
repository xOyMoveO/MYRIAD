package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
public final class DelayedNeedleSkill extends KehengSkill {
    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 80, 18, .50f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public DelayedNeedleSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){
        c.windup(4,.5f,()->c.actionProjectile(12,1.2,.40,false,false,(target,back)->{
            float before=KehengTimeFields.health(target);c.damage(target,DAMAGE.value(c),DamageCalc.Kind.MAGIC);
            if(KehengTimeFields.health(target)<before){KehengTimeFields.needle(c,target,1-.05*(c.rank+2));KehengAudio.play(c.player,"hit");}
        }));
    }
}
