package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class WinterBastionSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 90, 30, .4f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public WinterBastionSkill(Definition definition){super(definition);}

@Override public void release(Cast c){StaffHandCues.release(c);for(var ally:c.allies(c.player.position(),6))c.shield(ally,c.value(230,70,.7f),100);c.area(c.player.position(),5,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(c.player.position(),5))c.root(t,20,false);}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),5,24);}
}

