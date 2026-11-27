package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;


/** Independent branch; no gameplay switches in the batch registry. */
public final class CloudBreakSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(65, 22.5f, .55f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public CloudBreakSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&AbilityTargeting.clearSweep(c.player,c.forward(.75));}
@Override public void dashPose(Cast c,DashPose phase,Vec3 direction){LingyuanHandCues.dash(c,phase);}
@Override public void release(Cast c){c.actionDash(c.forward(6),8,1.2,(t,first)->{if(c.damage(t,DAMAGE.value(c),DAMAGE.kind()))SwordVisuals.dashHit(c,t.position().add(0,.8,0));},()->{},false,false);}
@Override public void present(Cast c){SwordVisuals.dash(c);}
}

