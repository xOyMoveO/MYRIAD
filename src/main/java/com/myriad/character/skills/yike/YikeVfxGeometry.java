package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.skills.yike.YikeVfx.*;

/** Faceted prismatic stages. Every swept tail and beam stays inside its confirmed endpoints. */
public final class YikeVfxGeometry {
    public static final int WHITE=0xFFF3DC,CORAL=0xFF6C78,GOLD=0xF3C879,VIOLET=0x9B83F4;
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private final List<Quad> mesh=new ArrayList<>();
    private final float fade;private final double age,progress,time;private final boolean reduced;
    private YikeVfxGeometry(AbilityVfxPayload p,float age,boolean reduced){
        this.age=age;this.reduced=reduced;progress=age/p.life();time=p.started()+age;
        fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));
    }
    public static int signal(AbilityVfxPayload p){return (int)Math.floor(p.angle()+1E-4);}
    /** Preparation packets carry authoritative windup progress in the fractional signal, not receipt age. */
    public static double preparation(AbilityVfxPayload p){return Math.clamp((p.angle()-signal(p))/.4,0,1);}
    public static boolean movingBody(AbilityVfxPayload p){return p.theme()==THEME&&p.kind()==AbilityVfxGeometry.FLIGHT&&p.instance()>0;}
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload p){
        if(!movingBody(p)||p.from().distanceToSqr(p.to())<1E-10)return null;
        return new AbilityVfxPayload(p.owner(),-1,THEME,p.variant(),AbilityVfxGeometry.TRAIL,p.from(),p.to(),p.radius(),p.angle(),signal(p)==BASIC?4:6,p.started(),false);
    }
    private static boolean sameLane(AbilityVfxPayload a,AbilityVfxPayload b){
        if(a.theme()!=THEME||b.theme()!=THEME||a.owner()!=b.owner()||a.variant()!=b.variant()||a.anchor()!=b.anchor()||a.instance()!=b.instance())return false;
        int s=signal(a),t=signal(b);
        if(s==BEAM&&t==BEAM_CHARGE||s==MUZZLE&&t==MUZZLE_CHARGE||s==LAST_MUZZLE&&t==MUZZLE_CHARGE)return true;
        return a.kind()==b.kind()&&s==t;
    }
    private static boolean refreshed(AbilityVfxPayload p){int s=signal(p);return s==HEAVY_WARNING||s==BEAM||s==BEAM_CHARGE||s==MUZZLE_CHARGE||s==DEPLOY||s==MARK1||s==MARK2;}
    public static boolean replaces(AbilityVfxPayload incoming,AbilityVfxPayload old){return sameLane(incoming,old)&&incoming.started()>=old.started()&&(refreshed(incoming)||refreshed(old));}
    public static boolean obsolete(AbilityVfxPayload incoming,AbilityVfxPayload old){return sameLane(incoming,old)&&incoming.started()<old.started()&&(refreshed(incoming)||refreshed(old));}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||p.theme()!=THEME||age<0||age>=p.life()||p.kind()==AbilityVfxGeometry.CANCEL)return List.of();
        var g=new YikeVfxGeometry(p,age,reduced);g.draw(p);return List.copyOf(g.mesh);
    }
    private void face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha){
        if(mesh.size()+2>(reduced?96:192)||alpha<=0)return;
        if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        float opacity=(float)Math.clamp(alpha*fade,0,1);if(opacity<=0)return;
        mesh.add(new Quad(a,b,c,d,rgb,opacity));mesh.add(new Quad(d,c,b,a,rgb,opacity));
    }
    private static Vec3 side(Vec3 f){Vec3 s=f.cross(UP);return s.lengthSqr()<1E-8?X:s.normalize();}
    private void ribbon(Vec3 a,Vec3 b,Vec3 normal,double width,int color,double alpha){
        Vec3 dir=b.subtract(a);if(dir.lengthSqr()<1E-10)return;Vec3 s=dir.cross(normal);
        if(s.lengthSqr()<1E-10)s=side(dir.normalize());s=s.normalize().scale(width*.5);
        face(a.add(s),a.subtract(s),b.subtract(s),b.add(s),color,alpha);
    }
    private void crystal(Vec3 tip,Vec3 f,double length,double width,double alpha){
        Vec3 s=side(f),v=s.cross(f).normalize(),center=tip.subtract(f.scale(length*.62)),rear=tip.subtract(f.scale(length));
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=a+Math.PI/2;Vec3 x=center.add(s.scale(Math.cos(a)*width)).add(v.scale(Math.sin(a)*width)),y=center.add(s.scale(Math.cos(b)*width)).add(v.scale(Math.sin(b)*width));
            face(tip,x,y,y,i%2==0?WHITE:GOLD,alpha);face(rear,y,x,x,i%2==0?CORAL:VIOLET,alpha*.8);
        }
    }
    private void arc(Vec3 center,Vec3 axis,double radius,double start,double sweep,int color,double alpha,double width){
        Vec3 s=axis.cross(Z);if(s.lengthSqr()<1E-8)s=axis.cross(X);s=s.normalize();Vec3 v=axis.cross(s).normalize();
        int n=reduced?3:6;Vec3 prior=center.add(s.scale(Math.cos(start)*radius)).add(v.scale(Math.sin(start)*radius));
        for(int i=1;i<=n;i++){double a=start+sweep*i/n;Vec3 next=center.add(s.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius));ribbon(prior,next,axis,width,color,alpha);prior=next;}
    }
    private void petal(Vec3 root,Vec3 along,Vec3 out,double height,double reach,int color,double alpha){
        Vec3 s=along.cross(out).normalize().scale(reach*.2),shoulder=root.add(out.scale(reach*.65)).add(along.scale(height*.38)),tip=root.add(out.scale(reach)).add(along.scale(height));
        face(root,shoulder.add(s),tip,shoulder.subtract(s),color,alpha);
        ribbon(root,tip,s,.018,GOLD,alpha*.7);
    }
    /** 0-.05s ivory point, .05-.18s coral facets, .18-.45s fragments, .45-.70s heavy afterglow. */
    private void impact(Vec3 at,double radius,int signal){
        boolean heavy=signal==HEAVY_IMPACT,denial=signal==DENIAL_BURST;
        double r=radius,petalT=Math.clamp((age-1)/2.6,0,1),energy=1-progress;
        if(age<1.8){double shrink=1-age/2;crystal(at.add(0,.25*r,0),UP,Math.min(.9,r*.45)*shrink,Math.min(.28,r*.18)*shrink,.98);}
        if(age>=1&&age<9){int n=reduced?3:6;double alpha=Math.min(1,(age-1)*1.5)*Math.min(1,(9-age)/4)*.62;
            for(int i=0;i<n;i++){double a=i*Math.PI*2/n+.17;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));
                petal(at.add(out.scale(r*.08)),UP,out,r*(denial?.29:.12+.17*(i%2))*petalT,r*(.2+.62*petalT),i%3==0?WHITE:CORAL,alpha);}
        }
        if(age>=3.6){int n=reduced?4:8;double t=Math.clamp((age-3.6)/(heavy?10.4:4.4),0,1);
            for(int i=0;i<n;i++){double a=i*2.399963;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),from=at.add(out.scale(r*(.45+.34*t))).add(0,r*(.09+(i%3)*.05)*(1-t),0);
                ribbon(from,from.add(out.scale(r*.12*(1-t))).add(0,.04*(1-t),0),UP,.024,i%2==0?VIOLET:GOLD,energy*.72);}
        }
        if(heavy&&age>=1&&age<9)for(int i=0;i<(reduced?2:3);i++)arc(at.add(0,.09+i*.17,0),UP,r*(.24+.62*petalT),i*2.1,.7,i%2==0?CORAL:VIOLET,energy*.35,.045);
    }
    private void tails(Vec3 from,Vec3 to,double width,Vec3 f,double alpha){
        Vec3 s=side(f),v=s.cross(f).normalize();
        // Three continuous layers use the real swept segment. No extrapolated tail across a collision.
        // Fixed transverse axes are essential: a taper-derived line normal can protrude past an endpoint.
        tailLayer(from,to,s.scale(width*.90),s.scale(width*.70),CORAL,.50*alpha);
        tailLayer(from.add(s.scale(width*.8)),to.add(s.scale(width*.12)),v.scale(width*.32),v.scale(width*.15),VIOLET,.44*alpha);
        tailLayer(from.subtract(s.scale(width*.65)),to.subtract(s.scale(width*.10)),v.scale(width*.15),v.scale(width*.08),GOLD,.62*alpha);
    }
    private void tailLayer(Vec3 from,Vec3 to,Vec3 widthFrom,Vec3 widthTo,int color,double alpha){
        face(from.add(widthFrom),from.subtract(widthFrom),to.subtract(widthTo),to.add(widthTo),color,alpha);
    }
    private void draw(AbilityVfxPayload p){
        Vec3 at=p.from(),end=p.to(),delta=end.subtract(at),f=delta.lengthSqr()<1E-10?Z:delta.normalize(),s=side(f),v=s.cross(f).normalize();
        int signal=signal(p);double r=Math.max(.03,p.radius());
        if(p.kind()==AbilityVfxGeometry.TRAIL){
            double alpha=Math.pow(1-progress,1.5);
            if(signal==BASIC){
                Vec3 start=end.lerp(at,.22);tailLayer(start,end,s.scale(.014),s.scale(.006),CORAL,.55*alpha);
                for(int i=0;i<2;i++){Vec3 center=start.lerp(end,.35+i*.32).add(v.scale(i==0?.025:-.025));double length=start.distanceTo(end)*.04;
                    face(center.add(f.scale(length)),center.add(s.scale(.012)),center.subtract(f.scale(length)),center.subtract(s.scale(.012)),VIOLET,.6*alpha);}
            }else tails(at,end,r,f,alpha);
            return;
        }
        if(p.kind()==AbilityVfxGeometry.FLIGHT){
            Vec3 head=at.lerp(end,Math.clamp(age,0,1));boolean basic=signal==BASIC,heavy=signal==HEAVY;
            double length=heavy?.9:basic?.16:.43;crystal(head,f,length,r,basic?.85:.95);
            if(!basic)for(int i=0;i<3;i++){double a=i*Math.PI*2/3+time*.15;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                petal(head.subtract(f.scale(length*.72)),f,out,length*.40,heavy?.29:.13,CORAL,.78);}
            return;
        }
        switch(signal){
            case BASIC -> {crystal(at.add(f.scale(.12)),f,.18,.045,.8);ribbon(at.subtract(f.scale(.14)),at,f,.025,CORAL,.55);}
            case BASIC_HIT -> {crystal(at.add(0,.08,0),UP,.16*(1-progress),.075*(1-progress),.9);
                if(!reduced)for(int i=0;i<2;i++){Vec3 d=new Vec3(i==0?1:-1,.5,0).scale(.11*progress);ribbon(at.add(d),at.add(d.scale(1.6)),Z,.02,VIOLET,1-progress);}}
            case IMPACT,DENIAL_BURST,HEAVY_IMPACT,BEAM_HIT,MARK_BURST -> impact(at,r,signal);
            case DISSIPATE -> {for(int i=0;i<(reduced?3:5);i++){double a=i*2.4;Vec3 out=new Vec3(Math.cos(a),.25,Math.sin(a));ribbon(at.add(out.scale(r*.2)),at.add(out.scale(r*(.3+.5*progress))),UP,.025,i%2==0?VIOLET:GOLD,.65*(1-progress));}}
            case DENIAL_WARNING,HEAVY_WARNING -> {
                int n=signal==HEAVY_WARNING?8:6;double contraction=signal==DENIAL_WARNING?progress:((time%20)/20);
                for(int i=0;i<n;i++){double a=i*Math.PI*2/n;arc(at,UP,r-.02,a,.22,GOLD,.82,.025);
                    Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));double inner=r*(.78-.42*contraction);
                    ribbon(at.add(out.scale(inner)),at.add(out.scale(inner+.12)),UP,.024,CORAL,.66);}
                if(signal==HEAVY_WARNING){ribbon(at.add(-.15,.015,0),at.add(.15,.015,0),UP,.019,WHITE,.7);ribbon(at.add(0,.015,-.15),at.add(0,.015,.15),UP,.019,WHITE,.7);}
            }
            case MARK1,MARK2 -> {
                int n=signal==MARK1?1:2;double shimmer=.75+.12*Math.sin(time*.25);
                for(int i=0;i<n;i++)arc(at.add(s.scale(.45)),f,r*.48,-.8+i*2.1,.7,GOLD,shimmer,.028);
            }
            case BARRIER -> {
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),base=at.add(out.scale(.5)),tangent=new Vec3(-Math.sin(a),0,Math.cos(a)).scale(.23);
                    Vec3 a0=base.add(tangent).add(0,-.4,0),b=base.subtract(tangent).add(0,-.4,0),c=base.subtract(tangent.scale(.75)).add(0,.4,0),d=base.add(tangent.scale(.75)).add(0,.4,0);
                    face(a0,b,c,d,VIOLET,.22);ribbon(a0,d,out,.023,GOLD,.8);ribbon(b,c,out,.023,GOLD,.8);ribbon(c,d,out,.018,WHITE,.7);
                }
            }
            case FIXED,MOBILE -> {
                boolean mobile=signal==MOBILE;double rr=mobile?.43:.65;int n=mobile?2:3;
                for(int i=0;i<n;i++){double a=i*Math.PI*2/n;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),base=at.add(out.scale(rr));
                    arc(at,UP,rr,a,.36,GOLD,.45,.022);ribbon(base,base.add(0,mobile?.06:.16,0),out,.022,WHITE,.55);
                    if(mobile)ribbon(base.subtract(f.scale(.13)),base.add(f.scale(.07)),UP,.025,CORAL,.38);}
            }
            case DEPLOY -> {
                double t=preparation(p),unlock=Math.clamp(t/.16,0,1),open=Math.clamp((t-.15)/.57,0,1),ready=Math.clamp((t-.78)/.22,0,1);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a))),base=at.add(out.scale(.08+.22*open));
                    ribbon(base,base.add(f.scale(.20+.18*ready)),v,.018,GOLD,(.3+.6*ready)*unlock);}
                if(ready>0)crystal(at.add(f.scale(.35)),f,.2,.045,ready*.8);
            }
            case MUZZLE_CHARGE,BEAM_CHARGE -> {
                double t=preparation(p),rr=(signal==BEAM_CHARGE?.34:.2)*(1-t*.65);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    ribbon(at.add(out.scale(rr)).subtract(f.scale(.08)),at.add(out.scale(rr*.32)).add(f.scale(.2)),v,.018,GOLD,.62);
                    arc(at.add(f.scale(.16)),f,rr,a,.46,CORAL,.45,.025);}
                if(t>.55)crystal(at.add(f.scale(.24)),f,.11,.025,.5+t*.35);
            }
            case MUZZLE,LAST_MUZZLE -> {
                if(age<1.4)crystal(at.add(f.scale(.35)),f,signal==LAST_MUZZLE?.4:.27,.045,.95);
                double t=Math.clamp(age/5,0,1);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    ribbon(at.add(out.scale(.08)).subtract(f.scale(.16+t*.3)),at.add(f.scale(.18)).add(out.scale(.06)),v,.025,i%2==0?VIOLET:GOLD,(1-t)*.65);}
            }
            case BEAM,BEAM_END -> {
                double length=at.distanceTo(end),stop=signal==BEAM_END?Math.pow(1-progress,1.7):1;
                Vec3 tip=at.add(f.scale(length*stop));double power=signal==BEAM_END?1-progress:1;
                ribbon(at,tip,v,.055*power,WHITE,.94*power);ribbon(at,tip,s,.043*power,WHITE,.88*power);
                ribbon(at,tip,v,.21*power,CORAL,.42*power);
                for(int sign:new int[]{-1,1})ribbon(at.add(s.scale(sign*.24*power)),tip.add(s.scale(sign*.24*power)),v,.023*power,GOLD,.62*power);
                int n=reduced?3:7;
                for(int i=0;i<n;i++){double t=((time/8.0+i/(double)n)%1+1)%1,a=time*.32+i*Math.PI*.8;
                    Vec3 offset=s.scale(Math.cos(a)*.27*power).add(v.scale(Math.sin(a)*.27*power));
                    Vec3 a0=at.add(f.scale(length*stop*t)).add(offset),b0=at.add(f.scale(Math.min(length*stop,length*stop*t+.8))).add(offset);
                    ribbon(a0,b0,v,.035*power,i%2==0?VIOLET:CORAL,.65*power);}
            }
            default -> {}
        }
    }
}
