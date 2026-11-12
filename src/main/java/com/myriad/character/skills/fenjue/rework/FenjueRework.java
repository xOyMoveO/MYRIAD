package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;

/** Registration only; all eight mechanics are implemented in separate files. */
public final class FenjueRework {
    public static final int INITIAL_HEALTH = 700;
    public static final int HEALTH_PER_LEVEL = 105;
    private FenjueRework(){}
    public static AbilitySkill bind(Definition d){return switch(d.index()){
        case 0->new EmberBurnSkill(d);
        case 1->new BurningLastStandSkill(d);
        case 2->new FlameSlashSkill(d);
        case 3->new FlameStepSkill(d);
        case 4->new LavaCageSkill(d);
        case 5->new FlameBurstSkill(d);
        case 6->new HeavenFireSeaSkill(d);
        case 7->new FlameArmorSkill(d);
        default->throw new IllegalArgumentException("Invalid fenjue skill index");
    };}
}
