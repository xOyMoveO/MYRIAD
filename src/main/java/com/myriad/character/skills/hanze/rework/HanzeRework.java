package com.myriad.character.skills.hanze.rework;
import com.myriad.character.ability.AbilitySkill;
/** Registration only; each skill is independently reusable. */
public final class HanzeRework {
 public static final int INITIAL_HEALTH=740,HEALTH_PER_LEVEL=115;
 private HanzeRework(){}
 public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
 case 0->new FrostStacksSkill(d);
 case 1->new RimeSkinSkill(d);
 case 2->new IceVolleySkill(d);
 case 3->new FrostShieldSkill(d);
 case 4->new AbsoluteZeroSkill(d);
 case 5->new GlacierLanceSkill(d);
 case 6->new FrozenExpanseSkill(d);
 case 7->new WinterBastionSkill(d);
 default->throw new IllegalArgumentException("Invalid hanze skill index");};}
}

