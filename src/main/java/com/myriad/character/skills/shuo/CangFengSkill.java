package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class CangFengSkill extends AbilitySkill {
    public CangFengSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void tickPassive(AbilityRuntime.Cast c){ShuoCombat.chargeEdge(c.player);}
    @Override public void onHit(AbilityRuntime.Hit hit){ShuoCombat.basicExtra(hit,this);}
}
