package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.AbilitySkill;
public final class ChixiaoRework {
 public static final int INITIAL_HEALTH=700,HEALTH_PER_LEVEL=110;
 private ChixiaoRework(){}
 public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
 case 0->new BattleIntentSkill(d);
 case 1->new UnbrokenWillSkill(d);
 case 2->new MountainCleaveSkill(d);
 case 3->new SoulPursuitSkill(d);
 case 4->new CrimsonParrySkill(d);
 case 5->new RendingWaveSkill(d);
 case 6->new UnreturningLeapSkill(d);
 case 7->new WarBladeSkill(d);
 default->throw new IllegalArgumentException("Invalid chixiao index");};}
}

