package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
public final class OffbeatStepSkill extends KehengSkill {
    public OffbeatStepSkill(Definition d){super(d);}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        KehengTimeFields.ground(c,c.player.position(),2,0,30,.8,0,0);
        c.shield(c.player,c.value(35,10,.15f),40);KehengVisuals.watchShield(c,40);
        // The normal movement packet already carries the horizontal input direction; standing falls back to aim.
        var input=c.player.getLastClientMoveIntent().multiply(1,0,1);
        var direction=input.lengthSqr()>.001?input.normalize():c.forward(1);
        c.actionDash(direction.scale(4.5),5,0,(t,first)->{},()->{},false,false);
    }
}
