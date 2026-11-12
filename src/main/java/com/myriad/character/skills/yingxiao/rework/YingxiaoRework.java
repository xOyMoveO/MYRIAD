package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;

/** Registration only; all eight mechanics are implemented in separate files. */
public final class YingxiaoRework {
    public static final int INITIAL_HEALTH = 560;
    public static final int HEALTH_PER_LEVEL = 85;
    private YingxiaoRework(){}
    public static AbilitySkill bind(Definition d){return switch(d.index()){
        case 0->new NightBackstabSkill(d);
        case 1->new NightAmbushSkill(d);
        case 2->new ShadowDaggerSkill(d);
        case 3->new ShadowVanishSkill(d);
        case 4->new ShadowAssaultSkill(d);
        case 5->new ShadowReturnSkill(d);
        case 6->new ShadowExecutionSkill(d);
        case 7->new ShadowShieldSkill(d);
        default->throw new IllegalArgumentException("Invalid yingxiao skill index");
    };}
}
