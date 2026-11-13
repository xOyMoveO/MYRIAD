package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class NaDaoSkill extends AbilitySkill {
    public NaDaoSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        boolean exposed=ShuoCombat.drawn(c.player);
        ShuoCombat.motion(c.player,8);
        if(exposed){
            var origin=c.player.position();var facing=c.forward(1);float total=c.value(55,14,.4f);
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,6);
            ShuoVisuals.slash(c,3,90,1);
            ShuoCombat.cone(c,3,90,total*.6f,0);
            // The after-cut stays where the retreat began; moving out is real counterplay.
            c.actionWhile(7,age->{
                if(AbilityRuntime.controlled(c.player)){ShuoVisuals.cancel(c.player,index());return false;}
                if(age<7)return true;
                ShuoVisuals.slashAt(c,origin,facing,3,90,0);
                ShuoCombat.coneAt(c,origin,facing,3,90,total*.4f,0,.3f,20);return false;
            });
        }
        var forward=c.forward(1);var side=new net.minecraft.world.phys.Vec3(forward.z,0,-forward.x);
        var delta=forward.scale(-.9).add(side.scale(.4358899)).normalize().scale(3);
        c.actionDash(delta,6,.2,(t,first)->{},()->{if(exposed)ShuoCombat.sheathe(c.player);else if(!AbilityRuntime.controlled(c.player))c.haste(.20f,30);},false,false);
        ShuoVisuals.trackMotion(c,exposed);
    }
}
