package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class VerdantDewSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount("heal", 70, 27.5f, .5f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

 public VerdantDewSkill(Definition definition){super(definition);}

 @Override public void release(Cast c){LivingEntity ally=c.target(12,true);if(ally==null)ally=c.player;StaffHandCues.release(c,ally==c.player);c.heal(ally,HEAL.value(c));c.slow(ally,-.15f,30);AbilityVisuals.beam(c,c.player.position().add(0,1,0),ally.position().add(0,1,0),.12);HerbVisuals.restoration(c,ally,.9);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.7,false);}
}

