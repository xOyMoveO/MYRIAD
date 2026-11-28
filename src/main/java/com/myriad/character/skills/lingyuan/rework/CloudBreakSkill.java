package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;


/** Independent branch; no gameplay switches in the batch registry. */
public final class CloudBreakSkill extends AbilitySkill {
    public static final int DASH_TICKS=8;
    public static final double DASH_RANGE=6, HIT_RADIUS=1.2;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(65, 22.5f, .55f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range", DASH_RANGE);
    }

    public CloudBreakSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&AbilityTargeting.clearSweep(c.player,c.forward(DASH_RANGE / DASH_TICKS));}
@Override public void dashPose(Cast c,DashPose phase,Vec3 direction){LingyuanHandCues.dash(c,phase);}
@Override public void release(Cast c){c.actionDash(c.forward(DASH_RANGE),DASH_TICKS,HIT_RADIUS,(t,first)->{if(c.damage(t,DAMAGE.value(c),DAMAGE.kind()))SwordVisuals.dashHit(c,t.position().add(0,.8,0));},()->{},false,false);}
@Override public void present(Cast c){SwordVisuals.dash(c);}
}

