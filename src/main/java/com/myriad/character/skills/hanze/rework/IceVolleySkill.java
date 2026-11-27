package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class IceVolleySkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 25, 10, .2f).segment("skill.myriad.damage.segment.projectile", 3);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public IceVolleySkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    for(int i=0;i<3;i++){
        int shot=i;
        c.actionLater(shot*5,()->{
            c.actionProjectile(15,1.5,.75,false,false,(t,back)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,.2f,20);});
            StaffHandCues.volley(c,shot);
        });
    }
}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.75,false);}
}

