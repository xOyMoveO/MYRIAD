package com.myriad.character.skills.shuo;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** HD core progressively revealed/erased, plus independent flowing filaments; no display entities. */
public final class ShuoSlashMesh {
    public static final int MAX_QUADS=74;
    private ShuoSlashMesh(){}
    private static float smooth(double p){p=Math.clamp(p,0,1);return (float)(p*p*(3-2*p));}
    public static List<Quad> build(Vec3 at,Vec3 right,Vec3 up,double w,double h,float p,boolean low){
        if(!Float.isFinite(p)||p<=0||p>=1||w<=0||h<=0)return List.of();
        var r=right.normalize();var u=up.subtract(r.scale(up.dot(r))).normalize();
        if(r.lengthSqr()<.5||u.lengthSqr()<.5)return List.of();
        var normal=r.cross(u).normalize();var out=new ArrayList<Quad>();
        float alpha=smooth(p/.10)*(1-smooth((p-.70)/.30));
        double lo=Math.max(0,1-p/.42),hi=1-Math.clamp((p-.58)/.42,0,1);
        int slices=low?8:16;
        for(int i=0;i<slices;i++){
            float a=(float)Math.max(lo,i/(double)slices),b=(float)Math.min(hi,(i+1)/(double)slices);
            if(b<=a)continue;
            var left=at.add(r.scale((a*2-1)*w));var end=at.add(r.scale((b*2-1)*w));
            double middle=(a+b)*.5;
            float edge=smooth((middle-lo)/.08)*smooth((hi-middle)/.08);
            panel(out,left.subtract(u.scale(h)),end.subtract(u.scale(h)),end.add(u.scale(h)),left.add(u.scale(h)),0xFFFFFF,alpha*edge,75,a,b,0,1);
        }
        int strands=low?1:3,steps=low?4:6;
        for(int strand=0;strand<strands;strand++){
            double q=p-strand*.055;if(q<=.08)continue;
            double head=Math.clamp((q-.08)/.58,0,1),tail=Math.max(0,head-(.38-.10*p));
            for(int n=0;n<steps;n++){
                double a=tail+(head-tail)*n/steps,b=tail+(head-tail)*(n+1)/steps;
                var x=filament(at,r,u,normal,w,h,a,strand,p);var y=filament(at,r,u,normal,w,h,b,strand,p);
                var width=u.scale((strand==0?.012:.007)*(1-p*.65));
                panel(out,x.subtract(width),y.subtract(width),y.add(width),x.add(width),strand==0?0xF6FDFF:0xA9D8F5,alpha*(strand==0?.88f:.56f),0,.45f,.55f,.45f,.55f);
            }
        }
        return List.copyOf(out);
    }
    private static Vec3 filament(Vec3 at,Vec3 r,Vec3 u,Vec3 n,double w,double h,double t,int strand,float p){
        double x=1-2*t,y=-.12+Math.sin(Math.PI*t)*(.48-strand*.055)-strand*.035;
        return at.add(r.scale(x*w)).add(u.scale(y*h)).add(n.scale(.025+strand*.012+p*.035));
    }
    private static void panel(List<Quad> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material,float u0,float u1,float v0,float v1){
        if(alpha<.005||out.size()+2>MAX_QUADS||b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        alpha=ShuoBrightness.opacity(alpha);color=ShuoBrightness.tint(color);
        out.add(new Quad(a,b,c,d,color,alpha,material,false,u0,u1,v0,v1));
        out.add(new Quad(d,c,b,a,color,alpha,material,true,u0,u1,v0,v1));
    }
}
