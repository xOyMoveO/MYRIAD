package com.myriad.character.ability;

import com.myriad.combat.WindWallSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Direction and obstruction boundary shared by independently aimed skills. */
public final class AbilityGeometry {
    private AbilityGeometry() {}
    /** Unit directions rotate by at most radians, including the opposite-direction case. */
    public static Vec3 turnToward(Vec3 from, Vec3 to, double radians) {
        double dot = Math.clamp(from.dot(to), -1, 1);
        double angle = Math.acos(dot);
        if (angle <= radians) return to;
        Vec3 axis = to.subtract(from.scale(dot));
        if (axis.lengthSqr() < 1e-8) {
            axis = from.cross(Math.abs(from.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return from.scale(Math.cos(radians)).add(axis.normalize().scale(Math.sin(radians))).normalize();
    }
    /** Clip to the first solid collider or hostile wind wall; allied walls stay transparent. */
    public static Vec3 clip(ServerPlayer p,Vec3 a,Vec3 b){
        BlockHitResult hit=p.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 end=hit.getType()==HitResult.Type.BLOCK?hit.getLocation():b;
        Vec3 wall=WindWallSystem.firstEnemyHit(p,a,end);return wall==null?end:wall;
    }
}
