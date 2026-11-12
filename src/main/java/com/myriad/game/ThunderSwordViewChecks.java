package com.myriad.game;

import com.myriad.client.firstperson.*;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Pure renderer math on the server: palm attachment and the three camera-space cuts. */
final class ThunderSwordViewChecks {
    static void verify(GameTestHelper h){
        h.assertTrue(ThunderSwordRig.allowed(true,"tianxing",true,true),"Own living Tianxing sword view is enabled");
        h.assertTrue(!ThunderSwordRig.allowed(false,"tianxing",true,true)&&!ThunderSwordRig.allowed(true,"yingxiao",true,true)
                &&!ThunderSwordRig.allowed(true,"tianxing",false,true)&&!ThunderSwordRig.allowed(true,"tianxing",true,false),"Mod/role/camera-life/item gates restore ordinary rendering");
        var motion=new ThunderSwordMotion();
        for(int cut=0;cut<3;cut++){
            double start=cut*20;motion.attack(start,20);
            h.assertTrue(motion.index()==cut,"Three accepted attacks cycle in order");
            for(int i=0;i<=120;i++)for(float equip:new float[]{0,.5f,1})for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
                var frame=motion.frame(start+motion.duration()*i/120);
                var wrist=ThunderSwordRig.wrist(frame.pose(),equip,left);
                var arm=new Matrix4f(wrist).mul(ThunderSwordRig.arm(slim,left));
                var blade=new Matrix4f(wrist).mul(ThunderSwordRig.blade(frame,left));
                var palm=arm.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0));
                h.assertTrue(palm.isFinite()&&palm.distance(blade.transformPosition(new Vector3f()))<1E-6,"Palm and handle stay attached in every cut/equip/skin/hand pose");
                h.assertTrue(wrist.determinant()>0&&blade.determinant()>0,"No reversed face winding or negative scale");
                if(frame.cuttingWeight()==1){
                    var shaft=blade.transformDirection(new Vector3f(0,1,0)).normalize();
                    var travel=ThunderSwordRig.travel(frame,left);
                    var transverse=new Vector3f(travel).sub(new Vector3f(shaft).mul(travel.dot(shaft))).normalize();
                    var edge=blade.transformDirection(new Vector3f(1,0,0)).normalize();
                    var normal=blade.transformDirection(new Vector3f(0,0,1)).normalize();
                    h.assertTrue(edge.dot(transverse)>.999,"Actual sharpened edge leads the cut, not broad sword face: "+edge.dot(transverse));
                    h.assertTrue(Math.abs(normal.dot(transverse))<.002,"Blade plane contains travel direction; no flat slap");
                }
            }
            var from=tip(motion.sample(start+motion.duration()*.22));var to=tip(motion.sample(start+motion.duration()*.62));
            float dx=to.x/-to.z-from.x/-from.z,dy=to.y/-to.z-from.y/-from.z;
            h.assertTrue(cut==0?dx>.5&&dy<-.35:cut==1?dx<-.5&&dy<-.35:dx<-.5&&Math.abs(dy)<.04,
                    "Projected tip follows diagonal/diagonal/horizontal: cut="+cut+" dx="+dx+" dy="+dy);
        }
        var idle=ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,0,false);
        var blade=new Matrix4f(idle).mul(ThunderSwordRig.blade(false));
        h.assertTrue(blade.transformDirection(new Vector3f(0,1,0)).normalize().y>.9,"Normal blade points up");
        for(boolean left:new boolean[]{false,true}){
            var b=ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,0,left).mul(ThunderSwordRig.blade(left));
            h.assertTrue(b.transformDirection(new Vector3f(1,0,0)).normalize().z<-.97,"Cutting edge faces forward rather than broad face");
        }
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            h.assertTrue(ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,invalid,false).isFinite(),"Invalid equip interpolation is safe");
        var before=motion.sample(41);motion.attack(41,20);
        h.assertTrue(motion.index()==0&&motion.sample(41).equals(before),"Fast next attack blends from current hand pose");
        motion.attack(200,20);h.assertTrue(motion.index()==0,"Long pause restarts first cut");
        motion.attack(10,20);h.assertTrue(motion.index()==0,"World tick rollback restarts first cut");
        motion.attack(Double.NaN,Double.NaN);h.assertTrue(motion.index()==0,"Invalid clock cannot poison animation");
        h.assertTrue(motion.sample(Double.POSITIVE_INFINITY).equals(ThunderSwordMotion.IDLE),"Invalid sample returns idle");
        motion.reset();h.assertTrue(motion.index()==-1&&motion.sample(12).equals(ThunderSwordMotion.IDLE),"Lifecycle reset drops combo state");
        for(double recovery:new double[]{1,20,100,Double.NaN}){
            motion.attack(0,recovery);h.assertTrue(motion.duration()>=4&&motion.duration()<=7,"Attack speed bounded cosmetic duration");
            h.assertTrue(motion.sample(motion.duration()).equals(ThunderSwordMotion.IDLE),"No lingering pose after completion");motion.reset();
        }
        System.out.println("[MYRIAD-THUNDER-FIRSTPERSON] PASS: 4356 palm/grip poses, forward idle edge, actual cutting edge aligned with all three swing velocities (no broad-face slap), projected paths, left/slim, lifecycle/clock/duration");
    }
    private static Vector3f tip(ThunderSwordMotion.Pose p){
        return ThunderSwordRig.wrist(p,0,false).mul(ThunderSwordRig.blade(false)).transformPosition(new Vector3f(0,1.40625f,0));
    }
}
