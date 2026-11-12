package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class WindWalkingBladeSkill extends AbilitySkill {
    public WindWalkingBladeSkill(Definition definition){super(definition);}
    @Override public void tickPassive(Cast c){var m=c.memory();Vec3 pos=c.player.position();if(m.lastPosition!=null&&c.player.onGround()&&!AbilityRuntime.channeling(c.player)&&!AbilityRuntime.moving(c.player)
            &&!com.myriad.combat.KnockbackBuffer.recentlyForced(c.player)){
 double moved=pos.subtract(m.lastPosition).multiply(1,0,1).length();if(moved<=.55)m.distance+=moved;}
 m.lastPosition=pos;if(m.distance>=30){m.distance=0;m.count=1;c.data.setBuff("sulan_passive_ready",1);}if(m.count>0)com.myriad.character.ability.AbilityVisuals.ready(c);}
 @Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.count==0)return;m.count=0;com.myriad.character.ability.AbilityVisuals.arc(c,h.target().position(),.85,145);c.data.setBuff("sulan_passive_ready",0);
 c.damage(h.target(),c.value(35,12,.35f),PHYSICAL,false);}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
