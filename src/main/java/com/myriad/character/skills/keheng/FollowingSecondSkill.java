package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
public final class FollowingSecondSkill extends KehengSkill {
    public FollowingSecondSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){c.windup(5,1,()->KehengTimeFields.follow(c,50+3*(c.rank-1),.75-.025*(c.rank-1),c.value(90,18,.5f)));}
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return KehengTimeFields.following(c.player);}
    @Override public void recast(AbilityRuntime.Cast c){KehengTimeFields.cancelFollowing(c.player);}
}
