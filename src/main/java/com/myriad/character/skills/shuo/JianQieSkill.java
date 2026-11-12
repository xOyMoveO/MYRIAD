package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class JianQieSkill extends AbilitySkill {
    public JianQieSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void onHit(AbilityRuntime.Hit hit){ShuoCombat.basicExtra(hit,this);}
}
