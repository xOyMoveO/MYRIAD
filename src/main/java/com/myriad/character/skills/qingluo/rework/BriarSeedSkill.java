package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class BriarSeedSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 50, 16, .45f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public BriarSeedSkill(Definition definition){super(definition);}

 @Override public void release(Cast c){StaffHandCues.release(c);c.actionProjectile(15,1.2,.8,false,false,(t,back)->{
     c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.root(t,14,false);
     for(var ally:c.allies(t.position(),3))HerbVisuals.heal(c,ally,c.value(35,12,.25f),.70);
 });}
@Override public void present(Cast c){AbilityVisuals.arc(c,c.player.position(),1,65);}
}

