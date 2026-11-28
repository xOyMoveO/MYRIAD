package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.character.ability.AbilityVfxGeometry.PILLAR;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class AbsoluteZeroSkill extends AbilitySkill {
    public static final int WINDUP_TICKS=20, ROOT_TICKS=24;
    public static final double POINT_RANGE=14, RADIUS=4.5;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 115, 31.25f, .7f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("charge", WINDUP_TICKS / 20.0, "point_range", POINT_RANGE, "radius", RADIUS, "root", ROOT_TICKS / 20.0);
    }

    public AbsoluteZeroSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return POINT_RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void selectionPose(Cast c,boolean active){StaffHandCues.select(c,active);}
@Override public void windupPose(Cast c,boolean active){StaffHandCues.charge(c,active);}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(WINDUP_TICKS,0,()->{
    StaffHandCues.release(c);
    c.area(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());
    for(var t:c.enemies(at,RADIUS))c.root(t,ROOT_TICKS,false);
    Vec3 heading=c.forward(1).multiply(1,0,1).normalize();
    AbilityVisuals.emit(c,PILLAR,at,at.add(heading).add(0,3,0),.75,0,13,false);
});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,RADIUS,WINDUP_TICKS);}
}

