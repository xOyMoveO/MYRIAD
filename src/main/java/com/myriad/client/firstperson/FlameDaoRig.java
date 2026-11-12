package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A single wrist anchors the real skin palm and the long dao; +X is its ONLY sharp edge. */
public final class FlameDaoRig {
    private static final float DEG=(float)(Math.PI/180);
    public static final float VIEW_SCALE=.74f;
    private FlameDaoRig(){}
    public static boolean allowed(boolean mod,String role,boolean ownLivingFirstPerson,boolean mainDao){
        return mod&&"fenjue".equals(role)&&ownLivingFirstPerson&&mainDao;
    }
    public static Matrix4f wrist(FlameDaoMotion.Pose p,float equip,boolean left){
        float side=left?-1:1,e=Float.isFinite(equip)?Math.clamp(equip,0,1):0;
        // Keep the fist in the lower side of the view; let the blade arc do the visible work.
        return new Matrix4f().translation(side*(.23f+.38f*(float)p.x()),-.58f+.35f*(float)p.y()-.48f*e,(float)p.z())
                .rotateX((float)p.pitch()*DEG).rotateY(side*(float)p.yaw()*DEG)
                .rotateZ(side*(float)p.roll()*DEG).scale(VIEW_SCALE);
    }
    public static Matrix4f arm(boolean slim,boolean left){return NightDaggerRig.arm(slim,left);}
    public static Matrix4f hand(FlameDaoMotion.Pose p,float equip,boolean left){
        // The fist follows with a restrained wrist gesture; do not rotate the whole forearm
        // through the weapon's full cutting angle and then bend it back to the camera.
        return wrist(new FlameDaoMotion.Pose(p.x(),p.y(),p.z(),-10+(p.pitch()+10)*.20,
                p.yaw()*.35,54+(p.roll()-54)*.18),equip,left);
    }
    public static Vector3f forearmEnd(boolean left){
        // A view-model continuation, not an anatomical elbow exposed in the camera.
        return new Vector3f(left?-.85f:.85f,-1.55f,-.86f);
    }
    private static Matrix4f shaft(boolean left){return new Matrix4f().rotateZ((left?65:-65)*DEG).scale(1.16f);}
    public static Vector3f travel(FlameDaoMotion.Frame f,boolean left){
        var tip=new Vector3f(0,1.390625f,0);
        var before=wrist(f.before(),0,left).mul(shaft(left)).transformPosition(new Vector3f(tip));
        return wrist(f.after(),0,left).mul(shaft(left)).transformPosition(tip).sub(before);
    }
    private static double angle(FlameDaoMotion.Frame f,boolean left,Vector3f direction){
        var base=wrist(f.pose(),0,left).mul(shaft(left));
        var x=base.transformDirection(new Vector3f(1,0,0)).normalize();
        var z=base.transformDirection(new Vector3f(0,0,1)).normalize();
        return Math.atan2(-direction.dot(z),direction.dot(x));
    }
    public static double target(FlameDaoMotion.Frame f,boolean left){
        var velocity=travel(f,left);
        return angle(f,left,velocity.lengthSquared()>1E-12?velocity:new Vector3f(0,0,-1));
    }
    public static double turn(FlameDaoMotion.Frame f,boolean left){
        double idle=angle(f,left,new Vector3f(0,0,-1));
        if(f.cut()<0)return idle;
        double anchor=left?f.anchorLeft():f.anchorRight();
        double target=anchor+Math.IEEEremainder(target(f,left)-anchor,2*Math.PI);
        if(f.progress()<.20){
            double entry=left?f.entryLeft():f.entryRight();
            target=entry+Math.IEEEremainder(target-entry,2*Math.PI);
            return mix(entry,target,smooth(f.progress()/.20));
        }
        if(f.progress()<=.65)return target;
        idle=target+Math.IEEEremainder(idle-target,2*Math.PI);
        return mix(target,idle,smooth((f.progress()-.65)/.35));
    }
    public static Matrix4f blade(FlameDaoMotion.Frame f,boolean left){
        // Twist about the shaft only: palm stays fixed while the cutting edge follows its actual travel.
        return new Matrix4f().rotateZ((left?65:-65)*DEG).rotateY((float)turn(f,left)).scale(1.16f);
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
}
