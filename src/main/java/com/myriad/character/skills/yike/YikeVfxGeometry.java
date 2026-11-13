package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.skills.yike.YikeVfx.*;

/** Open petal planes, faceted crystals, continuous ribbons and six distinct hit accents. */
public final class YikeVfxGeometry {
    private static final int WHITE=0xFFF3DC,CORAL=0xFF6C78,GOLD=0xF3C879,VIOLET=0x9B83F4;
    private static final Vec3 UP=new Vec3(0,1,0);
    private final List<Quad> mesh=new ArrayList<>();
    private final float fade;private final double age,progress;private final boolean reduced;
    private YikeVfxGeometry(AbilityVfxPayload p,float age,boolean reduced){this.age=age;this.reduced=reduced;progress=age/p.life();fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||age<0||age>=p.life())return List.of();var g=new YikeVfxGeometry(p,age,reduced);g.draw(p);return List.copyOf(g.mesh);
    }
    private void face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha){
        if(mesh.size()+2>192||alpha<=0)return;float a0=(float)Math.clamp(alpha*fade,0,1);
        mesh.add(new Quad(a,b,c,d,rgb,a0));mesh.add(new Quad(d,c,b,a,rgb,a0));
    }
    private void ribbon(Vec3 a,Vec3 b,Vec3 normal,double width,int color,double alpha){
        Vec3 dir=b.subtract(a);if(dir.lengthSqr()<1E-9)return;Vec3 side=dir.cross(normal);
        if(side.lengthSqr()<1E-9)side=dir.cross(new Vec3(1,0,0));if(side.lengthSqr()<1E-9)return;side=side.normalize().scale(width*.5);
        face(a.add(side),a.subtract(side),b.subtract(side),b.add(side),color,alpha);
    }
    private void crystal(Vec3 center,Vec3 direction,double length,double width){
        Vec3 f=direction.normalize(),side=f.cross(UP);if(side.lengthSqr()<1E-8)side=f.cross(new Vec3(1,0,0));side=side.normalize();Vec3 vert=side.cross(f).normalize();
        Vec3 front=center.add(f.scale(length*.65)),rear=center.subtract(f.scale(length*.35));
        for(int i=0;i<4;i++){double a=i*Math.PI/2,b=a+Math.PI/2;
            Vec3 x=center.add(side.scale(Math.cos(a)*width)).add(vert.scale(Math.sin(a)*width));
            Vec3 y=center.add(side.scale(Math.cos(b)*width)).add(vert.scale(Math.sin(b)*width));
            face(front,x,y,y,i%2==0?WHITE:GOLD,.95);face(rear,y,x,x,i%2==0?CORAL:VIOLET,.8);
        }
    }
    private void arc(Vec3 center,Vec3 axis,double radius,double start,double sweep,int color,double alpha,double width){
        Vec3 side=axis.cross(new Vec3(0,0,1));if(side.lengthSqr()<1E-8)side=axis.cross(new Vec3(1,0,0));side=side.normalize();Vec3 other=axis.cross(side).normalize();
        int count=reduced?4:8;Vec3 prior=center.add(side.scale(Math.cos(start)*radius)).add(other.scale(Math.sin(start)*radius));
        for(int i=1;i<=count;i++){double a=start+sweep*i/count;Vec3 next=center.add(side.scale(Math.cos(a)*radius)).add(other.scale(Math.sin(a)*radius));ribbon(prior,next,axis,width,color,alpha);prior=next;}
    }
    private void petal(Vec3 center,Vec3 along,Vec3 outward,double length,double reach,int rgb,double alpha){
        Vec3 side=along.cross(outward).normalize().scale(reach*.18);
        Vec3 root=center.add(outward.scale(reach*.15)),shoulder=center.add(outward.scale(reach*.72)).add(along.scale(length*.38)),tip=center.add(outward.scale(reach)).add(along.scale(length));
        face(root,shoulder.add(side),tip,shoulder.subtract(side),rgb,alpha);
        ribbon(root,tip,side,.026,GOLD,alpha*.75);
    }
    private void flash(Vec3 at,double radius,boolean heavy){
        double t=progress;Vec3 axis=new Vec3(0,0,1);
        if(t<.20)crystal(at.add(0,.18,0),axis,Math.min(radius*.8,.9)*(1-t),Math.min(radius*.25,.4));
        int n=reduced?3:6;
        for(int i=0;i<n;i++){double a=i*Math.PI*2/n+.12;
            Vec3 out=new Vec3(Math.cos(a),.12+(i%2)*.2,Math.sin(a)).normalize();
            petal(at,UP,out,.15+radius*.36*t,radius*(.18+.78*t),i%2==0?CORAL:WHITE,(1-t)*.8);
        }
        if(!reduced)for(int i=0;i<6;i++){
            double a=i*2.4;Vec3 out=new Vec3(Math.cos(a),.3+i%2*.3,Math.sin(a));Vec3 from=at.add(out.scale(radius*(.3+.6*t)));
            ribbon(from,from.add(out.scale(.12+.18*t)),UP,.035,i%2==0?VIOLET:GOLD,(1-t)*.7);
        }
        if(heavy&&t>.2)for(int i=0;i<3;i++)arc(at.add(0,i*.15,0),UP,radius*Math.min(1,.25+t),i*2.1,.9,i%2==0?CORAL:VIOLET,(1-t)*.5,.04);
    }
    private void draw(AbilityVfxPayload p){
        Vec3 at=p.from(),end=p.to(),f=end.subtract(at);if(f.lengthSqr()<1E-8)f=new Vec3(0,0,1);f=f.normalize();
        Vec3 side=f.cross(UP);if(side.lengthSqr()<1E-8)side=new Vec3(1,0,0);else side=side.normalize();Vec3 vert=side.cross(f).normalize();
        int signal=Math.round(p.angle());double radius=Math.max(.03,p.radius());
        if(p.kind()==9){
            double width=p.radius();boolean basic=signal==BASIC,heavy=signal==HEAVY;
            crystal(end,f,heavy?.9:basic?.15:.45,width);
            // All tails connect measured previous/current positions; no guessed one-block spacing.
            ribbon(at,end,UP,width*(basic?.8:1.2),CORAL,.75);
            ribbon(at,end,side,width*.65,WHITE,.8);
            if(!basic){ribbon(at.add(side.scale(width)),end,vert,width*.4,VIOLET,.5);ribbon(at.subtract(side.scale(width)),end,vert,width*.3,GOLD,.55);}
            if(heavy)for(int i=0;i<3;i++){double a=i*Math.PI*2/3+age*.16;Vec3 outward=side.scale(Math.cos(a)).add(vert.scale(Math.sin(a)));petal(end.subtract(f.scale(.25)),f,outward,.7,.3,CORAL,.8);}
            return;
        }
        switch(signal){
            case BASIC -> {crystal(at,f,.2,.08);ribbon(at.subtract(side.scale(.15)),at.add(side.scale(.15)),vert,.035,WHITE,.8);}
            case IMPACT,DENIAL_BURST,HEAVY_IMPACT,BEAM_HIT,MARK_BURST,DISSIPATE -> flash(at,radius,signal==HEAVY_IMPACT);
            case DENIAL_WARNING,HEAVY_WARNING -> {
                int n=signal==HEAVY_WARNING?8:6;
                for(int i=0;i<n;i++){
                    double a=i*Math.PI*2/n;arc(at,UP,radius,a,.28,GOLD,.85,.025);
                    Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));double inner=radius*(.75-.5*progress);
                    ribbon(at.add(out.scale(inner)),at.add(out.scale(inner+.13)),UP,.025,CORAL,.9);
                }
                if(signal==HEAVY_WARNING){ribbon(at.add(-.18,.02,0),at.add(.18,.02,0),UP,.02,WHITE,.8);ribbon(at.add(0,.02,-.18),at.add(0,.02,.18),UP,.02,WHITE,.8);}
            }
            case MARK1,MARK2 -> {
                int count=signal==MARK1?1:2;
                for(int i=0;i<count;i++)arc(at.add(side.scale(.5)),f,radius*.55,-.7+i*.7,.45,GOLD,.9,.035);
            }
            case BARRIER -> {
                for(int i=0;i<3;i++){
                    double a=i*Math.PI*2/3;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));Vec3 base=at.add(out.scale(.48));
                    Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a)).scale(.24);
                    face(base.add(tangent).add(0,-.4,0),base.subtract(tangent).add(0,-.4,0),base.subtract(tangent.scale(.75)).add(0,.4,0),base.add(tangent.scale(.75)).add(0,.4,0),VIOLET,.28);
                    ribbon(base.add(tangent).add(0,-.4,0),base.add(tangent.scale(.75)).add(0,.4,0),out,.025,GOLD,.85);
                }
            }
            case FIXED,MOBILE,DEPLOY -> {
                // Ground-level supports leave the centre and combat silhouettes open.
                Vec3 ground=at.add(0,-1.5,0);
                for(int i=0;i<3;i++)arc(ground,UP,signal==MOBILE?.43:.65,i*2.1,.5,GOLD,.45,.022);
                if(signal==DEPLOY)for(int i=0;i<3;i++){double a=i*2.1;Vec3 out=side.scale(Math.cos(a)).add(vert.scale(Math.sin(a)));ribbon(at.add(out.scale(.3)),end,vert,.025,GOLD,.7);}
            }
            case MUZZLE,LAST_MUZZLE -> {
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=side.scale(Math.cos(a)).add(vert.scale(Math.sin(a)));petal(at,f,out,.4*(1-progress),radius*(1-progress*.7),CORAL,.65);}
                crystal(at.add(f.scale(.25)),f,signal==LAST_MUZZLE?.65:.4,.08);
                arc(at.add(f.scale(.1)),f,.18*(1-progress*.5),age*.1,Math.PI*1.5,GOLD,.8,.02);
            }
            case BEAM_CHARGE -> {
                double s=.14+.4*(1-progress);for(int i:new int[]{-1,1})ribbon(at.add(side.scale(i*s)),end.add(side.scale(i*.11)),vert,.025,GOLD,.9);
                crystal(at.lerp(end,.3),f,.15+progress*.3,.08);
            }
            case BEAM -> {
                double length=at.distanceTo(end);
                ribbon(at,end,UP,.16,WHITE,.95);ribbon(at,end,side,.11,WHITE,.9);
                ribbon(at.add(vert.scale(.08)),end.add(vert.scale(.08)),UP,.36,CORAL,.4);
                for(int i:new int[]{-1,1})ribbon(at.add(side.scale(i*.28)),end.add(side.scale(i*.28)),vert,.035,GOLD,.65);
                int n=reduced?3:7;
                for(int i=0;i<n;i++){
                    double t=((i+(p.started()+age)/1.0)%8)/8.0,a=(p.started()+age)*.45+i*Math.PI;
                    Vec3 offset=side.scale(Math.cos(a)*.32).add(vert.scale(Math.sin(a)*.32));
                    Vec3 a0=at.add(f.scale(length*t)).add(offset),b0=at.add(f.scale(Math.min(length,length*t+.9))).add(offset);
                    ribbon(a0,b0,side,.07,i%2==0?VIOLET:CORAL,.65);
                }
            }
            default -> flash(at,radius,false);
        }
    }
}
