package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class RendingWaveSkill extends AbilitySkill {
    public static final double RANGE = 14, SPEED = 1.2, RADIUS = 1.1;
    public static final int SLOW_TICKS = 40;
    public static final float SLOW = .30f;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(85, 27.5f, .55f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public RendingWaveSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.actionProjectile(RANGE,SPEED,RADIUS,true,false,(t,back)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);});}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.arc(c,c.player.position(),3,110);}
}

