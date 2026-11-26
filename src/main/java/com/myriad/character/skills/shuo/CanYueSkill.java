package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class CanYueSkill extends AbilitySkill {
    private static final AbilityDamageFormula DRAWN = AbilityDamageFormula.physicalFromSpell(280, 90, 1.35f, .80f)
            .segment("skill.myriad.damage.moon_final", 1);
    private static final AbilityDamageFormula FIRST_TWO = AbilityDamageFormula.physicalFromSpell(100, 40, .60f, .80f * 2 / 7)
            .segment("skill.myriad.damage.moon_first_two", 2);
    private static final AbilityDamageFormula THIRD = AbilityDamageFormula.physicalFromSpell(160, 60, .90f, .80f * 3 / 7)
            .segment("skill.myriad.damage.moon_third", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DRAWN, FIRST_TWO, THIRD);
    }

    public CanYueSkill(Definition d){super(d);}
    static void display(AbilityRuntime.Cast c, int completed, int ticks) {
        for (int i = 0; i <= 3; i++) c.buff("g1_shuo_moon_" + i, i == completed ? ticks : 0);
    }
    @Override public void windupPose(AbilityRuntime.Cast c, boolean winding) {
        if (!winding) { CrescentGesture.discard(c.player); display(c, -1, 0); }
    }
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        CrescentGesture.discard(c.player);
        c.memory().count=0;c.memory().until=0;c.memory().targets.clear();c.buff(getId()+"_recast",0);
        if(ShuoCombat.drawn(c.player)){
            c.memory().tag="drawn";
            ShuoCombat.windup(c,6,()->{
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.RETURNING_MOON,8);
                ShuoVisuals.slash(c,5,140,2);
                ShuoCombat.cone(c,5,140,DRAWN.value(c),0);
                ShuoCombat.follow(c,6,()->ShuoCombat.sheathe(c.player));
            });return;
        }
        c.memory().tag="sheathed";cut(c);
    }
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return c.memory().tag.equals("sheathed")&&c.memory().count>0&&c.memory().count<3&&c.memory().until>c.now();}
    @Override public void recast(AbilityRuntime.Cast c){cut(c);}
    private void cut(AbilityRuntime.Cast c){
        var m=c.memory();int step=m.count++;m.until=0;c.buff(getId()+"_recast",0);
        var gesture = step > 0 ? CrescentGesture.accepted(c.player) : null;
        display(c, step, 24);
        ShuoCombat.windup(c,4,()->{
            if(gesture==null) strike(c,step);
            else gesture.decide(c,chase->{if(chase) pursue(c,step); else strike(c,step);});
        });
    }
    private void pursue(AbilityRuntime.Cast c,int step) {
        CrescentGesture.discard(c.player);
        ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.PURSUIT,5);
        c.actionApproach(c.forward(4), 4, 1, () -> {
            if (!AbilityRuntime.controlled(c.player)) strike(c, step);
        }, () -> {
            display(c,-1,0);
            ShuoCombat.cue(c.player,com.myriad.network.ShuoViewPayload.CANCEL,0);
        });
        ShuoVisuals.trackMotion(c,true);
    }
    private void strike(AbilityRuntime.Cast c,int step){
            var m=c.memory();
            float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.MOON_FIRST + step,7);
            ShuoVisuals.slash(c,step==2?4.5:4,step==2?90:110,step);
            var hits=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),step==2?4.5:4,step==2?90:110,
                (step==2?THIRD.value(c):FIRST_TWO.value(c))+bonus,0,step==0?.25f:0,20);
            for(var id:hits){
                long prior=m.targets.getOrDefault(id,0L);
                if(step==2&&prior==3&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,10,false);
                m.targets.put(id,prior|(1L<<step));
            }
            display(c, step+1, step==2?6:40);
            if(step==2){m.count=0;m.targets.clear();ShuoCombat.follow(c,6,()->ShuoCombat.sheathe(c.player));}
            else {m.until=c.now()+40;c.buff(getId()+"_recast",40);long window=m.until;
                c.later(41,false,()->{if(m.until==window&&m.count>0){m.count=0;m.until=0;m.targets.clear();c.buff(getId()+"_recast",0);ShuoCombat.sheathe(c.player);}});
                CrescentGesture.consume(c);}
    }
}
