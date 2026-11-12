package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.yingxiao.rework.NightFlipbook;
import com.myriad.character.skills.yingxiao.rework.NightNeedleModel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.network.NightSkillViewPayload.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Small camera/knife-local layers made from the existing authored gray flipbooks. */
public final class NightSkillHandVfx {
    private NightSkillHandVfx(){}
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    public static List<Quad> camera(NightSkillMotion.Frame f,double tick,boolean left){
        var out=new ArrayList<Quad>();double t=f.progress();
        if(f.action()==MERGE){
            double strength=Math.sin(t*Math.PI);
            var center=new Vec3(0,-.27,-1.065);
            double a=tick*.14;
            plane(out,center,new Vec3(Math.cos(a),Math.sin(a),0),new Vec3(-Math.sin(a),Math.cos(a),0),
                    .17,.17,NIGHT_GRAY_SEAL,tick,0,strength*.88);
            plane(out,center.add(0,0,.025),X,Y,.13,.13,NIGHT_GRAY_SMOKE,tick,3,strength*.72);
            for(int i=0;i<3;i++){
                double angle=a+i*Math.PI*2/3;
                var at=center.add(Math.cos(angle)*.13,Math.sin(angle)*.10,0);
                plane(out,at,X,Y,.055,.055,NIGHT_GRAY_IMPACT,tick,i,strength*.72);
            }
        }else if(f.action()==SHIELD&&t>.32&&t<.77){
            // The actual point of the approved dagger; always follows the same wrist/grip matrix.
            var tip=NightDaggerRig.wrist(f.main(),left).mul(NightDaggerRig.blade(left,f.main().grip()))
                    .transformPosition(new org.joml.Vector3f(.06375f,.5925f,0));
            double strength=Math.sin((t-.32)/.45*Math.PI);
            var at=new Vec3(tip.x,tip.y,tip.z+.025);
            plane(out,at,X,Y,.12,.12,NIGHT_GRAY_SEAL,tick,2,strength*.85);
            plane(out,at.add(0,0,.01),X,Y,.075,.075,NIGHT_GRAY_IMPACT,tick,1,strength*.72);
        }
        return List.copyOf(out);
    }
    /** Knife-local +Y matches the physical blade, rather than a detached halo in front of the camera. */
    public static List<Quad> blade(NightSkillMotion.Frame f,double tick){
        if(f.action()!=DASH&&f.action()!=RETURN&&f.action()!=READY&&f.action()!=STRIKE)return List.of();
        var out=new ArrayList<Quad>();double alpha=Math.sin(f.progress()*Math.PI)*.88;
        plane(out,new Vec3(0,.32,0),Y,X,.30,.065,NIGHT_GRAY_WAKE,tick,0,alpha);
        plane(out,new Vec3(0,.33,.018),Y,Z,.29,.052,NIGHT_GRAY_WAKE,tick,3,alpha*.64);
        return List.copyOf(out);
    }
    /** A small held needle during the windup, then only the real world projectile remains. */
    public static List<Quad> needle(NightSkillMotion.Frame f){
        if(f.action()!=THROW||f.progress()>.34)return List.of();
        var out=new ArrayList<Quad>();NightNeedleModel.append(out,new Vec3(0,.625,0),Y,true,1);
        return List.copyOf(out);
    }
    private static void plane(List<Quad> out,Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,double tick,int offset,double alpha){
        if(alpha<=0)return;
        var cell=NightFlipbook.loop(material,0,tick,offset);var r=right.scale(w);var u=up.scale(h);
        var a=at.subtract(r).subtract(u);var b=at.add(r).subtract(u);var c=at.add(r).add(u);var d=at.subtract(r).add(u);
        out.add(new Quad(a,b,c,d,0xFFFFFF,(float)alpha,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,0xFFFFFF,(float)alpha,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
}
