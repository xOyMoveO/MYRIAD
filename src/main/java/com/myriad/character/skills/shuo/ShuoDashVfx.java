package com.myriad.character.skills.shuo;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Layered painted blade wakes, not smoke cubes or a camera-facing opaque speed tunnel. */
public final class ShuoDashVfx {
    public static final int DEPART=315,ARRIVE=316,MAX_QUADS=96;
    private ShuoDashVfx(){}
    public static boolean signal(int signal){return signal==ShuoSkillVfx.TRACE||signal==ShuoSkillVfx.CHASE||signal==DEPART||signal==ARRIVE;}
    public static float ownerOpacity(AbilityVfxPayload p){return signal((int)p.angle())&&ShuoSkillVfx.owns(p)?.48f:1;}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!ShuoSkillVfx.owns(p)||!Float.isFinite(age)||age<0||age>=p.life()||!signal((int)p.angle()))return List.of();
        var delta=p.to().subtract(p.from());if(delta.lengthSqr()<.0004)return List.of();
        var along=delta.normalize();var side=along.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<.001)side=along.cross(new Vec3(1,0,0));side=side.normalize();var up=side.cross(along).normalize();
        var g=new Mesh();float t=age/p.life();
        float fade=(float)(Math.min(1,(age+1)/1.4)*Math.pow(1-t,1.2));
        double power=p.variant()==6?1.22:p.variant()==2?.78:p.variant()==7?.95:1;
        double r=Math.min(1.15,p.radius())*power;
        int white=p.variant()==2?0xD2E2EC:0xFFFFFF,blue=p.variant()==6?0xD4EFFF:0xACD8FF;
        if((int)p.angle()==DEPART||(int)p.angle()==ARRIVE){
            boolean arrival=(int)p.angle()==ARRIVE;
            var at=p.from();double spread=r*(.3+.7*Math.sin(Math.PI*t*.7));
            // Two open, curved blade wings: no full-screen ring and no future path preview.
            for(int sign:new int[]{-1,1}){
                var cross=side.scale(sign);var center=at.add(cross.scale(spread*.42)).add(0,-.1,0);
                g.sheet(center,cross.add(along.scale(arrival?.18:-.25)),up,spread,.55*power,SHUO_SLASH,white,fade*.78f);
                if(!low)g.sheet(center.add(up.scale(.08)),cross.add(up.scale(.3)),along,spread*.75,.28,SHUO_STREAM,blue,fade*.53f);
            }
            // Ground-level shearing streaks, not a solid decal disc.
            g.sheet(at.add(0,-.62,0),side,along,r*(.8+t*.35),.24,SHUO_STREAM,blue,fade*.65f);
            if(!low)g.sheet(at,side,up,.24*(1-t)+.07,.3,SHUO_IMPACT,0xFFFFFF,fade*.65f);
        }else{
            // All vertices remain between the two measured endpoints, even on sloped motion.
            double lo=t*.14,hi=1-t*.18;
            int sections=low?3:6;
            ribbon(g,p.from(),delta,side,up,lo,hi,0,-.08,r*.70,SHUO_STREAM,0x293847,fade*.27f,sections,t);
            for(int sign:new int[]{-1,1}){
                ribbon(g,p.from(),delta,side,up,lo,hi,sign*r*.62,.02,r*.48,SHUO_STREAM,white,fade*.83f,sections,t);
                if(!low)ribbon(g,p.from(),delta,side,up,lo+.035,hi-.035,sign*r*.91,.18,r*.17,SHUO_STREAM,blue,fade*.64f,sections,t);
            }
            // Foot skimming layer and a short suspended cutting edge leave the aiming centre clear.
            var center=p.from().add(delta.scale((lo+hi)*.5));double half=delta.length()*(hi-lo)*.5;
            // Broad painted blade feathers provide substance; thin filaments are only their trim.
            for(int sign:new int[]{-1,1})g.sheet(center.add(side.scale(sign*r*.55)).add(up.scale(.08)),along,
                up.add(side.scale(sign*.5)),half*.97,r*.94,SHUO_SLASH,white,fade*.86f);
            g.sheet(center.add(up.scale(-.60)),side,along,r*.70,half,SHUO_SLASH,blue,fade*.76f);
            if(!low){
                g.sheet(center.add(up.scale(.34)),along,side.add(up.scale(.4)),half,r*.25,SHUO_STREAM,white,fade*.64f);
                for(int sign:new int[]{-1,1}){
                    var fragment=p.from().add(delta.scale(.3+t*.25)).add(side.scale(sign*r*1.1));
                    g.sheet(fragment,along,up,Math.min(half*.3,.18),.10*(1-t)+.025,SHUO_STREAM,blue,fade*.68f);
                }
            }
        }
        return List.copyOf(g.quads);
    }
    private static void ribbon(Mesh g,Vec3 from,Vec3 delta,Vec3 side,Vec3 up,double lo,double hi,double lateral,double height,
            double width,int material,int color,float alpha,int n,float time){
        if(hi<=lo)return;
        for(int i=0;i<n;i++){
            double u=lo+(hi-lo)*i/n,v=lo+(hi-lo)*(i+1)/n;
            var a=from.add(delta.scale(u)).add(side.scale(lateral+Math.sin(Math.PI*u)*width*.28)).add(up.scale(height+Math.sin(u*Math.PI*2-time*2)*width*.14));
            var b=from.add(delta.scale(v)).add(side.scale(lateral+Math.sin(Math.PI*v)*width*.28)).add(up.scale(height+Math.sin(v*Math.PI*2-time*2)*width*.14));
            var w=up.scale(width*(1-time*.45));
            g.quad(a.subtract(w),b.subtract(w),b.add(w),a.add(w),material,color,alpha,(float)u,(float)v);
        }
    }
    private static final class Mesh {
        final List<Quad> quads=new ArrayList<>();
        void sheet(Vec3 at,Vec3 right,Vec3 up,double width,double height,int material,int color,float alpha){
            var r=right.normalize();var u=up.subtract(r.scale(up.dot(r))).normalize();if(r.lengthSqr()<.1||u.lengthSqr()<.1)return;
            r=r.scale(width);u=u.scale(height);
            quad(at.subtract(r).subtract(u),at.add(r).subtract(u),at.add(r).add(u),at.subtract(r).add(u),material,color,alpha,0,1);
        }
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int material,int color,float alpha,float lo,float hi){
            if(alpha<.005||quads.size()+2>MAX_QUADS||b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-12)return;
            alpha=ShuoBrightness.opacity(alpha);color=ShuoBrightness.tint(color);
            quads.add(new Quad(a,b,c,d,color,alpha,material,false,lo,hi,0,1));
            quads.add(new Quad(d,c,b,a,color,alpha,material,true,lo,hi,0,1));
        }
    }
}
