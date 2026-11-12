package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class DuanLiuSkill extends AbilitySkill {
    public DuanLiuSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_LEFT,5);
            ShuoVisuals.slash(c,3.5,100,0);
            var first=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),3.5,100,c.value(45,10,.35f),0,0,0);
            ShuoCombat.follow(c,4,()->{
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,5);
                ShuoVisuals.slash(c,3.5,100,1);
                var second=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),3.5,100,c.value(45,10,.35f),0,0,0);
                for(var id:second)if(first.contains(id)&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,6,false);
            });return;
        }
        ShuoCombat.windup(c,9,()->{
        float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.CUT_LEFT,7);
        var origin=c.player.position();var facing=c.forward(1);float amount=c.value(110,22,.85f);
        ShuoVisuals.rift(c,origin,facing,7,1.2);
        var first=ShuoCombat.line(c,origin,facing,7,1.2,amount*.6f+bonus);
        for(var id:first)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.slow(t,.35f,20);
        ShuoCombat.follow(c,6,()->{
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,5);
            ShuoVisuals.rift(c,origin,facing,7,1.2);
            var second=ShuoCombat.line(c,origin,facing,7,1.2,amount*.4f);
            for(var id:second)if(first.contains(id)&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,8,false);
        });
    });}
}
