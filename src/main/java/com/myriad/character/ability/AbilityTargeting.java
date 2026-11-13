package com.myriad.character.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Area targets need support; movement targets additionally need room for the player. */
public final class AbilityTargeting {
    private AbilityTargeting() { }
    public static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static boolean inRange(Vec3 origin,Vec3 point,double range){return finite(point)&&origin.distanceToSqr(point)<=range*range+.01;}
    public static Vec3 clampPrediction(Vec3 position,Vec3 endpoint,Vec3 velocity){
        Vec3 remaining=endpoint.subtract(position);
        if(!finite(remaining)||!finite(velocity)||remaining.dot(velocity)<=0||remaining.lengthSqr()<.0004)return Vec3.ZERO;
        return velocity.length()>remaining.length()?remaining:velocity;
    }
    public static Vec3 ground(ServerPlayer p,double range,boolean landing){
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        var hit=p.level().clip(new ClipContext(eye,eye.add(look.scale(range+1.7)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        Vec3 at;
        if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==Direction.UP&&inRange(p.position(),hit.getLocation(),range))at=hit.getLocation().add(0,.025,0);
        else {
            Vec3 flat=look.multiply(1,0,1).normalize();if(flat.lengthSqr()<.01||look.y>.5)return null;
            Vec3 probe=hit.getType()==HitResult.Type.BLOCK
                    ?hit.getLocation().add(hit.getDirection().getStepX()*.45,hit.getDirection().getStepY()*.45,hit.getDirection().getStepZ()*.45)
                    :p.position().add(flat.scale(range));
            if(probe.subtract(p.position()).multiply(1,0,1).length()>range)probe=p.position().add(flat.scale(range));
            var floor=p.level().clip(new ClipContext(new Vec3(probe.x,p.getY()+2.5,probe.z),
                    new Vec3(probe.x,p.getY()-4,probe.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(floor.getType()!=HitResult.Type.BLOCK||floor.getDirection()!=Direction.UP)return null;
            at=floor.getLocation().add(0,.025,0);
        }
        return legalGround(p,at,range,landing)?at:null;
    }
    /** Recheck an already selected point at deferred commit without retargeting the player's aim. */
    public static boolean legalGround(ServerPlayer p,Vec3 at,double range,boolean landing){
        if(at==null||!inRange(p.position(),at,range)||!supported(p,at))return false;
        var occlusion=p.level().clip(new ClipContext(p.getEyePosition(),at.add(0,.1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(occlusion.getType()!=HitResult.Type.MISS&&occlusion.getLocation().distanceToSqr(at)>.12)return false;
        return !landing||canStand(p,at);
    }
    public static boolean supported(ServerPlayer p,Vec3 at){
        if(!finite(at)||!p.level().hasChunkAt(BlockPos.containing(at))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(at)))return false;
        var below=BlockPos.containing(at.add(0,-.08,0));var state=p.level().getBlockState(below);
        return state.getFluidState().isEmpty()&&!state.getCollisionShape(p.level(),below).isEmpty();
    }
    public static boolean canStand(ServerPlayer p,Vec3 at){return supported(p,at)
            &&p.level().noCollision(p,p.getBoundingBox().move(at.subtract(p.position())));}
    public static boolean clearSweep(ServerPlayer p,Vec3 step){return finite(step)
            &&p.level().hasChunkAt(BlockPos.containing(p.position().add(step)))
            &&p.level().getWorldBorder().isWithinBounds(BlockPos.containing(p.position().add(step)))
            &&p.level().noCollision(p,p.getBoundingBox().expandTowards(step));}
    public static boolean clearArc(ServerPlayer p,Vec3 endpoint,int ticks,double height){
        if(!canStand(p,endpoint))return false;Vec3 origin=p.position(),previous=origin;
        for(int i=1;i<=ticks;i++){double t=i/(double)ticks;
            Vec3 next=origin.lerp(endpoint,t).add(0,4*height*t*(1-t),0);
            if(!p.level().hasChunkAt(BlockPos.containing(next))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(next))
                    ||!p.level().noCollision(p,p.getBoundingBox().move(previous.subtract(origin)).expandTowards(next.subtract(previous))))return false;
            previous=next;
        }return true;
    }
    public static boolean clearSegment(ServerPlayer p,Vec3 endpoint){
        if(!finite(endpoint)||!canStand(p,endpoint))return false;
        Vec3 delta=endpoint.subtract(p.position());int steps=Math.max(1,(int)Math.ceil(delta.length()/.4));
        if(steps>80)return false;
        for(int i=1;i<=steps;i++){
            Vec3 at=p.position().add(delta.scale(i/(double)steps));
            if(!p.level().hasChunkAt(BlockPos.containing(at))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(at))
                    ||!p.level().noCollision(p,p.getBoundingBox().move(delta.scale((i-1.0)/steps)).expandTowards(delta.scale(1.0/steps))))return false;
        }return true;
    }
}
