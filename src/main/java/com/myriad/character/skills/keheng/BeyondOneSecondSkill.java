package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
public final class BeyondOneSecondSkill extends KehengSkill {
    public BeyondOneSecondSkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 12;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 4;}
    @Override public void release(AbilityRuntime.Cast c){KehengTimeFields.ground(c,c.point,4,13,60,.4-.05*(c.rank-1),0,c.value(24,8,.12f));}
}
