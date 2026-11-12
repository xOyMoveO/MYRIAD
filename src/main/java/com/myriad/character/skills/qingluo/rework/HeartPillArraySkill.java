package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class HeartPillArraySkill extends AbilitySkill {
 public HeartPillArraySkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4;}
@Override public void release(Cast c){Vec3 at=c.point;for(var ally:c.allies(at,4))c.shield(ally,c.value(80,22,.35f),25);c.repeat(80,20,i->{for(var ally:c.allies(at,4))c.shield(ally,c.value(80,22,.35f),25);});}
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,4,80);}
}

