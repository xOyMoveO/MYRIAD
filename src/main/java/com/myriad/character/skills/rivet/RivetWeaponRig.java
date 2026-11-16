package com.myriad.character.skills.rivet;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Pure camera-space rig shared by the renderer and offline skin/item projection checks. */
public final class RivetWeaponRig {
    public static final float ITEM_SCALE=.60f,HAND_SCALE=.82f,GRIP_Y=5.5f;
    private static final float DEG=(float)(Math.PI/180);
    private RivetWeaponRig(){}
    private static Matrix4f frame(RivetWeaponMotion.Pose pose,boolean left){
        var m=new Matrix4f().translation((float)pose.x(),(float)pose.y(),(float)pose.z())
                .rotateX((float)pose.pitch()*DEG).rotateY((float)pose.yaw()*DEG).rotateZ((float)pose.roll()*DEG);
        return left?mirror(m):m;
    }
    /** Reflect the grip and shaft, then reverse thickness to retain positive winding and normal culling. */
    private static Matrix4f mirror(Matrix4f m){
        Vector4f x=m.getColumn(0,new Vector4f()),y=m.getColumn(1,new Vector4f()),z=m.getColumn(2,new Vector4f()),p=m.getColumn(3,new Vector4f());
        x.x=-x.x;y.x=-y.x;z.y=-z.y;z.z=-z.z;p.x=-p.x;
        return new Matrix4f().setColumn(0,x).setColumn(1,y).setColumn(2,z).setColumn(3,p);
    }
    public static Vector3f grip(RivetWeaponMotion.Pose pose,boolean left){
        return frame(pose,left).transformPosition(new Vector3f());
    }
    /** Matrix passed to ItemDisplayContext.NONE, before ItemRenderer's built-in (-.5,-.5,-.5). */
    public static Matrix4f item(RivetWeaponMotion.Pose pose,boolean left){
        return frame(pose,left).scale(ITEM_SCALE).translate(.5f,.5f-GRIP_Y/16,.5f);
    }
    /** Raw item JSON pixel coordinates -> camera blocks, including the real ItemRenderer centering. */
    public static Matrix4f rawItem(RivetWeaponMotion.Pose pose,boolean left){
        return item(pose,left).translate(-.5f,-.5f,-.5f).scale(1f/16);
    }
    public static Vector3f skinPalm(boolean slim,boolean physicalLeft){
        return new Vector3f((slim?.5f:1f)*(physicalLeft?1:-1),8,0);
    }
    private static float ease(float value){float t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    /** Skin +Y reaches the fingers; the proximal forearm points down/out to the camera, not along the shaft. */
    public static Matrix4f hand(RivetWeaponMotion.Pose pose,boolean slim,boolean left){
        Vector3f palm=grip(pose,false);
        var elbow=new Vector3f(1.05f,-1.05f,-.12f);
        // Start with the handle across skin X, rather than a spear-like continuation through the wrist.
        var shaft=frame(pose,false).transformDirection(new Vector3f(0,1,0)).normalize();
        var towardFingers=new Vector3f(palm).sub(elbow);
        var y=new Vector3f(towardFingers).fma(-towardFingers.dot(shaft),shaft).normalize();
        // During a downward strike a mathematically perpendicular wrist can point the elbow
        // sideways into the scene. Permit a bounded wrist flex so the sleeve returns to the body.
        float body=.55f*Math.max(ease((.20f-y.y)/.20f),ease((y.z+.45f)/.45f));
        y.lerp(new Vector3f(towardFingers).normalize(),body).normalize();
        float along=Math.clamp(y.dot(shaft),-.57357645f,.57357645f); // at most 35 degrees from neutral
        y.fma(-y.dot(shaft),shaft).normalize().mul((float)Math.sqrt(1-along*along)).fma(along,shaft);
        var x=new Vector3f(shaft).fma(-shaft.dot(y),y).normalize();
        var z=new Vector3f(x).cross(y).normalize();
        var at=new Matrix4f().setColumn(0,new Vector4f(x,0)).setColumn(1,new Vector4f(y,0))
                .setColumn(2,new Vector4f(z,0)).setColumn(3,new Vector4f(palm,1));
        if(left)at=mirror(at);
        var centre=skinPalm(slim,left).mul(1f/16);
        return at.scale(HAND_SCALE).translate(-centre.x,-centre.y,-centre.z);
    }
    /** RivetArmModel skin vertex pixels, including its short proximal continuation, -> camera blocks. */
    public static Matrix4f rawHand(RivetWeaponMotion.Pose pose,boolean slim,boolean left){
        return hand(pose,slim,left).scale(1f/16);
    }
}
