package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class AbsoluteZeroSkill extends AbilitySkill {
    public AbsoluteZeroSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4.5;}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(20,0,()->{c.area(at,4.5,c.value(115,25,.7f),MAGIC);for(var t:c.enemies(at,4.5))c.root(t,24,false);AbilityVisuals.pillar(c,at,3);});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,4.5,20);}
}

