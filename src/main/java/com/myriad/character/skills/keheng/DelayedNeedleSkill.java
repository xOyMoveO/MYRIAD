package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import static com.myriad.combat.DamageCalc.Kind.MAGIC;
public final class DelayedNeedleSkill extends KehengSkill {
    public DelayedNeedleSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){
        c.windup(4,.5f,()->c.actionProjectile(12,1.2,.40,false,false,(target,back)->{
            float before=KehengTimeFields.health(target);c.damage(target,c.value(80,18,.50f),MAGIC);
            if(KehengTimeFields.health(target)<before){KehengTimeFields.needle(c,target,1-.05*(c.rank+2));KehengAudio.play(c.player,"hit");}
        }));
    }
}
