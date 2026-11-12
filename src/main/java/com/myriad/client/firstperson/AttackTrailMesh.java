package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.*;
import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Camera-space geometry only. Absolute motion sampling, no frame history, particles or combat calls. */
public final class AttackTrailMesh {
    public static final int SAMPLES=12,MAX_QUADS=80;
    public static final double LIFE=2;
    public record Cell(float u0,float u1,float v0,float v1){}
    public record Style(int body,int core,int color,int highlight,float alpha,double width){}
    public record Slice(Vec3 inner,Vec3 outer,double tick){}
    private AttackTrailMesh(){}
    public static Slice slice(Matrix4f matrix,Vector3f inner,Vector3f outer,double tick){
        var a=matrix.transformPosition(new Vector3f(inner));var b=matrix.transformPosition(new Vector3f(outer));
        return new Slice(new Vec3(a.x,a.y,a.z),new Vec3(b.x,b.y,b.z),tick);
    }
    public static List<Slice> samples(double now,double start,double duration,double from,double to,DoubleFunction<Slice> sample){
        if(!Double.isFinite(now)||!Double.isFinite(start)||!Double.isFinite(duration)||duration<=0
                ||!Double.isFinite(from)||!Double.isFinite(to)||from<0||to>1||from>=to||now<start||now>=start+duration)return List.of();
        double lo=Math.max(start+duration*from,now-LIFE),hi=Math.min(now,start+duration*to);
        if(hi-lo<.005)return List.of();
        var out=new ArrayList<Slice>(SAMPLES);
        for(int i=0;i<SAMPLES;i++)out.add(sample.apply(i==SAMPLES-1?hi:lo+(hi-lo)*i/(SAMPLES-1)));
        return List.copyOf(out);
    }
    public static List<Quad> build(List<Slice> samples,double now,Style style,Cell body,Cell core){
        if(samples.size()<2||!Double.isFinite(now))return List.of();
        var out=new ArrayList<Quad>();
        for(int i=1;i<samples.size();i++){
            var a=samples.get(i-1);var b=samples.get(i);
            double age=(now-b.tick)/LIFE;
            if(age<0||age>=1)continue;
            float alpha=(float)(style.alpha*(1-age));
            float u0=(i-1f)/(samples.size()-1),u1=i/(float)(samples.size()-1);
            // A broad translucent painted wake and a smaller, differently phased sharp layer.
            panel(out,a.inner,b.inner,b.outer,a.outer,style.color,alpha,style.body,body,u0,u1);
            panel(out,a.inner.lerp(a.outer,.73),b.inner.lerp(b.outer,.73),b.outer,a.outer,
                    style.highlight,alpha*.95f,style.core,core,u0,u1);
            // Tip streak also reads on a forward thrust, where a swept blade surface can be edge-on.
            var travel=b.outer.subtract(a.outer);var side=travel.cross(new Vec3(0,0,1));
            if(side.lengthSqr()<1E-10)side=new Vec3(1,0,0);
            side=side.normalize().scale(style.width);
            var oldSide=side.scale(Math.clamp(1-(now-a.tick)/LIFE,0,1));
            var newSide=side.scale(Math.clamp(1-(now-b.tick)/LIFE,0,1));
            panel(out,a.outer.subtract(oldSide),b.outer.subtract(newSide),b.outer.add(newSide),a.outer.add(oldSide),
                    style.highlight,alpha*.98f,0,new Cell(.45f,.55f,.45f,.55f),u0,u1);
        }
        return List.copyOf(out);
    }
    private static void panel(List<Quad> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material,Cell uv,float s0,float s1){
        if(alpha<.005||out.size()+2>MAX_QUADS||a.z>-.055||b.z>-.055||c.z>-.055||d.z>-.055)return;
        if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        float u0=uv.u0+(uv.u1-uv.u0)*s0,u1=uv.u0+(uv.u1-uv.u0)*s1;
        out.add(new Quad(a,b,c,d,color,alpha,material,false,u0,u1,uv.v0,uv.v1));
        out.add(new Quad(d,c,b,a,color,alpha,material,true,u0,u1,uv.v0,uv.v1));
    }
}
