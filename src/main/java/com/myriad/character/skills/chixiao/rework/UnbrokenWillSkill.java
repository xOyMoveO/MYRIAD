package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class UnbrokenWillSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 80, 26.4f, .4f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public UnbrokenWillSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(c.player.getHealth()>c.player.getMaxHealth()*.4||c.memory().cooldown>c.now())return;c.memory().cooldown=c.now()+240;c.shield(c.player,SHIELD.value(c),60);c.haste(.15f,40);}
@Override public void tickPassive(Cast c){if(c.memory().cooldown<=c.now()&&c.player.getHealth()<=c.player.getMaxHealth()*.4)AbilityVisuals.ready(c);}
@Override public void release(Cast c){}
}

