package com.myriad.character.ability;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Geometry primitives only. Role classes own their material, timing and composition. */
public final class AbilityMeshBuilder {
    public static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private final ArrayList<Quad> out=new ArrayList<>();
    private final int limit;
    public final float fade;public final double age,progress;public final boolean reduced;
    public AbilityMeshBuilder(AbilityVfxPayload p,float age,boolean reduced){
        this(p,age,reduced,192);
    }
    public AbilityMeshBuilder(AbilityVfxPayload p,float age,boolean reduced,int limit){
        this.limit=limit;
        this.age=age;this.progress=Math.clamp(age/p.life(),0,1);this.reduced=reduced;
        this.fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));
    }
    public List<Quad> result(){return List.copyOf(out);}
    public static Vec3 direction(Vec3 from,Vec3 to){Vec3 d=to.subtract(from);return d.lengthSqr()<1E-8?Z:d.normalize();}
    public static Vec3 side(Vec3 forward){Vec3 s=forward.cross(UP);return s.lengthSqr()<1E-8?X:s.normalize();}
    public static Vec3 circle(Vec3 at,double r,double angle,double y){return at.add(Math.cos(angle)*r,y,Math.sin(angle)*r);}
    public void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha,int material){
        if(out.size()+2>limit||alpha<=0)return;
        float opacity=(float)Math.clamp(alpha*fade,0,1);if(opacity<=0)return;
        out.add(new Quad(a,b,c,d,rgb,opacity,material));
        out.add(new Quad(d,c,b,a,rgb,opacity,material,true));
    }
    public void plane(Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,int rgb,double alpha){
        Vec3 r=right.scale(w),u=up.scale(h);
        quad(at.subtract(r).subtract(u),at.add(r).subtract(u),at.add(r).add(u),at.subtract(r).add(u),rgb,alpha,material);
    }
    /** Select a 128px cell from a horizontal four-frame atlas, including texel-safe edges. */
    public void frame(Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,int frame,double alpha){
        Vec3 r=right.scale(w),u=up.scale(h);
        Vec3 a=at.subtract(r).subtract(u),b=at.add(r).subtract(u),c=at.add(r).add(u),d=at.subtract(r).add(u);
        atlasFace(a,b,c,d,material,frame,alpha);
    }
    public void atlasTriangle(Vec3 a,Vec3 b,Vec3 c,int material,int facet){
        atlasFace(a,b,c,c,material,facet,1);
    }
    /** A closed model supplies outward winding; a second reverse quad would reveal its interior. */
    public void modelFace(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int material,int cell,int tint,double opacity){
        float u0=(Math.floorMod(cell,4)*128+1f)/512,u1=u0+126f/512;
        modelFace(a,b,c,d,material,tint,opacity,u0,u1,1f/128,127f/128,false);
    }
    /** Use an independent atlas region for each exported solid-model face. */
    public void modelFace(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int material,int tint,double opacity,
                          float u0,float u1,float v0,float v1,boolean topFirst){
        if(out.size()>=limit||opacity<=0)return;
        out.add(new Quad(a,b,c,d,tint,(float)Math.clamp(opacity,0,1),material,topFirst,u0,u1,v0,v1));
    }
    private void atlasFace(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int material,int frame,double alpha){
        if(out.size()+2>limit||alpha<=0||fade<=0)return;
        float opacity=(float)Math.clamp(alpha*fade,0,1);
        float u0=(Math.floorMod(frame,4)*128+.5f)/512,u1=u0+127f/512;
        float v0=.5f/128,v1=1-v0;
        out.add(new Quad(a,b,c,d,0xFFFFFF,opacity,material,false,u0,u1,v0,v1));
        out.add(new Quad(d,c,b,a,0xFFFFFF,opacity,material,true,u0,u1,v0,v1));
    }
    public void triangle(Vec3 a,Vec3 b,Vec3 c,int rgb,double alpha){quad(a,b,c,c,rgb,alpha,0);}
    public void line(Vec3 a,Vec3 b,Vec3 normal,double width,int rgb,double alpha){
        Vec3 d=b.subtract(a);if(d.lengthSqr()<1E-8)return;
        Vec3 s=d.cross(normal);if(s.lengthSqr()<1E-8)s=side(d.normalize());s=s.normalize().scale(width*.5);
        quad(a.subtract(s),b.subtract(s),b.add(s),a.add(s),rgb,alpha,0);
    }
    /** Six-sided tube, face colours serve as an explicit bevel instead of a faint wire. */
    public void rod(Vec3 from,Vec3 to,double radius,int lit,int body,int shade,double alpha){
        if(from.distanceToSqr(to)<1E-8)return;
        Vec3 f=direction(from,to),s=side(f),v=s.cross(f).normalize();
        for(int i=0;i<6;i++){
            double a=i*Math.PI/3,b=a+Math.PI/3;
            Vec3 x=s.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius));
            Vec3 y=s.scale(Math.cos(b)*radius).add(v.scale(Math.sin(b)*radius));
            quad(from.add(x),to.add(x),to.add(y),from.add(y),i<2?lit:i<4?body:shade,alpha,0);
        }
    }
    /** Raised tapered crescent. Separate bright top and dark outer edge remain legible head-on. */
    public void bladeArc(Vec3 at,double radius,double start,double sweep,double width,double height,int top,int side){
        int steps=Math.clamp((int)Math.ceil((reduced?8:12)*Math.abs(sweep)/Math.PI),4,reduced?8:12);
        for(int i=0;i<steps;i++){
            double t=i/(double)steps,u=(i+1.0)/steps,a=start+sweep*t,b=start+sweep*u;
            double wa=width*(.08+.92*Math.sin(Math.PI*t)),wb=width*(.08+.92*Math.sin(Math.PI*u));
            Vec3 innerA=circle(at,radius-wa,a,0),outerA=circle(at,radius,a,0);
            Vec3 innerB=circle(at,radius-wb,b,0),outerB=circle(at,radius,b,0);
            Vec3 oa=outerA.add(0,height,0),ob=outerB.add(0,height,0);
            quad(innerA.add(0,height*.5,0),innerB.add(0,height*.5,0),ob,oa,top,.97,0);
            quad(outerA,outerB,ob,oa,side,.94,0);
            quad(innerA,innerB,outerB,outerA,side,.7,0);
        }
    }
    public void ring(Vec3 at,double radius,double width,int rgb,double alpha){
        int n=reduced?12:20;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n,b=(i+1)*Math.PI*2/n;
            quad(circle(at,Math.max(0,radius-width),a,0),circle(at,Math.max(0,radius-width),b,0),
                    circle(at,radius,b,0),circle(at,radius,a,0),rgb,alpha,0);
        }
    }
    public void decal(Vec3 at,double radius,int material,int rgb,double alpha){plane(at,X,Z,radius,radius,material,rgb,alpha);}
    /** Two continuous crossed ribbons with one UV interval along the whole path. */
    public void trail(Vec3 from,Vec3 to,double width,int material,int rgb,double alpha){
        if(from.distanceToSqr(to)<1E-8)return;
        Vec3 d=direction(from,to),side=side(d),up=side.cross(d).normalize();
        int n=reduced?3:5;
        for(int i=0;i<n;i++){
            double t=i/(double)n,u=(i+1.0)/n;Vec3 a=from.lerp(to,t),b=from.lerp(to,u);
            for(Vec3 normal:new Vec3[]{side,up}){
                Vec3 w0=normal.scale(width*(.1+.9*t)),w1=normal.scale(width*(.1+.9*u));
                if(out.size()+2>limit)return;float opacity=(float)Math.clamp(alpha*fade*(.25+.75*u),0,1);
                out.add(new Quad(a.subtract(w0),b.subtract(w1),b.add(w1),a.add(w0),rgb,opacity,material,false,(float)t,(float)u));
                out.add(new Quad(a.add(w0),b.add(w1),b.subtract(w1),a.subtract(w0),rgb,opacity,material,true,(float)t,(float)u));
            }
        }
    }
    public void sparks(Vec3 at,double radius,int rgb){
        int n=reduced?4:7;
        for(int i=0;i<n;i++){
            double a=i*2.399963;Vec3 from=circle(at,radius*(.2+.3*progress),a,.15+progress*.3);
            Vec3 to=circle(at,radius*(.4+.5*progress),a,.25+progress*.7);
            line(from,to,UP,.035,rgb,.9);
        }
    }
}
