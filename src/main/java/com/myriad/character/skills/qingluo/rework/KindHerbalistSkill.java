package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class KindHerbalistSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 20, 9.6f, .18f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

 public KindHerbalistSkill(Definition definition){super(definition);}

 @Override public void onSupport(Cast c,LivingEntity t){var m=c.memory(t.getUUID().toString());if(t.getHealth()>=t.getMaxHealth()*.6||m.cooldown>c.now())return;m.cooldown=c.now()+80;c.heal(t,HEAL.value(c));c.slow(t,-.1f,40);HerbVisuals.restoration(c,t,.65);}
@Override public void release(Cast c){}
}

