package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Supported, collision-free landings only, within the target's rear 80-degree sector. */
public final class ShadowBehindLanding {
    private ShadowBehindLanding(){}
    public static Vec3 find(ServerPlayer p,LivingEntity target){
        Vec3 facing=target.getLookAngle().multiply(1,0,1).normalize();
        if(facing.lengthSqr()<1E-8)facing=Vec3.directionFromRotation(0,target.getYRot());
        Vec3 back=facing.scale(-1),side=new Vec3(-back.z,0,back.x);
        double base=(p.getBbWidth()+target.getBbWidth())*.5+.42;
        for(double extra:new double[]{0,.3,.6})for(double angle:new double[]{0,.25,-.25,.6,-.6}){
            Vec3 axis=back.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            Vec3 candidate=target.position().add(axis.scale(base+extra));
            var ground=p.level().clip(new ClipContext(candidate.add(0,.8,0),candidate.add(0,-1.3,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(ground.getType()!=HitResult.Type.BLOCK||ground.getDirection()!=net.minecraft.core.Direction.UP)continue;
            Vec3 at=ground.getLocation().add(0,.025,0);
            if(!AbilityTargeting.canStand(p,at))continue;
            if(p.getBoundingBox().move(at.subtract(p.position())).intersects(target.getBoundingBox().inflate(.05)))continue;
            // Behind a column is not an acceptable stab landing: the victim must be reachable from it.
            var line=p.level().clip(new ClipContext(at.add(0,p.getEyeHeight(),0),target.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(line.getType()==HitResult.Type.MISS)return at;
        }
        return null;
    }
}
