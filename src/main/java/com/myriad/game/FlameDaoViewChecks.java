package com.myriad.game;

import com.myriad.client.firstperson.*;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real view-rig maths, not a Minecraft client visual or server hit-timing test. */
final class FlameDaoViewChecks {
    static void verify(GameTestHelper h){
        h.assertTrue(FlameDaoRig.allowed(true,"fenjue",true,true),"Only own living Fenjue dao view enabled");
        h.assertTrue(!FlameDaoRig.allowed(false,"fenjue",true,true)&&!FlameDaoRig.allowed(true,"tianxing",true,true)
                &&!FlameDaoRig.allowed(true,"fenjue",false,true)&&!FlameDaoRig.allowed(true,"fenjue",true,false),"Role/mod/camera-life/item gates");
        var motion=new FlameDaoMotion();int poses=0;
        for(int cut=0;cut<3;cut++){
            double start=cut*22;h.assertTrue(motion.attack(start,20)&&motion.index()==cut,"Three accepted cuts cycle");
            h.assertTrue(!motion.attack(start,20)&&motion.index()==cut,"Same-tick duplicate cannot advance combo");
            for(int i=0;i<=120;i++)for(float equip:new float[]{0,.5f,1})for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
                var frame=motion.frame(start+motion.duration()*i/120);
                var wrist=FlameDaoRig.wrist(frame.pose(),equip,left);
                var arm=FlameDaoRig.hand(frame.pose(),equip,left).mul(FlameDaoRig.arm(slim,left));
                var blade=new Matrix4f(wrist).mul(FlameDaoRig.blade(frame,left));
                var palm=arm.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0));
                var end=FlameDaoRig.forearmEnd(left);
                h.assertTrue(end.isFinite()&&end.y/-end.z<-Math.tan(Math.toRadians(55)),"Forearm continues below even a 110-degree vertical field, no visible elbow/end cap");
                h.assertTrue(palm.isFinite()&&palm.distance(blade.transformPosition(new Vector3f()))<1E-6,"Fist/handle remain attached in all skin/equip/hand variants");
                h.assertTrue(blade.isFinite()&&wrist.determinant()>0&&blade.determinant()>0,"Finite rotations, no mirror winding flip");
                if(frame.progress()>=.21&&frame.progress()<=.64){
                    var shaft=blade.transformDirection(new Vector3f(0,1,0)).normalize();
                    var travel=FlameDaoRig.travel(frame,left);
                    var transverse=new Vector3f(travel).sub(new Vector3f(shaft).mul(travel.dot(shaft))).normalize();
                    var edge=blade.transformDirection(new Vector3f(1,0,0)).normalize();
                    var normal=blade.transformDirection(new Vector3f(0,0,1)).normalize();
                    h.assertTrue(edge.dot(transverse)>.999,"Single sharp +X edge leads actual travel, not broad-face slap");
                    h.assertTrue(Math.abs(normal.dot(transverse))<.002,"Blade plane contains cutting direction");
                }
                poses++;
            }
            var from=tip(motion.frame(start+motion.duration()*.20));var to=tip(motion.frame(start+motion.duration()*.65));
            float dx=to.x/-to.z-from.x/-from.z,dy=to.y/-to.z-from.y/-from.z;
            h.assertTrue(cut==0?dx<-.5&&dy<-.35:cut==1?dx>.5&&dy>.35:dx<-.5&&Math.abs(dy)<.05,
                    "Projected descending / rising / horizontal cut paths: "+cut+" dx="+dx+" dy="+dy);
        }
        for(boolean left:new boolean[]{false,true}){
            var frame=new FlameDaoMotion().frame(0);var b=FlameDaoRig.wrist(frame.pose(),0,left).mul(FlameDaoRig.blade(frame,left));
            h.assertTrue(b.transformDirection(new Vector3f(1,0,0)).normalize().z<-.97,"Idle edge faces forward");
        }
        motion.reset();motion.attack(0,20);
        var before=motion.frame(4);var pose=before.pose();
        var right=FlameDaoRig.wrist(pose,0,false).mul(FlameDaoRig.blade(before,false));
        motion.attack(4,20);var after=motion.frame(4);
        var next=FlameDaoRig.wrist(after.pose(),0,false).mul(FlameDaoRig.blade(after,false));
        h.assertTrue(after.pose().equals(pose)&&right.equals(next,1E-5f),"Fast restart preserves exact wrist and edge twist");
        motion.attack(200,20);h.assertTrue(motion.index()==0,"Long pause starts first cut");
        motion.attack(10,20);h.assertTrue(motion.index()==0,"Tick rollback starts first cut");
        h.assertTrue(!motion.attack(Double.NaN,20)&&motion.sample(Double.POSITIVE_INFINITY).equals(FlameDaoMotion.IDLE),"Invalid clock is safe");
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            h.assertTrue(FlameDaoRig.wrist(FlameDaoMotion.IDLE,bad,false).isFinite(),"Invalid equip interpolation safe");
        for(double recovery:new double[]{1,20,100,Double.NaN}){
            motion.reset();motion.attack(0,recovery);
            h.assertTrue(motion.duration()>=4&&motion.duration()<=7.5,"Cosmetic duration follows bounded attack speed");
            h.assertTrue(motion.sample(motion.duration()).equals(FlameDaoMotion.IDLE),"Completion returns idle");
        }
        motion.reset();h.assertTrue(motion.index()==-1&&motion.frame(100).cut()==-1,"Reset clears combo");
        System.out.println("[MYRIAD-FENJUE-FIRSTPERSON] PASS: "+poses+" skin/equip/hand palm attachments, real sharp-edge/travel alignment; descending/rising/horizontal paths, continuity/clock/lifecycle and bounded duration");
    }
    private static Vector3f tip(FlameDaoMotion.Frame f){
        return FlameDaoRig.wrist(f.pose(),0,false).mul(FlameDaoRig.blade(f,false)).transformPosition(new Vector3f(0,1.390625f,0));
    }
}
