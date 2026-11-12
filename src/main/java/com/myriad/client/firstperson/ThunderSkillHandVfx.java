package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.tianxing.rework.ThunderFlipbook;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Bounded own-camera palm and physical blade-local lightning, reusing the native HD atlases. */
public final class ThunderSkillHandVfx {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private ThunderSkillHandVfx(){}
    public static List<Quad> camera(ThunderSkillMotion.Frame f,double tick,boolean left){
        var out=new ArrayList<Quad>();int action=f.action();double t=f.progress();
        if(action<0)return List.of();
        // Place energy just camera-side of the palm, not hidden behind the opaque skin mesh.
        var palm=ThunderSkillRig.off(f,left).transformPosition(new Vector3f(0,.055f,.20f));
        var at=new Vec3(palm.x,palm.y,palm.z);
        if(action==ARC_READY||action==ARC_FIRE||action==ThunderSkillMotion.AIM||action==NINE_FIRE){
            double power=action==ARC_READY?.45+.5*t:action==ARC_FIRE||action==NINE_FIRE?1-t:t;
            double size=action==ThunderSkillMotion.AIM?.13:.17;
            plane(out,at,X,Y,size,size,THUNDER_PIXEL_CORONA,tick,0,power*.85);
            plane(out,at.add(0,0,-.015),X,Y,size*.48,size*.48,THUNDER_PIXEL_IMPACT,tick,3,power*.82);
            plane(out,at.add(0,0,-.025),X,Z,size*.78,size*.78,THUNDER_PIXEL_SEAL,tick,2,power*.65);
            if(action==ARC_FIRE&&t<.45){
                var end=at.add((left?-1:1)*.11,0,-.32*t/.45);var mid=at.lerp(end,.5);
                plane(out,mid,end.subtract(at).normalize(),Y,.14,.055,THUNDER_PIXEL_BOLT,tick,0,(1-t/.45)*.75);
            }
        }else if(action==GUARD){
            double power=Math.sin(Math.PI*t);var center=new Vec3(0,-.24,-1.28);
            double angle=tick*.07;var r=new Vec3(Math.cos(angle),Math.sin(angle),0);var up=new Vec3(-Math.sin(angle),Math.cos(angle),0);
            plane(out,center,r,up,.17,.17,THUNDER_PIXEL_SEAL,tick,0,power*.88);
            plane(out,center.add(0,0,-.02),X,Y,.21,.21,THUNDER_PIXEL_CORONA,tick,3,power*.70);
        }else if(action==JUDGMENT_READY){
            plane(out,at,X,Y,.085,.085,THUNDER_PIXEL_IMPACT,tick,0,.75);
            plane(out,at.add(0,0,-.012),X,Y,.13,.13,THUNDER_PIXEL_CORONA,tick,3,.75);
        }
        return List.copyOf(out);
    }
    public static List<Quad> blade(ThunderSkillMotion.Frame f,double tick){
        if(f.action()!=SLASH_READY&&f.action()!=SLASH_FIRE&&f.action()!=JUDGMENT_READY&&f.action()!=JUDGMENT_FIRE)return List.of();
        var out=new ArrayList<Quad>();double alpha=.55+.35*(ThunderSkillMotion.ready(f.action())?f.progress():1-f.progress());
        double top=f.action()==JUDGMENT_READY?ThunderSkillRig.wipeHeight(f.progress()):1.15;
        double length=Math.max(.10,top-.06);var center=new Vec3(0,.06+length*.5,0);
        plane(out,center,Y,X,length*.5,.065,THUNDER_PIXEL_BOLT,tick,0,alpha);
        plane(out,center.add(0,0,.016),Y,Z,length*.5,.060,THUNDER_PIXEL_BOLT,tick,3,alpha*.75);
        for(int i=0;i<2;i++){
            double y=Math.min(top,.25+i*.45),angle=tick*.12+i;
            var r=new Vec3(Math.cos(angle),0,Math.sin(angle));var up=new Vec3(-Math.sin(angle),0,Math.cos(angle));
            plane(out,new Vec3(0,y,0),r,up,.105,.105,THUNDER_PIXEL_CORONA,tick,i*3,alpha*.82);
        }
        return List.copyOf(out);
    }
    private static void plane(List<Quad> out,Vec3 at,Vec3 r,Vec3 up,double w,double h,int material,double tick,int phase,double alpha){
        if(alpha<=0||out.size()>=48)return;
        var cell=ThunderFlipbook.loop(0,tick,phase,material==THUNDER_PIXEL_SEAL||material==THUNDER_PIXEL_CORONA);
        r=r.scale(w);up=up.scale(h);var a=at.subtract(r).subtract(up);var b=at.add(r).subtract(up);var c=at.add(r).add(up);var d=at.subtract(r).add(up);
        float opacity=(float)Math.clamp(alpha,0,1);
        out.add(new Quad(a,b,c,d,0xFFFFFF,opacity,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,0xFFFFFF,opacity,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
}
