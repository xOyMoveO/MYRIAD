package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ThunderArcSkill extends AbilitySkill {
    public static final double CHAIN_RADIUS=4;
    public static final double RANGE = 18, SPEED = 2.5, RADIUS = .9;
    public static final int WINDUP = 6, SLOW_TICKS = 30;
    public static final float SLOW_RATE = .25f;
    public static final float CHAIN_RATIO=.35f;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(60, 22.5f, .65f, .5f).segment("skill.myriad.damage.segment.primary", 1);
    private static final AbilityDamageFormula CHAIN =
            DAMAGE.scaled(CHAIN_RATIO).segment("skill.myriad.damage.segment.chain", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE, CHAIN);
    }

    public ThunderArcSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){AbilityVisuals.charging(c,WINDUP,RANGE,.3);}
    @Override public void release(Cast c){
        // One thrown arc stops at the first enemy. Only that contact fans out; no recursive chains.
        ThunderSkillPresentation.charged(c,WINDUP,com.myriad.network.ThunderSkillViewPayload.ARC_READY,
                com.myriad.network.ThunderSkillViewPayload.ARC_FIRE,
                ()->{ThunderAudio.play(c,ThunderAudio.Cue.CAST);c.actionProjectile(RANGE,SPEED,RADIUS,false,false,(target,back)->discharge(c,target));});
    }
    private static void discharge(Cast c,LivingEntity primary){
        Vec3 center=primary.position();
        ThunderAudio.play(c,ThunderAudio.Cue.HIT,center);
        // Snapshot before the main hit: a lethal hit still discharges from the same impact point.
        var secondary=c.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(CHAIN_RADIUS),
                t->t!=primary&&!t.isSpectator()&&!t.isRemoved()&&c.enemy(t)
                        &&t.position().distanceToSqr(center)<=CHAIN_RADIUS*CHAIN_RADIUS&&primary.hasLineOfSight(t));
        c.damage(primary,DAMAGE.value(c),DAMAGE.kind());
        c.slow(primary,SLOW_RATE,SLOW_TICKS);
        Vec3 from=primary.getBoundingBox().getCenter();
        for(var target:secondary){
            AbilityVisuals.beam(c,from,target.getBoundingBox().getCenter(),.12);
            // Derived damage cannot crit again or trigger a new direct-hit passive/chain.
            c.damage(target,CHAIN.value(c),CHAIN.kind(),false);
            AbilityVisuals.burst(c,target.position().add(0,.4,0),.45);
        }
    }
}
