package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Painted fire mapped onto the reference's upper 120-degree circle arc, not a stretched ellipse. */
public final class FlameCrossSlashVfx {
    public static final double SWEEP_DEGREES=120,RADIUS=1.05,CUT_ANGLE=25,LIFT=-.12,CHORD_CENTER=.25;
    public static final double SOURCE_TOP=184.0/512,SOURCE_BOTTOM=408.0/512;
    /** Common source contour across all phases; do not rescale/recenter each animation frame. */
    public static double sourceEdge(double t){return 260+116*Math.sqrt(Math.max(0,1-Math.pow(2*t-1,2)));}
    /** Canonical plane: X along the slash, Y convex/forward. Upper circle from 0 to 120 degrees. */
    public static Vec3 localPoint(double t,double u,double radius){
        double angle=Math.toRadians(SWEEP_DEGREES*t);
        double rho=radius*(RADIUS+(u*512-sourceEdge(t))*.0021);
        return new Vec3(rho*Math.cos(angle),rho*Math.sin(angle),0);
    }
    public static Vec3 diagonal(AbilityVfxPayload p,int cut){
        var b=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());double a=Math.toRadians(CUT_ANGLE);
        return b.side().scale(Math.cos(a)).add(b.up().scale((cut==0?1:-1)*Math.sin(a)));
    }
    /** Eye packet is not a foot position. Recenter the asymmetric 0..120-degree chord for BOTH cuts. */
    public static Vec3 center(AbilityVfxPayload p,int cut,double age){
        var b=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());var d=diagonal(p,cut);double r=Math.max(.08,p.radius());
        return p.from().add(b.forward().scale(r*(.65+.12*smooth(age/p.life())))).add(b.up().scale(LIFT))
            .subtract(d.scale(r*RADIUS*CHORD_CENTER)).add(b.forward().cross(d).scale(.025));
    }
    private FlameCrossSlashVfx(){}
    public static boolean owns(AbilityVfxPayload p){return p.valid()&&p.theme()==2&&p.variant()==2&&p.kind()==ARC;}
    public record Window(double head,double tail,double opacity){}
    public static Window window(double age,int cut,int life){
        double t=age*14.0/life-cut*3.6;
        if(t<=0||t>=10)return new Window(0,0,0);
        return new Window(smooth(t/3.2),smooth((t-5.5)/4.5),smooth(t/.5)*(1-smooth((t-7)/3)));
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!Float.isFinite(age)||age<0||age>=p.life())return List.of();
        var basis=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());var f=basis.forward();
        double r=Math.max(.08,p.radius());var out=new ArrayList<Quad>();
        for(int cut=0;cut<2;cut++){
            var w=window(age,cut,p.life());if(w.opacity()<=0||w.head()<=w.tail())continue;
            var diagonal=diagonal(p,cut);
            // The arc plane is still spanned by the slash diagonal and forward: no broad lateral XY face.
            var center=center(p,cut,age);
            double phase=age*14.0/p.life()-cut*3.6;
            int cell=(int)Math.clamp(phase<3.2?phase/3.2*4:phase<5.5?4:5+(phase-5.5)/4.5*3,0,7);
            {
                double a=w.tail(),b=w.head();
                float opacity=(float)w.opacity();
                for(int layer=0;layer<(low?1:2);layer++){
                    // Identical canvas/frame alignment avoids resized inner copies and blended-frame ghosting.
                    int tint=layer==0?0xFFFFFF:0xFFE4AD;float alpha=opacity*(layer==0?1:.64f);
                    imageArc(out,center,f,diagonal,r,a,b,cut,tint,alpha,CrispFlameSlashFrames.cell(cell),low?9:18);
                }
                if(!low)embers(out,center,f,diagonal,r,w,cut,phase);
            }
        }
        return List.copyOf(out);
    }
    /** Brighter painted fire and warm inner image, preserving world sky light; other recipes are unchanged. */
    public static int sceneLight(int packed,int tint){
        int block=(packed>>4)&15,sky=(packed>>20)&15;
        return (Math.max(block,tint==0xFFFFFF?10:14)<<4)|(sky<<20);
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private static void imageArc(List<Quad> out,Vec3 center,Vec3 forward,Vec3 diagonal,double radius,double start,double end,int cut,int tint,float alpha,FlameFlipbook.Cell cell,int segments){
        if(alpha<=0)return;
        double top=cut==0?start:1-end,bottom=cut==0?end:1-start;
        for(int i=0;i<segments;i++){
            double a=Math.max(top,i/(double)segments),b=Math.min(bottom,(i+1.0)/segments);if(b-a<1E-9)continue;
            var p0=world(center,forward,diagonal,localPoint(b,0,radius));var p1=world(center,forward,diagonal,localPoint(b,1,radius));
            var p2=world(center,forward,diagonal,localPoint(a,1,radius));var p3=world(center,forward,diagonal,localPoint(a,0,radius));
            float v0=cell.v0()+(cell.v1()-cell.v0())*(float)(SOURCE_TOP+(SOURCE_BOTTOM-SOURCE_TOP)*a);
            float v1=cell.v0()+(cell.v1()-cell.v0())*(float)(SOURCE_TOP+(SOURCE_BOTTOM-SOURCE_TOP)*b);
            out.add(new Quad(p0,p1,p2,p3,tint,alpha,FENJUE_CRISP_SLASH,false,cell.u0(),cell.u1(),v0,v1));
            out.add(new Quad(p3,p2,p1,p0,tint,alpha,FENJUE_CRISP_SLASH,true,cell.u0(),cell.u1(),v0,v1));
        }
    }
    private static Vec3 world(Vec3 center,Vec3 forward,Vec3 diagonal,Vec3 p){return center.add(diagonal.scale(p.x)).add(forward.scale(p.y));}
    /** Sparse moving after-flame, not another offset copy of the crescent silhouette. */
    private static void embers(List<Quad> out,Vec3 center,Vec3 forward,Vec3 diagonal,double r,Window w,int cut,double phase){
        for(int i=0;i<3;i++){
            double lag=.10+i*.14;float alpha=(float)(.58*w.opacity()*smooth((w.head()-w.tail()-lag)/.12)*(1-i*.18));
            if(alpha<=0)continue;
            double t=cut==0?w.head()-lag:1-w.head()+lag;
            var at=world(center,forward,diagonal,localPoint(t,sourceEdge(t)/512-.06,r)).subtract(forward.scale(r*.10));
            var u=forward.scale(r*.25*(1-i*.12));var v=diagonal.scale(r*.12*(1-i*.12));
            var a=at.subtract(u).subtract(v);var b=at.add(u).subtract(v);var c=at.add(u).add(v);var d=at.subtract(u).add(v);
            var cell=FlameFlipbook.once(Math.clamp(phase/10,0,1),i);
            out.add(new Quad(a,b,c,d,0xFFD39A,alpha,FENJUE_PIXEL_EMBERS,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
            out.add(new Quad(d,c,b,a,0xFFD39A,alpha,FENJUE_PIXEL_EMBERS,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        }
    }
}
