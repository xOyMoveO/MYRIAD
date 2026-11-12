package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;

/** Registration only; all eight mechanics are implemented in separate files. */
public final class SulanRework {
    public static final int INITIAL_HEALTH = 620;
    public static final int HEALTH_PER_LEVEL = 95;
    private SulanRework(){}
    public static AbilitySkill bind(Definition d){return switch(d.index()){
        case 0->new WindWalkingBladeSkill(d);
        case 1->new WindComboBladeSkill(d);
        case 2->new GaleThrustSkill(d);
        case 3->new SpiritWindMarkSkill(d);
        case 4->new WindBarrierSkill(d);
        case 5->new ReturningWindBladeSkill(d);
        case 6->new ThousandWindDashSkill(d);
        case 7->new WindDomainSkill(d);
        default->throw new IllegalArgumentException("Invalid sulan skill index");
    };}
}
