package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class CloudBreakSkill extends AbilitySkill {
    public CloudBreakSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&AbilityTargeting.clearSweep(c.player,c.forward(.75));}
@Override public void release(Cast c){c.dash(c.forward(6),8,1.2,(t,first)->c.damage(t,c.value(65,18,.55f),PHYSICAL),()->{},false,false);}
@Override public void present(Cast c){SwordVisuals.dash(c);}
}

