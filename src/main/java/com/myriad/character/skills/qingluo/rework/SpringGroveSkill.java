package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpringGroveSkill extends AbilitySkill {
 public SpringGroveSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 7;}
@Override public void release(Cast c){Vec3 at=c.point;c.repeat(120,20,i->{for(var ally:c.allies(at,7))c.heal(ally,c.value(45,20,.22f));AbilityVisuals.burst(c,at,2);});}
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,7,120);}
}

