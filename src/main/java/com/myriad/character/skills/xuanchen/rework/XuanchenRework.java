package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;

/** Registration only; all eight mechanics are implemented in separate files. */
public final class XuanchenRework {
    public static final int INITIAL_HEALTH = 600;
    public static final int HEALTH_PER_LEVEL = 85;
    private XuanchenRework(){}
    public static AbilitySkill bind(Definition d){return switch(d.index()){
        case 0->new ArrayGuardianSkill(d);
        case 1->new SealResonanceSkill(d);
        case 2->new SpiritBindSkill(d);
        case 3->new InkShieldSkill(d);
        case 4->new PairedPortalSkill(d);
        case 5->new InkProtectionArraySkill(d);
        case 6->new HeavenCoverArraySkill(d);
        case 7->new SealedBoundarySkill(d);
        default->throw new IllegalArgumentException("Invalid xuanchen skill index");
    };}
}
