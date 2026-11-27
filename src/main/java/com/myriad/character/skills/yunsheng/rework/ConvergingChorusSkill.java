package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class ConvergingChorusSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount("heal", 45, 20.7f, .25f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 23, .25f).segment("skill.myriad.damage.segment.pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public ConvergingChorusSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 6;}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(20,0,()->{WarSpiritHandCues.cast(c);AbilityVisuals.field(c,at,6,80);c.repeat(80,20,i->{for(var ally:c.allies(at,6))c.heal(ally,HEAL.value(c));c.pulse(at,6,DAMAGE.value(c),DAMAGE.kind());});});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,6,20);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
@Override public void windupPose(Cast c,boolean winding){WarSpiritHandCues.chorus(c,winding);}
}

