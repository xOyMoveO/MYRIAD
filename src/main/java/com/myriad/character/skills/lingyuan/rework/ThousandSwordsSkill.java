package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class ThousandSwordsSkill extends AbilitySkill {
    public ThousandSwordsSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 10;}
@Override public void release(Cast c){Vec3 at=c.point;c.repeat(120,20,i->{c.pulse(at,10,c.value(55,30,.25f),MAGIC);SwordVisuals.rainPulse(c,at,i);for(var t:c.enemies(at,10).stream().limit(8).toList())AbilityVisuals.beam(c,t.position().add(0,3.8,0),t.position().add(0,.4,0),.15);});}
@Override public void present(Cast c){SwordVisuals.rain(c);}
}

