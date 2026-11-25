package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordArraySkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 10, .25f).segment("skill.myriad.damage.segment.pulse", 4);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SwordArraySkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4;}
@Override public void selectionPose(Cast c,boolean active){LingyuanHandCues.aim(c,active);}
@Override public void release(Cast c){LingyuanHandCues.release(c);Vec3 at=c.point;c.repeat(80,20,i->{c.pulse(at,4,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(at,4))c.slow(t,.25f,20);SwordVisuals.arrayPulse(c,at,i);});}
@Override public void present(Cast c){SwordVisuals.array(c);}
}

