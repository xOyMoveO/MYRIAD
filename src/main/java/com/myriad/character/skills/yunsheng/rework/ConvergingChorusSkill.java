package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class ConvergingChorusSkill extends AbilitySkill {
 public ConvergingChorusSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 6;}
@Override public void release(Cast c){Vec3 at=c.point;c.windup(20,0,()->{AbilityVisuals.field(c,at,6,80);c.repeat(80,20,i->{for(var ally:c.allies(at,6))c.heal(ally,c.value(45,18,.25f));c.pulse(at,6,c.value(45,20,.25f),MAGIC);});});}
@Override public void present(Cast c){AbilityVisuals.warning(c,c.point,6,20);}
}

