package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class QiaoFanSkill extends AbilitySkill {
    public static final double DRAWN_RANGE = 3.5, COUNTER_RANGE = 4;
    public static final int COUNTER_SHIELD_TICKS = 40, COUNTER_STUN_TICKS = 6, DRAWN_SLOW_TICKS = 20;
    public static final float DRAWN_SLOW = .30f;
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.BONUS_PHYSICAL,
            57.5f, 12.5f, 1f / 3);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula SWEEP = AbilityDamageFormula.physicalFromSpell(65, 22.5f, .45f, .50f)
            .segment("skill.myriad.damage.flowing_cut", 1);
    private static final AbilityDamageFormula COUNTER = AbilityDamageFormula.physicalFromSpell(50, 18.75f, .40f, .50f)
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
        ShuoVisuals.slash(c,DRAWN_RANGE,100,1);
        ShuoCombat.cone(c,DRAWN_RANGE,100,SWEEP.value(c),0,DRAWN_SLOW,DRAWN_SLOW_TICKS);
    }
    @Override public boolean recastAvailable(AbilityRuntime.Cast c){return c.memory().until>c.now();}
    @Override public void recast(AbilityRuntime.Cast c){
        c.memory().until=0;c.buff(getId()+"_recast",0);
        float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.CUT_LEFT,7);
        ShuoVisuals.slash(c,COUNTER_RANGE,80,0);
        var hits=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),COUNTER_RANGE,80,COUNTER.value(c)+bonus,COUNTER_STUN_TICKS,0,0);
        // A successful defence must still land its counter; empty counters grant no shield.
        if(!hits.isEmpty())c.shield(c.player,SHIELD.value(c),COUNTER_SHIELD_TICKS);
    }
}
