package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritTetherSkill extends AbilitySkill {
    public static final double RANGE = 12;
    public static final int TETHER_TICKS = 40;
    public static final int ROOT_TICKS = 12;
    public static final float SLOW = .3f;
    public static final int SLOW_TICKS = 30;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "targetRange", RANGE,
            "tetherSeconds", TETHER_TICKS / 20.0,
            "rootSeconds", ROOT_TICKS / 20.0,
            "slowPercent", Math.round(100 * SLOW),
            "slowSeconds", SLOW_TICKS / 20.0);
    }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 15, .3f).segment("skill.myriad.damage.segment.tether", 2);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public SpiritTetherSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.target(RANGE,false)!=null;}
@Override public void release(Cast c){
    LivingEntity t=c.target(RANGE,false);if(t==null)return;
    WarSpiritHandCues.cast(c);
    c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.root(t,ROOT_TICKS,false);thread(c,t,false);
    c.whileActive(TETHER_TICKS,a->{
        if(!t.isAlive()||t.level()!=c.player.level()||t.distanceToSqr(c.player)>RANGE*RANGE||!c.player.hasLineOfSight(t))return false;
        if(a<TETHER_TICKS){if(a>0&&a%5==0)thread(c,t,false);return true;}
        c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);
        WarSpiritHandCues.follow(c);thread(c,t,true);AbilityVisuals.burst(c,t.position(),1);return false;
    });
}
private static void thread(Cast c,LivingEntity t,boolean close){
    AbilityVisuals.emit(c,AbilityVfxGeometry.BEAM,c.player.position().add(0,.8,0),t.position().add(0,.8,0),.09,close?1:0,close?8:5,false);
}
}

