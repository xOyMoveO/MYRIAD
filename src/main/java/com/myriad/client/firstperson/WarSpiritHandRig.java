package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** First-person grip pivots in raw item-model space. The dao's silver edge is model +X. */
public final class WarSpiritHandRig {
    private static final Vec3 RIGHT=new Vec3(1,0,0), UP=new Vec3(0,1,0);
    private static final float DAO_SCALE=.77f*1.20f, BELL_SCALE=.60f;
    // Center of jade_loop_joint_2, the top of the jade ring (model origin is 8,8,8).
    private static final float BELL_GRIP_X=(7.8025f-8)/16, BELL_GRIP_Y=(30.12f-8)/16;

    private WarSpiritHandRig() {}

    public static Matrix4f item(WarSpiritHandMotion motion, double now, boolean war, boolean left, float equip) {
        var p=motion.pose(now);
        var aim=p.axis();
        // Hang the bell slightly forward of the fist so its shell clears the wrist in the idle view.
        var axis=mirror(war?aim:new Vec3(aim.x,aim.y,.23+.55*aim.z).normalize(),left);
        var side=perpendicular(RIGHT,axis);
        var face=side.cross(axis);
        double roll=left?-p.roll():p.roll();
        if(war && p.edge()>0) {
            Vec3 travel=mirror(motion.strikeTravel((31.585-8)/16*DAO_SCALE),left);
            double x=travel.dot(side), z=travel.dot(face);
            if(x*x+z*z>1e-9) {
                // A single-edged dao must lead with +X even on the reversed stroke.
                double lead=Math.atan2(-z,x);
                roll+=Math.IEEEremainder(lead-roll,2*Math.PI)*p.edge();
            }
        }
        var hand=war?p.grip():p.grip().add(-.10,.84,-.70);
        var item=basis(StaffHandRig.lowered(hand,left,equip),twist(side,face,roll),axis).scale(war?DAO_SCALE:BELL_SCALE);
        // Rotate about the ring itself, not the original model's lower handle.
        return war?item:item.translate(-BELL_GRIP_X,-BELL_GRIP_Y,0);
    }

    public static Matrix4f arm(WarSpiritHandMotion.Pose pose, Matrix4f item,
                               boolean war, boolean left, boolean off, boolean slim, float equip) {
        boolean armLeft=left!=off;
        var hand=off?StaffHandRig.lowered(pose.palm(),left,equip):palm(item,war);
        var shoulder=new Vec3(armLeft?-.88:.88,-.87,-.17);
        Vec3 axis=hand.subtract(shoulder).normalize(), side=perpendicular(RIGHT,axis), face=side.cross(axis);
        double roll=off?pose.palmRoll()*(left?-1:1):
                .71*(item.m00()*face.x+item.m01()*face.y+item.m02()*face.z);
        return basis(hand,twist(side,face,roll),axis).scale(.90f,1,.90f).mul(NightDaggerRig.arm(slim,armLeft));
    }

    private static Vec3 palm(Matrix4f item,boolean war) {
        var p=item.transformPosition(war?0:BELL_GRIP_X,war?0:BELL_GRIP_Y,0,new Vector3f());
        return new Vec3(p.x,p.y,p.z);
    }

    private static Vec3 twist(Vec3 side,Vec3 face,double roll) {
        return side.scale(Math.cos(roll)).subtract(face.scale(Math.sin(roll)));
    }
    private static Vec3 perpendicular(Vec3 hint, Vec3 axis) {
        var p=hint.subtract(axis.scale(hint.dot(axis)));
        return (p.lengthSqr()<1e-6?UP.subtract(axis.scale(axis.y)):p).normalize();
    }
    private static Vec3 mirror(Vec3 v,boolean left) {return left?v.multiply(-1,1,1):v;}
    private static Matrix4f basis(Vec3 at,Vec3 x,Vec3 y) {
        return new Matrix4f().setColumn(0,column(x,0)).setColumn(1,column(y,0))
                .setColumn(2,column(x.cross(y),0)).setColumn(3,column(at,1));
    }
    private static Vector4f column(Vec3 v,float w) {return new Vector4f((float)v.x,(float)v.y,(float)v.z,w);}
}
