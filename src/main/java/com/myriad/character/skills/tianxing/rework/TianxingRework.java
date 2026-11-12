package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;

/** Registration only; all eight mechanics are implemented in separate files. */
public final class TianxingRework {
    public static final int INITIAL_HEALTH = 660;
    public static final int HEALTH_PER_LEVEL = 100;
    private TianxingRework(){}
    public static AbilitySkill bind(Definition d){return switch(d.index()){
        case 0->new ThunderThirdStrikeSkill(d);
        case 1->new ThunderResonanceSkill(d);
        case 2->new ThunderBlinkSkill(d);
        case 3->new ThunderArcSkill(d);
        case 4->new ThunderGuardSkill(d);
        case 5->new ThunderSlashSkill(d);
        case 6->new NineThunderSkill(d);
        case 7->new ThunderJudgmentSkill(d);
        default->throw new IllegalArgumentException("Invalid tianxing skill index");
    };}
}
