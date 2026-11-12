package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrozenExpanseSkill extends AbilitySkill {
    public FrozenExpanseSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 7;}
@Override public void release(Cast c){Vec3 at=c.point;c.repeat(120,20,i->{c.pulse(at,7,c.value(45,25,.22f),MAGIC);for(var t:c.enemies(at,7))c.slow(t,.35f,20);AbilityVisuals.pillar(c,at.add(Math.cos(i*2)*3,0,Math.sin(i*2)*3),2.7);});}
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,7,120);}
}

