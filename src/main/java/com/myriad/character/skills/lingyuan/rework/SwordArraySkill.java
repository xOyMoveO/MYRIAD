package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class SwordArraySkill extends AbilitySkill {
    public SwordArraySkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4;}
@Override public void release(Cast c){Vec3 at=c.point;c.repeat(80,20,i->{c.pulse(at,4,c.value(35,10,.25f),MAGIC);for(var t:c.enemies(at,4))c.slow(t,.25f,20);});}
@Override public void present(Cast c){SwordVisuals.array(c);}
}

