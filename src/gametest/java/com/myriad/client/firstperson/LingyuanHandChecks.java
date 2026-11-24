package com.myriad.client.firstperson;

import java.util.HashSet;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3f;
import static com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.*;

public final class LingyuanHandChecks {
    public static void verify(GameTestHelper h) {
        var m=new LingyuanHandMotion();
        var poses=new HashSet<LingyuanHandMotion.Pose>();
        for(int skill=2;skill<=7;skill++){
            m.reset();
            m.cue(skill,skill==2?DASH:skill==7?GATHER:CAST,0,7,1);
            poses.add(m.pose(2));
            for(double t=0;t<=12;t+=.25) for(boolean left:new boolean[]{false,true}) {
                var sword=LingyuanHandRig.sword(m,t,left,.17f);
                for(boolean slim:new boolean[]{false,true}){
                    var arm=LingyuanHandRig.arm(m.pose(t),left,false,slim,.17f,sword);
                    float x=(slim?.5f:1f)/16*(left?-1:1);
                    var palm=arm.transformPosition(new Vector3f(-x,.5f,0));
                    h.assertTrue(palm.distance(sword.getTranslation(new Vector3f()))<1e-5 && sword.determinant()>0,
                            "Both hands attach blade grip without mirroring model winding");
                }
            }
            h.assertTrue(m.pose(12).equals(LingyuanHandMotion.IDLE),"Every hand move has a finite return");
        }
        h.assertTrue(poses.size()==6,"Six distinct silhouettes");
        m.reset();
        for(int i=0;i<4;i++){
            m.cue(0,BASIC,20*i,9,20);
            h.assertTrue(m.combo()==i%3,"Accepted swings rotate across three moves");
        }
        m.cue(0,BASIC,120,9,20);
        h.assertTrue(m.combo()==0,"Pause resets combo");
        m.cue(7,CHOP,130,6,.5);
        var hand=m.pose(132);
        m.cue(7,STOP,132,7,0);
        h.assertTrue(m.pose(132).equals(hand)&&m.pose(136).equals(LingyuanHandMotion.IDLE),"Interrupt unloads the chop instead of replaying impact");
    }
}
