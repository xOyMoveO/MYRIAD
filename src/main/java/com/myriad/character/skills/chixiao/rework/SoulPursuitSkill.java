package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

/** Independent reusable branch. */
public final class SoulPursuitSkill extends AbilitySkill {
    public static final double DASH_RANGE = 7, HIT_RADIUS = 1.2;
    public static final int DASH_TICKS = 8, RECAST_WINDOW = 60, SLOW_TICKS = 30;
    public static final float SLOW = .30f;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(50, 18.75f, .4f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("dash_range_blocks", DASH_RANGE, "return_window_seconds", RECAST_WINDOW / 20.0,
                "slow_percent", 100 * SLOW, "slow_seconds", SLOW_TICKS / 20.0);
    }

 public SoulPursuitSkill(Definition definition){super(definition);}

@Override public boolean canRelease(Cast c){return c.data.getBuff("g1_root")==0&&AbilityTargeting.clearSweep(c.player,c.forward(DASH_RANGE / DASH_TICKS));}
@Override public void release(Cast c){var m=c.memory();m.point=c.player.position();m.until=c.now()+RECAST_WINDOW;c.buff(getId()+"_recast",RECAST_WINDOW);c.memory("vfxDashPhase").count=0;c.actionDash(c.forward(DASH_RANGE),DASH_TICKS,HIT_RADIUS,(t,first)->{c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);},()->{},false,false);}
@Override public boolean recastAvailable(Cast c){return c.memory().point!=null&&c.memory().until>c.now();}
@Override public void recast(Cast c){Vec3 to=c.memory().point;if(to==null||!c.safe(to)||!AbilityTargeting.clearSegment(c.player,to))return;c.memory().until=0;c.memory().point=null;c.buff(getId()+"_recast",0);c.memory("vfxDashPhase").count=1;c.actionDash(to.subtract(c.player.position()),DASH_TICKS,0,(t,first)->{},()->{},false,false);}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.85,false);}
@Override public void dashPose(Cast c,DashPose phase,Vec3 direction){WarSpiritHandCues.dash(c,phase,c.memory("vfxDashPhase").count==1);}
}

