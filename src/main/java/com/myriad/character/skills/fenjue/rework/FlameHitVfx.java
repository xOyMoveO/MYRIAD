package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.BURST;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Short blade-flame contact: authored impact, depth-separated ember chips and directional spark streaks. */
public final class FlameHitVfx {
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0);
    private FlameHitVfx(){}
    public static boolean owns(AbilityVfxPayload p){return p.theme()==2&&p.variant()==0&&p.kind()==BURST
            &&(p.angle()==FlameHitVisuals.NORMAL||p.angle()==FlameHitVisuals.CRITICAL||p.angle()==FlameHitVisuals.EMBER);}
    public static int frame(double age,int life){return Math.clamp((int)Math.floor(age*8/life),0,7);}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!p.valid()||age<0||age>=p.life())return List.of();
        var out=new ArrayList<Quad>();Vec3 f=p.to().subtract(p.from()).normalize();if(f.lengthSqr()<1E-9)f=new Vec3(0,0,1);
        Vec3 right=f.cross(UP);if(right.lengthSqr()<1E-9)right=X;right=right.normalize();Vec3 up=right.cross(f).normalize();
        double t=age/p.life(),r=p.radius();boolean ember=p.angle()==FlameHitVisuals.EMBER;
        float fade=(float)Math.min(1,(p.life()-age)/2);
        int phase=ember?Math.max(4,frame(age,p.life())):frame(age,p.life());
        sheet(out,p.from(),right,up,r,r,FENJUE_HIT,phase,ember?.58f:1f);
        int chips=low?3:6;
        for(int i=0;i<chips;i++){
            double a=i*2.399+Math.floorMod(p.started(),200)*.11,travel=r*(.08+t*.95),bend=.16*Math.sin(i*3.7);
            Vec3 radial=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 at=p.from().add(radial.scale(travel)).add(f.scale(bend*t)).add(0,t*.12,0);
            double size=r*(.12+.035*(i%3))*(1-t*.6);
            Vec3 across=i%2==0?right:right.scale(.5).add(f.scale(.8660254)).normalize();
            sheet(out,at,across,up,size,size,FENJUE_PIXEL_EMBERS,Math.min(7,phase+i%2),fade*(ember?.34f:.72f));
            if(!low&&!ember&&i%2==0&&t>.10&&t<.78){
                Vec3 start=p.from().add(radial.scale(travel*.55));Vec3 axis=at.subtract(start);double length=axis.length();
                sheet(out,start.lerp(at,.5),axis.normalize(),f.cross(axis).normalize(),length*.5,r*.035,
                        FENJUE_PIXEL_STREAM,Math.min(7,phase),fade*.65f);
            }
        }
        return List.copyOf(out);
    }
    private static void sheet(List<Quad> out,Vec3 center,Vec3 right,Vec3 up,double w,double h,int material,int frame,float alpha){
        if(alpha<=0||w<=0||h<=0)return;Vec3 r=right.scale(w),u=up.scale(h);
        var a=center.subtract(r).subtract(u);var b=center.add(r).subtract(u);var c=center.add(r).add(u);var d=center.subtract(r).add(u);
        var cell=FlameFlipbook.cell(frame);out.add(new Quad(a,b,c,d,0xFFFFFF,alpha,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,0xFFFFFF,alpha,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
}
