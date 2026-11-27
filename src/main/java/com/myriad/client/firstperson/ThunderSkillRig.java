package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Shared first-person sword attachment, second-hand grip and blade-wipe contact. */
public final class ThunderSkillRig {
    private static final float DEG=(float)(Math.PI/180);
    private ThunderSkillRig(){}
    public static Matrix4f main(ThunderSkillMotion.Frame f,boolean left){return ThunderSwordRig.wrist(f.main(),0,left);}
    public static Matrix4f blade(ThunderSkillMotion.Frame f,boolean left){
        if(f.action()==SLASH_FIRE){
            return ThunderSwordRig.blade(new ThunderSwordMotion.Frame(f.main(),f.before(),f.after(),1-smooth((f.progress()-.80)/.20),0),left);
        }
        double t=f.progress();
        double gesture=switch(f.action()){
            case ARC_READY,SLASH_READY,JUDGMENT_READY,ThunderSkillMotion.AIM -> smooth(t/.18);
            case ARC_FIRE,JUDGMENT_FIRE,NINE_FIRE -> 1-smooth((t-.80)/.20);
            case GROUND,GUARD -> smooth(t/.18)*(1-smooth((t-.80)/.20));
            default -> 0;
        };
        double rest=ThunderSwordRig.restTurn(left);
        double twist=rest+gesture*Math.IEEEremainder((left?-90:90)*DEG-rest,2*Math.PI);
        return new Matrix4f().rotateZ((float)((left?-1:1)*(-65+f.bladeFlip()))*DEG)
                .rotateY((float)twist).scale(ThunderSwordRig.BLADE_SCALE);
    }
    public static Matrix4f sword(ThunderSkillMotion.Frame f,boolean left){return main(f,left).mul(blade(f,left));}
    /** Both endpoints are rigid wrist or item transforms with the same uniform scale. */
    public static Matrix4f recover(Matrix4f from,Matrix4f to,float amount){
        var position=from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()),amount);
        var rotation=from.getUnnormalizedRotation(new Quaternionf()).slerp(to.getUnnormalizedRotation(new Quaternionf()),amount);
        return new Matrix4f().translation(position).rotate(rotation).scale(to.getScale(new Vector3f()));
    }
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
