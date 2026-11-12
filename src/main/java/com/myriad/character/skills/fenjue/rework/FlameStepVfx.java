package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Attached side wakes and one endpoint slash, using authored fire images, never damage geometry. */
public final class FlameStepVfx {
    private FlameStepVfx(){}
    public static boolean sides(AbilityVfxPayload p){return p.valid()&&p.theme()==2&&p.variant()==3&&p.kind()==SHIELD&&p.anchor()==p.owner()&&(p.angle()==0||p.angle()==2);}
    public static boolean finish(AbilityVfxPayload p){return p.valid()&&p.theme()==2&&p.variant()==3&&p.kind()==ARC&&p.angle()==1;}
    public static boolean owns(AbilityVfxPayload p){return sides(p)||finish(p);}
    /** An attached dash accent follows the rendered body, not the body's stale position at receipt. */
    public static Vec3 anchorOrigin(AbilityVfxPayload p,Vec3 ordinary){return sides(p)?p.from():ordinary;}
    public static void follow(Cast c,int stage){AbilityVisuals.following(c,SHIELD,c.player,.8,stage,3);}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!Float.isFinite(age)||age<0||age>=p.life())return List.of();
        return sides(p)?wakes(p,age,low):slash(p,age,low);
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private static void panel(List<Quad> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int tint,double alpha,int material,FlameFlipbook.Cell uv,float u0,float u1,float v0,float v1){
        if(alpha<.002)return;
        float x0=uv.u0()+(uv.u1()-uv.u0())*u0,x1=uv.u0()+(uv.u1()-uv.u0())*u1;
        float y0=uv.v0()+(uv.v1()-uv.v0())*v0,y1=uv.v0()+(uv.v1()-uv.v0())*v1;
        out.add(new Quad(a,b,c,d,tint,(float)Math.min(1,alpha),material,false,x0,x1,y0,y1));
        out.add(new Quad(d,c,b,a,tint,(float)Math.min(1,alpha),material,true,x0,x1,y0,y1));
    }
    private static List<Quad> wakes(AbilityVfxPayload p,float age,boolean low){
        var out=new ArrayList<Quad>();var f=p.to().subtract(p.from()).multiply(1,0,1).normalize();
        if(f.lengthSqr()<.01)f=new Vec3(0,0,1);
        var up=new Vec3(0,1,0);var side=f.cross(up).normalize();
        double fade=smooth((p.life()-age)/1.2);int segments=low?5:8;
        for(int sign:new int[]{-1,1})for(int lane=0;lane<(low?1:2);lane++){
            var width=up.scale(.75).add(side.scale(sign*.3)).normalize();
            var cell=FlameFlipbook.loop(p.started(),age,sign+lane*3);
            for(int i=0;i<segments;i++){
                double a=i/(double)segments,b=(i+1.0)/segments;
                var start=wakePoint(p.from(),f,side,sign,lane,a,p.started()+age);
                var end=wakePoint(p.from(),f,side,sign,lane,b,p.started()+age);
                // The painted source has transparent vertical gutters; compensate before it is
                // mapped so the side streams remain visible at a normal third-person distance.
                double wa=(.32+.10*lane)*(1-a*.75),wb=(.32+.10*lane)*(1-b*.75);
                panel(out,start.subtract(width.scale(wa)),end.subtract(width.scale(wb)),end.add(width.scale(wb)),start.add(width.scale(wa)),
                    0xFFFFFF,fade*(1-a)*.92,FENJUE_PIXEL_STREAM,cell,(float)a,(float)b,0,1);
                if(!low)panel(out,start.subtract(width.scale(wa*.30)),end.subtract(width.scale(wb*.30)),end.add(width.scale(wb*.30)),start.add(width.scale(wa*.30)),
                    0xFFF0B5,fade*(1-a)*.88,FENJUE_PIXEL_STREAM,FlameFlipbook.loop(p.started(),age,sign+lane*3+2),(float)a,(float)b,.3f,.7f);
            }
        }
        return List.copyOf(out);
    }
    private static Vec3 wakePoint(Vec3 at,Vec3 f,Vec3 s,int sign,int lane,double t,double age){
        return at.add(s.scale(sign*(.55+lane*.12+.14*Math.sin(t*Math.PI))))
            .subtract(f.scale(.12+t*(3.2+lane*.22)))
            .add(0,.56+lane*.40+.08*Math.sin(t*4+age*.7+lane),0);
    }
    private static List<Quad> slash(AbilityVfxPayload p,float age,boolean low){
        var out=new ArrayList<Quad>();var basis=FlameSlashAim.basis(p.to().subtract(p.from()),0);
        var f=basis.forward();var d=basis.side().scale(Math.cos(Math.toRadians(20))).add(basis.up().scale(Math.sin(Math.toRadians(20))));
        double head=smooth(age/1.4),tail=smooth((age-3)/4),alpha=smooth(age/.25)*smooth((p.life()-age)/2.5);
        if(head<=tail||alpha<=0)return List.of();
        double r=p.radius();var center=p.from().add(basis.up().scale(-.12)).add(f.scale(.9)).subtract(d.scale(r*FlameCrossSlashVfx.RADIUS*.25));
        int cell=(int)Math.clamp(age<1.4?age/1.4*4:age<3?4:5+(age-3)/5*3,0,7),segments=low?9:18;
        var uv=CrispFlameSlashFrames.cell(cell);
        // Left-ready -> right-descending release: reverse the same crisp 120-degree painted contour.
        for(int i=0;i<segments;i++){
            double a=Math.max(1-head,i/(double)segments),b=Math.min(1-tail,(i+1.0)/segments);if(b-a<1E-8)continue;
            var a0=point(center,f,d,b,0,r);var a1=point(center,f,d,b,1,r);
            var b1=point(center,f,d,a,1,r);var b0=point(center,f,d,a,0,r);
            float v0=(float)(FlameCrossSlashVfx.SOURCE_TOP+(FlameCrossSlashVfx.SOURCE_BOTTOM-FlameCrossSlashVfx.SOURCE_TOP)*a);
            float v1=(float)(FlameCrossSlashVfx.SOURCE_TOP+(FlameCrossSlashVfx.SOURCE_BOTTOM-FlameCrossSlashVfx.SOURCE_TOP)*b);
            panel(out,a0,a1,b1,b0,0xFFFFFF,alpha,FENJUE_CRISP_SLASH,uv,0,1,v0,v1);
            if(!low)panel(out,a0,a1,b1,b0,0xFFE4AD,alpha*.64,FENJUE_CRISP_SLASH,uv,0,1,v0,v1);
        }
        return List.copyOf(out);
    }
    private static Vec3 point(Vec3 center,Vec3 f,Vec3 d,double t,double u,double r){
        var local=FlameCrossSlashVfx.localPoint(t,u,r);return center.add(d.scale(local.x)).add(f.scale(local.y));
    }
}
