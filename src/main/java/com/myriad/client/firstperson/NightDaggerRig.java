package com.myriad.client.firstperson;

import org.joml.Matrix4f;

/** Shared by the renderer and offline pose/grip checks. Units are camera-space blocks. */
public final class NightDaggerRig {
    public static final float VIEW_SCALE=.74f;
    private static final float DEG=(float)(Math.PI/180);
    private NightDaggerRig() { }
    public static Matrix4f wrist(NightDaggerMotion.Pose p,boolean left) {
        float side=left ? -1 : 1;
        return new Matrix4f().translation((float)p.x()*side,(float)p.y(),(float)p.z())
                .rotateX((float)p.pitch()*DEG).rotateY((float)p.yaw()*side*DEG)
                .rotateZ((float)p.roll()*side*DEG).rotateY((float)p.wrist()*side*DEG).scale(VIEW_SCALE);
    }
    public static Matrix4f arm(boolean slim,boolean left) {
        return new Matrix4f().translation((slim ? .5f : 1f)/16f*(left ? -1 : 1),-8f/16f,0);
    }
    public static Matrix4f blade(boolean left) {
        return blade(left,0);
    }
    public static Matrix4f blade(boolean left,double grip) {
        float side=left ? -1 : 1;
        // Proper rotations instead of a negative scale: item face winding/culling stays correct.
        return new Matrix4f().rotateZ(side*(90-(float)grip)*DEG).rotateY(side*55*DEG).scale(.78f);
    }
}
