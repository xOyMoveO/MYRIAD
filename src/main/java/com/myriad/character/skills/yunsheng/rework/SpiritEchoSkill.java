package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritEchoSkill extends AbilitySkill {
    public static final int REQUIRED_SUPPORTS = 2;
    public static final int MIN_INTERVAL = 4;
    public static final int SUPPORT_WINDOW = 120;
    public static final int INTERNAL_COOLDOWN = 160;
    public static final int SHIELD_TICKS = 60;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "supportCount", REQUIRED_SUPPORTS,
            "minimumInterval", MIN_INTERVAL / 20.0,
            "windowSeconds", SUPPORT_WINDOW / 20.0,
            "procSeconds", INTERNAL_COOLDOWN / 20.0,
            "shieldSeconds", SHIELD_TICKS / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 45, 18, .25f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public SpiritEchoSkill(Definition definition){super(definition);}

@Override public void onSupport(Cast c,LivingEntity t){var m=c.memory(t.getUUID().toString());if(m.cooldown>c.now()||c.now()-m.last<MIN_INTERVAL)return;if(m.until<=c.now())m.count=0;m.last=c.now();m.until=c.now()+SUPPORT_WINDOW;if(++m.count<REQUIRED_SUPPORTS)return;m.count=0;m.cooldown=c.now()+INTERNAL_COOLDOWN;c.shield(t,SHIELD.value(c),SHIELD_TICKS);}
@Override public void release(Cast c){}
}

