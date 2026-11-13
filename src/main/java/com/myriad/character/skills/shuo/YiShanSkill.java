package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class YiShanSkill extends AbilitySkill {
    public YiShanSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.FLASH,7);
            float amount=c.value(65,15,.55f);
            c.actionDash(c.forward(4),5,1.0,(t,first)->{if(ShuoCombat.physicalHit(c,t,amount)){ShuoVisuals.hit(c,t);c.slow(t,.25f,20);}},()->{},false,false);
            ShuoVisuals.trackMotion(c,true);
            return;
        }
        ShuoCombat.windup(c,10,()->{
            float amount=c.value(95,20,.8f),bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.FLASH,9);
            var marked=new java.util.HashSet<java.util.UUID>();
            c.actionDash(c.forward(7),6,1.1,(t,first)->{
                if(ShuoCombat.physicalHit(c,t,amount*.6f+bonus)){marked.add(t.getUUID());ShuoVisuals.hit(c,t);ShuoVisuals.mark(c,t);}
            },()->{
                if(AbilityRuntime.controlled(c.player)){ShuoVisuals.cancel(c.player,index());return;}
                // Already-applied blade wounds are delayed damage, not another interruptible cast.
                c.later(6,false,()->{
                    ShuoVisuals.cancel(c.player,index());
                    for(var id:marked)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t&&c.enemy(t)
                            &&t.distanceToSqr(c.player)<=24*24&&ShuoCombat.physicalHit(c,t,amount*.4f)){
                        c.slow(t,.35f,20);ShuoVisuals.contact(c.player,index(),t,.85f);
                    }
                });
            },false,false);
            ShuoVisuals.trackMotion(c,false);
        });
    }
}
