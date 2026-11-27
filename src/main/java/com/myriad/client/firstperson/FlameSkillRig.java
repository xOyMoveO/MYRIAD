package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One common palm/weapon anchor, including an actual second-hand contact on the lower hilt. */
public final class FlameSkillRig {
    private FlameSkillRig(){}
    public static Matrix4f main(FlameSkillMotion.Frame f,boolean left){return FlameDaoRig.wrist(f.main(),0,left);}
    static double turn(FlameSkillMotion.Frame f,boolean left){
        double rest=FlameDaoRig.restTurn(f.main(),left);
        return rest+f.bladeGesture()*Math.IEEEremainder(Math.toRadians(left?-90:90)-rest,2*Math.PI);
    }
    public static Matrix4f blade(FlameSkillMotion.Frame f,boolean left){
        return new Matrix4f().rotateZ((float)Math.toRadians((left?-1:1)*-65))
                .rotateY((float)turn(f,left)).scale(FlameDaoRig.BLADE_SCALE);
    }
    public static Matrix4f off(FlameSkillMotion.Frame f,boolean left){
        var palm=FlameDaoRig.palm(f.off(),0,!left);
        if(f.supportGrip()>0){
            var grip=main(f,left).mul(blade(f,left)).transformPosition(new Vector3f(0,-.20f,0));
            // Same rigid grip throughout airborne ready and contact, easing out only on recovery.
            palm.lerp(grip,(float)f.supportGrip());
        }
        return FlameDaoRig.handAt(palm,!left);
    }
}
