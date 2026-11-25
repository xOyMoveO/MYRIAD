package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class QiaoFanSkill extends AbilitySkill {
    private static final AbilityDamageFormula SWEEP = AbilityDamageFormula.physicalFromSpell(65, 18, .45f, .50f)
            .segment("skill.myriad.damage.flowing_cut", 1);
    private static final AbilityDamageFormula COUNTER = AbilityDamageFormula.physicalFromSpell(50, 15, .40f, .50f)
            .segment("skill.myriad.damage.riposte", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(SWEEP, COUNTER);
    }

    public QiaoFanSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        c.memory().until=0;c.buff(getId()+"_recast",0);
        if(!ShuoCombat.drawn(c.player)){ShuoCombat.guard(c);return;}
        ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,7);
        ShuoVisuals.slash(c,3.5,100,1);
        ShuoCombat.cone(c,3.5,100,SWEEP.value(c),0,.30f,20);
    }
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return c.memory().until>c.now();}
    @Override public void recast(AbilityRuntime.Cast c){
        c.memory().until=0;c.buff(getId()+"_recast",0);
        float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.CUT_LEFT,7);
        ShuoVisuals.slash(c,4,80,0);
        var hits=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),4,80,COUNTER.value(c)+bonus,6,0,0);
        // A successful defence must still land its counter; empty counters grant no shield.
        if(!hits.isEmpty())c.shield(c.player,c.spellPowerValue(50,10,.25f),40);
    }
}
