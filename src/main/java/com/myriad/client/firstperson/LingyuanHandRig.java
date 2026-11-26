package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** The real jade sword has its grip on (8,8,8), +Y tip and +/-X cutting edges. */
final class LingyuanHandRig {
    private static final float SCALE = .81f * 1.20f;
    private static final double TIP = (27.3-8)/16;
    private static final Vec3 RIGHT = new Vec3(1,0,0);
    private static final Vec3 UP = new Vec3(0,1,0);

    private LingyuanHandRig() {}

    static Matrix4f sword(LingyuanHandMotion m, double now, boolean left, float equip) {
        var p=m.pose(now);
        Vec3 axis=p.axis().normalize();
        Vec3 across=perpendicular(RIGHT,axis), face=across.cross(axis);
        double roll=-.93;
        double weight=m.edgeWeight(now), t=m.edgeSample(now);
        if (weight>0) {
            Vec3 motion=tip(m.pose(t+.055)).subtract(tip(m.pose(t-.055)));
            if (motion.lengthSqr()>1e-7) {
                double angle=Math.atan2(-motion.dot(face),motion.dot(across));
                // A double-edged blade can lead with either edge; take the shorter turn.
                roll+=Math.IEEEremainder(angle-roll,Math.PI)*weight;
            }
        }
        across=across.scale(Math.cos(roll)).subtract(face.scale(Math.sin(roll)));
        return basis(StaffHandRig.lowered(p.grip(),left,equip),mirror(across,left),mirror(axis,left)).scale(SCALE);
    }

    static Matrix4f arm(LingyuanHandMotion.Pose p, boolean left, boolean off, boolean slim, float equip,
                        Matrix4f weapon) {
        boolean armIsLeft=left!=off;
        var palm=StaffHandRig.lowered(off?p.palm():p.grip(),left,equip);
        var shoulder=new Vec3(armIsLeft?-.85:.85,-.85,-.14);
        var axis=palm.subtract(shoulder).normalize();
        var across=perpendicular(RIGHT,axis);
        var side=axis.cross(across);
        double roll=off?p.palmRoll()*(left?-1:1):
                .56*(weapon.m00()*side.x+weapon.m01()*side.y+weapon.m02()*side.z)/SCALE;
        across=across.scale(Math.cos(roll)).add(side.scale(Math.sin(roll)));
        return basis(palm,across,axis).scale(.9f,1,.9f).mul(NightDaggerRig.arm(slim,armIsLeft));
    }

    private static Vec3 tip(LingyuanHandMotion.Pose p) { return p.grip().add(p.axis().normalize().scale(SCALE*TIP)); }
    private static Vec3 mirror(Vec3 v,boolean left){return left?v.multiply(-1,1,1):v;}
    private static Vec3 perpendicular(Vec3 hint,Vec3 axis){
        var n=hint.subtract(axis.scale(hint.dot(axis)));
        return (n.lengthSqr()<1e-7?UP.subtract(axis.scale(axis.y)):n).normalize();
    }
    private static Matrix4f basis(Vec3 point,Vec3 x,Vec3 y){
        return new Matrix4f().setColumn(0,c(x,0)).setColumn(1,c(y,0))
                .setColumn(2,c(x.cross(y),0)).setColumn(3,c(point,1));
    }
    private static Vector4f c(Vec3 v,float w){return new Vector4f((float)v.x,(float)v.y,(float)v.z,w);}
}
