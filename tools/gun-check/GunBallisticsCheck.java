package com.myriad.check;

import com.myriad.character.skills.shenli.ShenLiBallistics;
import net.minecraft.world.phys.Vec3;

public final class GunBallisticsCheck {
    public static void main(String[] args) {
        Vec3[] views={new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(0,-1,0),
                new Vec3(1,.99,0),new Vec3(-1,.01,-1),new Vec3(0,.9999,.001)};
        int checked=0;
        for(Vec3 view:views) for(int angle=0;angle<=100;angle++) for(int az=0;az<=24;az++) {
            double sample=angle/100.0;
            Vec3 result=ShenLiBallistics.direction(view,false,sample,az/24.0);
            near(result.length(),1,1e-10,"normalization");
            double deviation=Math.toDegrees(Math.acos(Math.clamp(view.normalize().dot(result),-1,1)));
            near(deviation,2+8*sample,1e-8,"cone deviation including vertical aim");
            near(ShenLiBallistics.direction(view,true,sample,az/24.0).distanceTo(view.normalize()),0,1e-10,"aim has zero spread");
            checked++;
        }
        near(ShenLiBallistics.AIM_SPEED*20,50,1e-10,"speed per second");
        near(ShenLiBallistics.AIM_RANGE,22,1e-10,"range");
        System.out.println("PASS: "+checked+" aim/spread cases, including vertical views, plus range/speed.");
    }
    private static void near(double actual,double expected,double tolerance,String message) {
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)throw new AssertionError(message+": "+actual+" != "+expected);
    }
}
