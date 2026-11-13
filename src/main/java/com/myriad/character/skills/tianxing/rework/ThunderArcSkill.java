package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.PHYSICAL;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ThunderArcSkill extends AbilitySkill {
    public static final double CHAIN_RADIUS=4;
    public static final double RANGE=18,SPEED=2.5;
    public static final float CHAIN_RATIO=.35f;
    public ThunderArcSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){AbilityVisuals.charging(c,6,RANGE,.3);}
    @Override public void release(Cast c){
        // One thrown arc stops at the first enemy. Only that contact fans out; no recursive chains.
        ThunderSkillPresentation.charged(c,6,com.myriad.network.ThunderSkillViewPayload.ARC_READY,
                com.myriad.network.ThunderSkillViewPayload.ARC_FIRE,
                ()->{ThunderAudio.play(c,ThunderAudio.Cue.CAST);c.actionProjectile(RANGE,SPEED,.9,false,false,(target,back)->discharge(c,target));});
    }
    private static void discharge(Cast c,LivingEntity primary){
        Vec3 center=primary.position();
        ThunderAudio.play(c,ThunderAudio.Cue.HIT,center);
        // Snapshot before the main hit: a lethal hit still discharges from the same impact point.
        var secondary=c.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(CHAIN_RADIUS),
                t->t!=primary&&!t.isSpectator()&&!t.isRemoved()&&c.enemy(t)
                        &&t.position().distanceToSqr(center)<=CHAIN_RADIUS*CHAIN_RADIUS&&primary.hasLineOfSight(t));
        float damage=c.value(60,18,.65f);
        c.damage(primary,damage,PHYSICAL);
        c.slow(primary,.25f,30);
        Vec3 from=primary.getBoundingBox().getCenter();
        for(var target:secondary){
            AbilityVisuals.beam(c,from,target.getBoundingBox().getCenter(),.12);
            // Derived damage cannot crit again or trigger a new direct-hit passive/chain.
            c.damage(target,damage*CHAIN_RATIO,PHYSICAL,false);
            AbilityVisuals.burst(c,target.position().add(0,.4,0),.45);
        }
    }
}
