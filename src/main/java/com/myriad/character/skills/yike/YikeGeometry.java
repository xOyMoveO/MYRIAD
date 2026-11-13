package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.SkillTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.List;

/** Three-dimensional volume and centre visibility, independent of the caster's later sightline. */
public final class YikeGeometry {
    private YikeGeometry(){}
    public static boolean visible(Cast c,Vec3 from,Vec3 to){
        return c.player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player)).getType()==HitResult.Type.MISS;
    }
    public static List<LivingEntity> sphere(Cast c,Vec3 center,double radius){
        return c.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),t->SkillTargets.enemy(c.player,t)
            &&t.getBoundingBox().distanceToSqr(center)<=radius*radius&&visible(c,center,t.getBoundingBox().getCenter()));
    }
    public static Vec3 end(Cast c,Vec3 from,Vec3 direction,double range){
        Vec3 to=from.add(direction.normalize().scale(range));
        var wall=c.player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
        Vec3 wind=com.myriad.combat.WindWallSystem.firstEnemyHit(c.player,from,to);
        if(wall.getType()!=HitResult.Type.MISS)to=wall.getLocation();
        if(wind!=null&&wind.distanceToSqr(from)<to.distanceToSqr(from))to=wind;return to;
    }
    public static Vec3 rotate(Vec3 from,Vec3 toward,double radians){
        Vec3 a=from.normalize(),b=toward.normalize();double dot=Math.clamp(a.dot(b),-1,1),angle=Math.acos(dot);
        if(angle<=radians||angle<1E-7)return b;
        Vec3 tangent=b.subtract(a.scale(dot));
        if(tangent.lengthSqr()<1E-9){tangent=a.cross(new Vec3(0,1,0));if(tangent.lengthSqr()<1E-9)tangent=a.cross(new Vec3(1,0,0));}
        return a.scale(Math.cos(radians)).add(tangent.normalize().scale(Math.sin(radians))).normalize();
    }
}
