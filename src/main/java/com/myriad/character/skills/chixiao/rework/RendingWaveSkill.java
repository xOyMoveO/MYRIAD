package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class RendingWaveSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(85, 27.5f, .55f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public RendingWaveSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.actionProjectile(14,1.2,1.1,true,false,(t,back)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,.3f,40);});}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.arc(c,c.player.position(),3,110);}
}

