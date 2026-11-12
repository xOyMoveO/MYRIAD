package com.myriad.character.skills.lingyuan.rework;
import com.myriad.character.ability.AbilitySkill;
/** Registration only; each skill is independently reusable. */
public final class LingyuanRework {
 public static final int INITIAL_HEALTH=650,HEALTH_PER_LEVEL=100;
 private LingyuanRework(){}
 public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
 case 0->new SwordHeartSkill(d);
 case 1->new SwordResonanceSkill(d);
 case 2->new CloudBreakSkill(d);
 case 3->new SwordMantleSkill(d);
 case 4->new SwordVolleySkill(d);
 case 5->new SwordArraySkill(d);
 case 6->new ThousandSwordsSkill(d);
 case 7->new HeavenSwordSkill(d);
 default->throw new IllegalArgumentException("Invalid lingyuan skill index");};}
}

