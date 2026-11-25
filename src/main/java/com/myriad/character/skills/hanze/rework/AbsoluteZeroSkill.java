package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.character.ability.AbilityVfxGeometry.PILLAR;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class AbsoluteZeroSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 115, 25, .7f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public AbsoluteZeroSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4.5;}
@Override public void selectionPose(Cast c,boolean active){StaffHandCues.select(c,active);}
@Override public void windupPose(Cast c,boolean active){StaffHandCues.charge(c,active);}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(20,0,()->{
    StaffHandCues.release(c);
    c.area(at,4.5,DAMAGE.value(c),DAMAGE.kind());
    for(var t:c.enemies(at,4.5))c.root(t,24,false);
    Vec3 heading=c.forward(1).multiply(1,0,1).normalize();
    AbilityVisuals.emit(c,PILLAR,at,at.add(heading).add(0,3,0),.75,0,13,false);
});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,4.5,20);}
}

