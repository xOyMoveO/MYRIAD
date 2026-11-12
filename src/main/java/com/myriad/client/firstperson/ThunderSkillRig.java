package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Shared first-person sword attachment, second-hand grip and blade-wipe contact. */
public final class ThunderSkillRig {
    private static final float DEG=(float)(Math.PI/180);
    private ThunderSkillRig(){}
    public static Matrix4f main(ThunderSkillMotion.Frame f,boolean left){return ThunderSwordRig.wrist(f.main(),0,left);}
    public static Matrix4f blade(ThunderSkillMotion.Frame f,boolean left){
        if(f.action()==SLASH_FIRE){
            return ThunderSwordRig.blade(new ThunderSwordMotion.Frame(f.main(),f.before(),f.after(),1,0),left);
        }
        return new Matrix4f().rotateZ((float)((left?-1:1)*(-65+f.bladeFlip()))*DEG)
                .rotateY((left?-90:90)*DEG).scale(1.25f);
    }
    public static Matrix4f sword(ThunderSkillMotion.Frame f,boolean left){return main(f,left).mul(blade(f,left));}
    public static double wipeHeight(double progress){return .10+.94*Math.clamp((progress-.18)/.75,0,1);}
    public static Matrix4f off(ThunderSkillMotion.Frame f,boolean left){
        var matrix=ThunderSwordRig.wrist(f.off(),0,!left);
        if(f.action()==GROUND){
            var grip=sword(f,left).transformPosition(new Vector3f(0,-.28f,0));
            double t=f.progress(),weight=smooth((t-.08)/.18)*(1-smooth((t-.75)/.22));
            matrix.setTranslation(matrix.getTranslation(new Vector3f()).lerp(grip,(float)weight));
        }else if(f.action()==JUDGMENT_READY){
            // The off palm slides next to the real sword, from guard to upper blade, not across empty air.
            var contact=sword(f,left).transformPosition(new Vector3f(-.075f,(float)wipeHeight(f.progress()),0));
            matrix.setTranslation(matrix.getTranslation(new Vector3f()).lerp(contact,(float)smooth(f.progress()/.18)));
        }else if(f.action()==JUDGMENT_FIRE){
            var contact=sword(f,left).transformPosition(new Vector3f(-.075f,1.04f,0));
            matrix.setTranslation(contact.lerp(matrix.getTranslation(new Vector3f()),(float)smooth(f.progress()/.55)));
        }
        return matrix;
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
