package com.myriad.character.skills.shuo;
import com.myriad.character.ability.AbilitySkill;
public final class ShuoRework {
    public static final int INITIAL_HEALTH=640,HEALTH_PER_LEVEL=100;
    private ShuoRework(){}
    public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
        case 0->new CangFengSkill(d);case 1->new JianQieSkill(d);case 2->new NaDaoSkill(d);case 3->new QiaoFanSkill(d);
        case 4->new YiShanSkill(d);case 5->new DuanLiuSkill(d);case 6->new WuShengSkill(d);case 7->new CanYueSkill(d);
        default->throw new IllegalArgumentException("Invalid Shuo branch");};}
}
