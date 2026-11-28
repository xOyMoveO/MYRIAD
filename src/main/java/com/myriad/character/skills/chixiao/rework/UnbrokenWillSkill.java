package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class UnbrokenWillSkill extends AbilitySkill {
    public static final double HEALTH_RATIO = .40;
    public static final int COOLDOWN_TICKS = 240, SHIELD_TICKS = 60, HASTE_TICKS = 40;
    public static final float HASTE = .15f;
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 80, 26.4f, .4f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public UnbrokenWillSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(c.player.getHealth()>c.player.getMaxHealth()*HEALTH_RATIO||c.memory().cooldown>c.now())return;c.memory().cooldown=c.now()+COOLDOWN_TICKS;c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);c.haste(HASTE,HASTE_TICKS);}
@Override public void tickPassive(Cast c){if(c.memory().cooldown<=c.now()&&c.player.getHealth()<=c.player.getMaxHealth()*HEALTH_RATIO)AbilityVisuals.ready(c);}
@Override public void release(Cast c){}
}

