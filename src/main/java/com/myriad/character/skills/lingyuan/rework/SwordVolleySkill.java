package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordVolleySkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 10, .25f).segment("skill.myriad.damage.segment.sword", 3);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public SwordVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    for(int i=0;i<3;i++){
        int shot=i;
        c.actionLater(shot*4,()->{
            c.actionProjectile(18,1.5,.7,false,false,(t,back)->c.damage(t,DAMAGE.value(c),DAMAGE.kind()));
            LingyuanHandCues.volley(c,shot);
        });
    }
}
@Override public void present(Cast c){SwordVisuals.volley(c);}
}

