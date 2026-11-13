package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
public final class DelayedBoundarySkill extends KehengSkill {
    public DelayedBoundarySkill(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 10;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 3.5;}
    @Override public void release(AbilityRuntime.Cast c){KehengTimeFields.ground(c,c.point,3.5,9,60+5*(c.rank-1),.65-.025*(c.rank-1),c.value(80,18,.45f),0);}
}
