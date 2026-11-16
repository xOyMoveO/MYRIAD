package com.myriad.game;

import com.myriad.character.skills.yike.YikeWeaponAnimation;
import com.myriad.character.skills.yike.YikeWeaponAnimation.Controller;
import com.myriad.character.skills.yike.YikeWeaponAnimation.Snapshot;
import net.minecraft.gametest.framework.GameTestHelper;

/** Checks the same pure pose and snapshot controller used by both held-item views. */
final class YikeWeaponAnimationChecks {
    static void run(GameTestHelper h){
        var unlock=YikeWeaponAnimation.target(new Snapshot(0,4,.12,1));
        h.assertTrue(unlock.latch()>0&&unlock.petal0()==0&&unlock.ringSlide()==0,"Mechanical unlock precedes petals and focus-ring travel");
        var petals=YikeWeaponAnimation.target(new Snapshot(0,4,.5,2));
        h.assertTrue(petals.petal0()>0&&petals.ringSlide()==0&&petals.coreReady()==0,"Petals separate before ring/core alignment");
        var focus=YikeWeaponAnimation.target(new Snapshot(0,4,.85,3));
        h.assertTrue(focus.petal0()==30&&focus.ringSlide()>0&&focus.coreReady()>0,"Forward focus and core alignment finish after petal separation");
        var fixed=YikeWeaponAnimation.target(new Snapshot(1,-1,0,4));
        h.assertTrue(fixed.petal0()==30&&fixed.ringSlide()==1.35&&fixed.coreReady()==1&&fixed.aimBlend()==1,"Confirmed fixed posture uses the delivered local dimensions");
        for(double p:new double[]{0,.1,.4,.8,1}){
            var mobile=YikeWeaponAnimation.target(new Snapshot(0,5,p,5));
            h.assertTrue(mobile.petal0()==0&&mobile.petal1()<=16.5&&mobile.petal2()<=16.5,"Mobile upper petal remains folded during the whole deployment windup");
        }
        var mobile=YikeWeaponAnimation.target(new Snapshot(2,-1,0,6));
        h.assertTrue(mobile.petal0()==0&&mobile.petal1()==16.5&&mobile.ringSlide()==.7425,"Mobile shape has two half-open side petals and shorter focus travel");
        Controller c=new Controller();c.accept(new Snapshot(0,-1,0,0),0);c.accept(new Snapshot(0,4,.4,1),1);
        var middle=c.pose(1.5);var settled=c.pose(2);
        h.assertTrue(middle.petal0()>0&&middle.petal0()<settled.petal0(),"Partial ticks interpolate acknowledged geometry");
        c.accept(new Snapshot(0,4,.4,2),2);
        h.assertTrue(c.pose(3).equals(settled)&&c.pose(4).equals(settled),"Paused authoritative progress never continues deployment on the client clock");
        c.accept(new Snapshot(0,4,.43,4),4);
        h.assertTrue(c.pose(4).equals(settled)&&c.pose(5).petal0()>settled.petal0(),"A slower next progress sample preserves continuity and completed progress");
        var beforeCancel=c.pose(5);c.accept(new Snapshot(0,-1,0,5),5);
        h.assertTrue(c.pose(5).equals(beforeCancel)&&c.pose(7).petal0()<beforeCancel.petal0(),"Cancelled deployment reverses from its visible pose without snapping or finishing the windup");
        c.accept(new Snapshot(0,-1,0,6),6);
        h.assertTrue(c.pose(9).equals(YikeWeaponAnimation.CLOSED),"Repeated idle snapshots do not restart or extend the four-tick close");
        Controller shot=new Controller(),duplicate=new Controller();
        for(var controller:new Controller[]{shot,duplicate}){controller.accept(new Snapshot(1,-1,0,10),10);controller.fire(100,11);}
        duplicate.fire(100,12);
        h.assertTrue(shot.pose(12).equals(duplicate.pose(12))&&shot.pose(12).recoil()<0,"Only a new committed muzzle event produces recoil; duplicate packets do not replay it");
        Controller cancelledQ=new Controller();cancelledQ.accept(new Snapshot(0,2,.8,10),10);cancelledQ.accept(new Snapshot(0,-1,0,11),11);
        h.assertTrue(cancelledQ.pose(11).recoil()==0&&cancelledQ.pose(12).petal0()==0,"Cancelled Q/preparation without a committed event never produces firing recoil");
        h.assertTrue(shot.pose(15).recoil()==0,"The recoil recovers after four real ticks");
        shot.clear();h.assertTrue(shot.pose(12).equals(YikeWeaponAnimation.CLOSED),"World/role/death cleanup removes pose and recoil immediately");
        Controller stale=new Controller();stale.accept(new Snapshot(1,-1,0,1),1);
        h.assertTrue(stale.pose(7).equals(YikeWeaponAnimation.CLOSED),"Missing owner snapshots expire rather than leaving an open weapon");
        for(double[] axis:new double[][]{{0,0,-1},{0,1,0},{.35,-.8,-.48},{1,0,0}}){
            double norm=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);
            var right=YikeWeaponAnimation.gripRight(axis[0],axis[1],axis[2]);
            double dot=(right[0]*axis[0]+right[1]*axis[1]+right[2]*axis[2])/norm;
            double length=right[0]*right[0]+right[1]*right[1]+right[2]*right[2];
            h.assertTrue(Double.isFinite(length)&&Math.abs(length-1)<1E-8&&Math.abs(dot)<1E-8,"Grip basis is finite and orthogonal for horizontal/up/down/90-degree offset aims");
        }
        var camera=YikeWeaponAnimation.screenRight(0,0,45);
        h.assertTrue(Double.isFinite(camera[0])&&Math.abs(camera[0]*camera[0]+camera[2]*camera[2]-1)<1E-8,"Vertical camera retains a finite yaw-based right vector");
        System.out.println("[MYRIAD-YIKE-WEAPON] PASS: staged fixed/mobile poses, confirmed progress, pause/slow/cancel, recoil, cleanup and view bases");
    }
}
