package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.AbilitySkill;
public final class YunshengRework {
 public static final int INITIAL_HEALTH=600,HEALTH_PER_LEVEL=90;
 private YunshengRework(){}
 public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
 case 0->new SpiritEchoSkill(d);
 case 1->new SpiritChimeSkill(d);
 case 2->new PaperCraneSkill(d);
 case 3->new SpiritTetherSkill(d);
 case 4->new EchoBeaconSkill(d);
 case 5->new CloudEscortSkill(d);
 case 6->new ConvergingChorusSkill(d);
 case 7->new CovenantShelterSkill(d);
 default->throw new IllegalArgumentException("Invalid yunsheng index");};}
}

