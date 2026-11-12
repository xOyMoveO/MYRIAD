package com.myriad.character.skills.shuo;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
/** World-only, depth-tested layered painted sprites. No model entities or generic legacy recipes. */
public final class ShuoSkillVfx {
    public static final int CHARGING=301,LEFT=302,RIGHT=303,DOWN=304,TRACE=305,CHASE=306,
        GUARDING=307,MARK=308,CONTACT=309,PARRY=310,SHEATHING=311,READY=312,EMPOWER=313,RIFT=314;
    public static final int MAX_QUADS=160;
    private ShuoSkillVfx(){}
    public static boolean owns(AbilityVfxPayload p){
        if(!p.valid()||p.theme()!=10||p.angle()!=(int)p.angle())return false;
        return switch((int)p.angle()){
            case CHARGING->p.kind()==CHARGE;case LEFT,RIGHT,DOWN->p.kind()==ARC;
            case TRACE,CHASE,RIFT->p.kind()==BEAM;case GUARDING,MARK->p.kind()==SHIELD;
            case CONTACT,PARRY,SHEATHING,READY,EMPOWER,ShuoDashVfx.DEPART,ShuoDashVfx.ARRIVE->p.kind()==BURST;default->false;
        };
    }
    public static AABB bounds(AbilityVfxPayload p){return new AABB(p.from(),p.to()).inflate(Math.max(2,p.radius()+1));}
    /** The caster sees the same world effects, including when looking through their own eyes. */
    public static boolean ownerHidden(AbilityVfxPayload p){return false;}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!Float.isFinite(age)||age<0||age>=p.life())return List.of();
        if(ShuoDashVfx.signal((int)p.angle()))return ShuoDashVfx.build(p,age,low);
        var g=new Mesh(low);float t=age/p.life(),fade=Math.min(1,(age+1)/1.5f)*Math.min(1,(p.life()-age)/Math.max(1,p.life()*.32f));
        Vec3 f=p.to().subtract(p.from()).multiply(1,0,1).normalize();if(f.lengthSqr()<.001)f=new Vec3(0,0,1);
        Vec3 side=new Vec3(f.z,0,-f.x),up=new Vec3(0,1,0),at=p.from();
        int signal=(int)p.angle();
        if(signal>=LEFT&&signal<=DOWN){
            double degrees=Math.clamp(p.from().distanceTo(p.to())*30,20,160),span=Math.toRadians(degrees),r=p.radius();
            // Sweep the authored image across the actual forward sector, bounded by its real reach.
            double head=Math.min(1,t/.42),tail=Math.max(0,(t-.55)/.45);int n=low?8:16;
            for(int i=0;i<n;i++){
                double u=i/(double)n,v=(i+1)/(double)n;if(v<tail||u>head)continue;
                double a=(u-.5)*span,b=(v-.5)*span;if(signal==RIGHT){a=-a;b=-b;}
                Vec3 da=f.scale(Math.cos(a)).add(side.scale(Math.sin(a))),db=f.scale(Math.cos(b)).add(side.scale(Math.sin(b)));
                double ya=signal==DOWN?.90-Math.cos(a)*.5:Math.sin(a)*(signal==RIGHT?-.50:.50);
                double yb=signal==DOWN?.90-Math.cos(b)*.5:Math.sin(b)*(signal==RIGHT?-.50:.50);
                g.quad(at.add(da.scale(r*.12)).add(0,ya,0),at.add(db.scale(r*.12)).add(0,yb,0),
                    at.add(db.scale(r)).add(0,yb,0),at.add(da.scale(r)).add(0,ya,0),SHUO_SLASH,0xFFFFFF,fade,(float)u,(float)v,0,1);
            }
            // An independently travelling silver edge rather than a second opaque crescent copy.
            double a=(head-.5)*span;if(signal==RIGHT)a=-a;
            Vec3 tip=at.add(f.scale(Math.cos(a)*r*.65)).add(side.scale(Math.sin(a)*r*.65));
            g.panel(tip.add(0,.12,0),side.add(up.scale(signal==DOWN?.9:.22)),up,r*.25,.30,SHUO_STREAM,0xDBF4FF,fade*.72f);
            if(signal==DOWN)g.star(at.add(f.scale(r*.55)),side,up,.55+.35*t,fade*.62f);
            // A tilted, authored blade sheet stays readable beside the player as well as above.
            // It remains within the actual forward reach; the horizontal sector still shows width.
            var ribbonSide=side.add(up.scale(signal==RIGHT?-.42:signal==DOWN?.06:.42));
            g.quads.addAll(ShuoSlashMesh.build(at.add(f.scale(r*.43)).add(0,.12,0),ribbonSide,
                f.scale(.58).add(up.scale(.82)),Math.sin(span*.5)*r*.72,r*.30,t,low));
        }else if(signal==CHARGING){
            double rise=.20+t*.75;int n=low?2:4;
            for(int i=0;i<n;i++){
                double a=i*Math.PI*2/n+t*.55;Vec3 radial=side.scale(Math.cos(a)).add(f.scale(Math.sin(a)));
                var origin=at.add(radial.scale(.45*(1-t*.45))).add(0,.35+rise*.45,0);
                g.panel(origin,up.add(radial.scale(.18)),radial,.65+.30*t,.38,SHUO_STREAM,i%2==0?0xFFFFFF:0xB6DEFF,fade*(.32f+.40f*t));
            }
            g.star(at.add(f.scale(.25)).add(0,.85,0),side,up,.25+.28*t,fade*(.3f+.4f*t));
        }else if(signal==GUARDING){
            int n=low?6:12;double span=Math.toRadians(130);
            for(int i=0;i<n;i++){
                double u=i/(double)n,v=(i+1)/(double)n,a=(u-.5)*span,b=(v-.5)*span;
                var x=at.add(f.scale(Math.cos(a)*.95)).add(side.scale(Math.sin(a)*.95));
                var y=at.add(f.scale(Math.cos(b)*.95)).add(side.scale(Math.sin(b)*.95));
                g.quad(x.add(0,-.85,0),y.add(0,-.85,0),y.add(0,.85,0),x.add(0,.85,0),SHUO_GUARD,0xFFFFFF,fade,(float)u,(float)v,0,1);
            }
            g.panel(at.add(f.scale(1.01)),side,up,.50,.12,SHUO_STREAM,0xEAF8FF,fade*.72f);
            g.panel(at.add(f.scale(.98)),side,up,1.35,1.35,SHUO_GUARD,0xDBF0FF,fade*.85f);
        }else if(signal==RIFT){
            var delta=p.to().subtract(at);double length=delta.length();if(length<.02)return List.of();
            // A straight upright cut, not a wider generic fan; full reach is visible immediately.
            var center=at.add(delta.scale(.5));var along=delta.normalize();
            g.panel(center,along,up,length*.5,.50,SHUO_STREAM,0xFFFFFF,fade);
            g.panel(center,along,side,length*.5,p.radius(),SHUO_STREAM,0xBDDFFF,fade*.7f);
            if(!low)g.panel(center.add(up.subtract(along.scale(up.dot(along))).scale(.12)),along,up,length*.5,.18,SHUO_STREAM,0xFFFFFF,fade*.85f);
        }else if(signal==MARK){
            double pulse=.65+.12*Math.sin(age*.55);
            g.panel(at,side.add(up.scale(.9)),up.subtract(side.scale(.9)),.64,.35,SHUO_STREAM,0xD9EBFF,fade*(float)pulse);
            if(!low)g.panel(at.add(f.scale(.025)),side.subtract(up.scale(.9)),up.add(side.scale(.9)),.53,.28,SHUO_STREAM,0x7C95AA,fade*.38f);
        }else if(signal==PARRY){
            g.panel(at,side,up,1.25,1.15,SHUO_GUARD,0xFFFFFF,fade);
            g.star(at.add(f.scale(.05)),side,up,.35+t*.65,fade);
        }else if(signal==SHEATHING){
            // No early explosion: tighten to the throat, then a tiny metallic glint near completion.
            g.panel(at,side,up,.45*(1-t*.5),.16,SHUO_STREAM,0xD5E8F5,fade*.55f);
            if(t>.55)g.star(at,side,up,.18,fade);
        }else if(signal==READY||signal==EMPOWER){
            g.panel(at,up,side,.54,.30,SHUO_STREAM,signal==READY?0xC1DBF5:0xFFFFFF,fade*.65f);
            g.star(at.add(0,.17,0),side,up,.25+t*.16,fade*.8f);
        }else {g.star(at,side,up,p.radius()*(.50+.45*Math.sin(Math.PI*t)),fade);}
        return List.copyOf(g.quads);
    }
    private static final class Mesh {
        final List<Quad> quads=new ArrayList<>();final boolean low;
        Mesh(boolean low){this.low=low;}
        void panel(Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,int rgb,float alpha){
            var r=right.normalize();var u=up.subtract(r.scale(up.dot(r))).normalize();if(r.lengthSqr()<.1||u.lengthSqr()<.1)return;
            r=r.scale(w);u=u.scale(h);quad(at.subtract(r).subtract(u),at.add(r).subtract(u),at.add(r).add(u),at.subtract(r).add(u),material,rgb,alpha,0,1,0,1);
        }
        void star(Vec3 at,Vec3 side,Vec3 up,double size,float alpha){
            panel(at,side,up,size,size,SHUO_IMPACT,0xFFFFFF,alpha);
            if(!low){var normal=side.cross(up).normalize();panel(at.add(normal.scale(.035)),normal,up,size*.72,size*.72,SHUO_IMPACT,0xC7E9FF,alpha*.72f);}
        }
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int material,int rgb,float alpha,float u,float v,float y,float z){
            if(alpha<.005||quads.size()+2>MAX_QUADS||b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-12)return;
            alpha=ShuoBrightness.opacity(alpha);rgb=ShuoBrightness.tint(rgb);quads.add(new Quad(a,b,c,d,rgb,alpha,material,false,u,v,y,z));
            quads.add(new Quad(d,c,b,a,rgb,alpha,material,true,u,v,y,z));
        }
    }
}
