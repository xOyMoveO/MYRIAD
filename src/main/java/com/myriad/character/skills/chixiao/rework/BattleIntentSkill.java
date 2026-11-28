package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class BattleIntentSkill extends AbilitySkill {
    public static final int WINDOW_TICKS = 120, STACKS = 3, HIT_INTERVAL_TICKS = 4;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(TRUE, 25, 12, .25f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public BattleIntentSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){
    Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target()))return;
    var m=c.memory();if(!h.target().getUUID().equals(m.target)||c.now()>m.until)m.count=0;
    if(c.now()-m.last<HIT_INTERVAL_TICKS)return;
    if(!h.basic()&&m.tag.equals(h.skillId()+"/"+AbilityRuntime.sourceCast()))return;
    m.target=h.target().getUUID();m.last=c.now();m.until=c.now()+WINDOW_TICKS;
    m.tag=h.basic()?"":h.skillId()+"/"+AbilityRuntime.sourceCast();
    if(m.count>=STACKS&&!h.basic()){
        m.count=0;c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);
        AbilityVisuals.burst(c,h.target().position(),.8);
    }else{
        m.count=Math.min(STACKS,m.count+1);
        AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,h.target(),.43,m.count,8);
    }
    c.buff(getId()+"_ready",m.count>=STACKS?WINDOW_TICKS:0);
}
@Override public void release(Cast c){}
}

