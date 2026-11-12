package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
public final class CanYueSkill extends AbilitySkill {
    public CanYueSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        c.memory().count=0;c.memory().until=0;c.memory().targets.clear();c.buff(getId()+"_recast",0);
        if(ShuoCombat.drawn(c.player)){
            c.memory().tag="drawn";
            ShuoCombat.windup(c,6,()->{
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_DOWN,8);
                ShuoVisuals.slash(c,5,140,2);
                ShuoCombat.cone(c,5,140,c.value(280,90,1.35f),0);
                ShuoCombat.follow(c,6,()->ShuoCombat.sheathe(c.player));
            });return;
        }
        c.memory().tag="sheathed";cut(c);
    }
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return c.memory().tag.equals("sheathed")&&c.memory().count>0&&c.memory().count<3&&c.memory().until>c.now();}
    @Override public void recast(AbilityRuntime.Cast c){cut(c);}
    private void cut(AbilityRuntime.Cast c){
        var m=c.memory();int step=m.count++;m.until=0;c.buff(getId()+"_recast",0);
        boolean chase=step>0&&c.player.isShiftKeyDown();
        ShuoCombat.windup(c,4,()->{
            if(chase){
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.FLASH,5);
                c.dash(c.forward(2),4,.2,(t,first)->{},()->{if(!AbilityRuntime.controlled(c.player))strike(c,step);},false,false);
                ShuoVisuals.trackMotion(c,true);
            }else strike(c,step);
        });
    }
    private void strike(AbilityRuntime.Cast c,int step){
            var m=c.memory();
            float bonus=ShuoCombat.draw(c.player,step==0?com.myriad.network.ShuoViewPayload.CUT_LEFT:step==1?com.myriad.network.ShuoViewPayload.CUT_RIGHT:com.myriad.network.ShuoViewPayload.CUT_DOWN,7);
            ShuoVisuals.slash(c,step==2?4.5:4,step==2?90:110,step);
            var hits=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),step==2?4.5:4,step==2?90:110,
                (step==2?c.value(160,60,.9f):c.value(100,40,.6f))+bonus,0,step==0?.25f:0,20);
            for(var id:hits){
                long prior=m.targets.getOrDefault(id,0L);
                if(step==2&&prior==3&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,10,false);
                m.targets.put(id,prior|(1L<<step));
            }
            if(step==2){m.count=0;m.targets.clear();ShuoCombat.follow(c,6,()->ShuoCombat.sheathe(c.player));}
            else {m.until=c.now()+40;c.buff(getId()+"_recast",40);long window=m.until;
                c.later(41,false,()->{if(m.until==window&&m.count>0){m.count=0;m.until=0;m.targets.clear();c.buff(getId()+"_recast",0);ShuoCombat.sheathe(c.player);}});}
    }
}
