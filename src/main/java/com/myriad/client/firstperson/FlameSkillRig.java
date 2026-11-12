package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.network.FlameSkillViewPayload.*;

/** One common palm/weapon anchor, including an actual second-hand contact on the lower hilt. */
public final class FlameSkillRig {
    private FlameSkillRig(){}
    public static Matrix4f main(FlameSkillMotion.Frame f,boolean left){return FlameDaoRig.wrist(f.main(),0,left);}
    public static Matrix4f blade(FlameSkillMotion.Frame f,boolean left){
        return new Matrix4f().rotateZ((float)Math.toRadians((left?-1:1)*-65))
                .rotateY((float)Math.toRadians(left?-90:90)).scale(1.16f);
    }
    public static Matrix4f off(FlameSkillMotion.Frame f,boolean left){
        var matrix=FlameDaoRig.hand(f.off(),0,!left);
        if(f.action()==SEA_FLIGHT||f.action()==SEA_LAND){
            var grip=main(f,left).mul(blade(f,left)).transformPosition(new Vector3f(0,-.20f,0));
            // Same rigid grip throughout airborne ready and contact, easing out only on recovery.
            float weight=f.action()==SEA_LAND?(float)(1-FlameSkillMotion.smooth((f.progress()-.55)/.35)):1;
            matrix.setTranslation(matrix.getTranslation(new Vector3f()).lerp(grip,weight));
        }
        return matrix;
    }
}
