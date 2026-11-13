package com.myriad.character.skills.keheng;
import com.myriad.character.ability.AbilitySkill;
public final class KehengRework {
    private KehengRework(){}
    public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
        case 0->new EngravedEchoSkill(d);case 1->new ConstantStepSkill(d);case 2->new DelayedNeedleSkill(d);case 3->new OffbeatStepSkill(d);
        case 4->new DelayedBoundarySkill(d);case 5->new FollowingSecondSkill(d);case 6->new BeyondOneSecondSkill(d);case 7->new FinalPenetrationSkill(d);
        default->throw new IllegalArgumentException("Invalid Keheng candidate");};}
}
