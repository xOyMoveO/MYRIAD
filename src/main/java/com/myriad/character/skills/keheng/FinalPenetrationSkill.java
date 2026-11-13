package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
public final class FinalPenetrationSkill extends KehengSkill {
    public FinalPenetrationSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){
        var field=KehengTimeFields.corridor(c,c.player.position(),c.forward(1));
        c.windup(16,0,()->{KehengTimeFields.penetrate(field,c.value(330,100,1.35f));KehengTimeFields.remove(field);});
    }
}
