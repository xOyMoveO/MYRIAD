package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared palm attachment for Tianxing's cosmetic first-person arm and actual item. */
public final class ThunderSwordRig {
    private static final float DEG=(float)(Math.PI/180);
    public static final float VIEW_SCALE=.74f;
    private ThunderSwordRig(){}
    public static boolean allowed(boolean mod,String role,boolean ownLivingFirstPerson,boolean mainSword){
        return mod&&"tianxing".equals(role)&&ownLivingFirstPerson&&mainSword;
    }
    public static Matrix4f wrist(ThunderSwordMotion.Pose p,float equip,boolean left){
        float side=left?-1:1;
        float e=Float.isFinite(equip)?Math.clamp(equip,0,1):0;
        return new Matrix4f().translation(side*(float)p.x(),(float)p.y()-.48f*e,(float)p.z())
                .rotateX((float)p.pitch()*DEG).rotateY(side*(float)p.yaw()*DEG)
                .rotateZ(side*(float)p.roll()*DEG).scale(VIEW_SCALE);
    }
    public static Matrix4f arm(boolean slim,boolean left){return NightDaggerRig.arm(slim,left);}
    public static Matrix4f blade(boolean left){
        var p=ThunderSwordMotion.IDLE;
        return blade(new ThunderSwordMotion.Frame(p,p,p,0,-1),left);
    }
    private static Matrix4f unturned(boolean left){return new Matrix4f().rotateZ((left?65:-65)*DEG).scale(1.25f);}
    public static Vector3f travel(ThunderSwordMotion.Frame f,boolean left){
        var tip=new Vector3f(0,1.40625f,0);
        var before=wrist(f.before(),0,left).mul(unturned(left)).transformPosition(new Vector3f(tip));
        return wrist(f.after(),0,left).mul(unturned(left)).transformPosition(tip).sub(before);
    }
    public static Matrix4f blade(ThunderSwordMotion.Frame f,boolean left){
        // Model +Y is the shaft, +/-X are the sharpened edges, Z is the broad-face normal.
        // Rotate only ABOUT the shaft: the handle and tip never leave their planned arc.
        var base=wrist(f.pose(),0,left).mul(unturned(left));
        var x=base.transformDirection(new Vector3f(1,0,0)).normalize();
        var z=base.transformDirection(new Vector3f(0,0,1)).normalize();
        var forward=new Vector3f(0,0,-1);
        double idle=Math.atan2(-forward.dot(z),forward.dot(x));
        var velocity=travel(f,left);
        double angle=idle;
        if(Math.pow(velocity.dot(x),2)+Math.pow(velocity.dot(z),2)>1E-10){
            double target=Math.atan2(-velocity.dot(z),velocity.dot(x));
            // Keep the sharpened +X edge leading; unwrap around this cut's side to avoid a roll seam.
            double reference=(f.cut()==0?!left:left)?0:Math.PI;
            target=reference+Math.IEEEremainder(target-reference,Math.PI*2);
            angle+=(target-idle)*f.cuttingWeight();
        }
        return new Matrix4f().rotateZ((left?65:-65)*DEG).rotateY((float)angle).scale(1.25f);
    }
}
