package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.AbilitySkill;
public final class QingluoRework {
 public static final int INITIAL_HEALTH=580,HEALTH_PER_LEVEL=86;
 private QingluoRework(){}
 public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
 case 0->new KindHerbalistSkill(d);
 case 1->new HerbalEssenceSkill(d);
 case 2->new VerdantDewSkill(d);
 case 3->new BriarSeedSkill(d);
 case 4->new HeartPillArraySkill(d);
 case 5->new LifeThreadSkill(d);
 case 6->new SpringGroveSkill(d);
 case 7->new JadeCanopySkill(d);
 default->throw new IllegalArgumentException("Invalid qingluo index");};}
}

