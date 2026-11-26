package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Reflect a right-authored view across the camera while preserving model winding and the cutting edge. */
final class HandedView {
    private HandedView() {}
    static Matrix4f arm(Matrix4f right, boolean left) {
        return left ? new Matrix4f().scaling(-1,1,1).mul(right).scale(-1,1,1) : right;
    }
    static Matrix4f weapon(Matrix4f right, boolean left) {
        return left ? new Matrix4f().scaling(-1,1,1).mul(right).scale(1,1,-1) : right;
    }
    static List<Quad> effects(List<Quad> geometry, boolean left) {
        if (!left) return geometry;
        return geometry.stream().map(q -> new Quad(mirror(q.b()),mirror(q.a()),mirror(q.d()),mirror(q.c()),
                q.color(),q.alpha(),q.material(),q.reverseV(),q.endU(),q.startU(),q.startV(),q.endV())).toList();
    }
    private static Vec3 mirror(Vec3 point) { return new Vec3(-point.x,point.y,point.z); }
}
