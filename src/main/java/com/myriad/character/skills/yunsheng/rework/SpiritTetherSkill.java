package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritTetherSkill extends AbilitySkill {
 public SpiritTetherSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.target(12,false)!=null;}
@Override public void release(Cast c){
    LivingEntity t=c.target(12,false);if(t==null)return;
    WarSpiritHandCues.cast(c);
    c.damage(t,c.value(35,12,.3f),MAGIC);c.root(t,12,false);thread(c,t,false);
    c.whileActive(40,a->{
        if(!t.isAlive()||t.level()!=c.player.level()||t.distanceToSqr(c.player)>144||!c.player.hasLineOfSight(t))return false;
        if(a<40){if(a>0&&a%5==0)thread(c,t,false);return true;}
        c.damage(t,c.value(35,12,.3f),MAGIC);c.slow(t,.3f,30);
        WarSpiritHandCues.follow(c);thread(c,t,true);AbilityVisuals.burst(c,t.position(),1);return false;
    });
}
private static void thread(Cast c,LivingEntity t,boolean close){
    AbilityVisuals.emit(c,AbilityVfxGeometry.BEAM,c.player.position().add(0,.8,0),t.position().add(0,.8,0),.09,close?1:0,close?8:5,false);
}
}

