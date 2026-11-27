package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class LifeThreadSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount("shield", 150, 43.75f, .7f);
    private static final SupportAmount HEAL = new SupportAmount("heal", 20, 8.75f, .15f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD, HEAL); }

 public LifeThreadSkill(Definition definition){super(definition);}

 @Override public void release(Cast c){LivingEntity picked=c.target(12,true);final LivingEntity ally=picked==null?c.player:picked;StaffHandCues.release(c,ally==c.player);c.shield(ally,SHIELD.value(c),80);
 c.whileActive(80,a->{
     if(!ally.isAlive()||ally.level()!=c.player.level()||ally.distanceToSqr(c.player)>196||!c.player.hasLineOfSight(ally))return false;
     if(a>0&&a%20==0)HerbVisuals.heal(c,ally,HEAL.value(c),.64);
     if(a%5==0)AbilityVisuals.emit(c,AbilityVfxGeometry.BEAM,c.player.position().add(0,.8,0),ally.position().add(0,.8,0),.10,0,5,false);
     return true;
 });}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),1,false);}
}

