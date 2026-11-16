package com.myriad.character.skills.yike;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.ability.RosterProjectileModels;
import com.myriad.character.ability.RosterVfxTuning;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.skills.yike.YikeVfx.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Three-wing photon-array stages. Swept trails and collimated rails retain confirmed endpoints. */
public final class YikeVfxGeometry {
    public static final int CORE=0xF1FCFF,PHOTON=0x5DDEFF,COBALT=0x18456B,GRAPHITE=0x15222E,ACCENT=0xA88AFF,PULSE=0xFF6AAE;
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private static final RosterProjectileModels MODELS=RosterProjectileModels.load("yike_projectiles_mesh.json");
    private final List<Quad> mesh=new ArrayList<>();
    private final float fade;private final double age,progress,time;private final boolean reduced;private final int limit;
    private YikeVfxGeometry(AbilityVfxPayload p,float age,boolean reduced){
        this.age=age;this.reduced=reduced;progress=age/p.life();time=p.started()+age;
        fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));
        limit=p.kind()==AbilityVfxGeometry.FLIGHT?(reduced?192:512):(reduced?96:192);
    }
    public static int signal(AbilityVfxPayload p){return (int)Math.floor(p.angle()+1E-4);}
    public static boolean continuousBeam(AbilityVfxPayload p){return p.theme()==THEME&&p.kind()==AbilityVfxGeometry.BEAM&&(signal(p)==BEAM||signal(p)==BEAM_END);}
    /** Preparation packets carry authoritative windup progress in the fractional signal, not receipt age. */
    public static double preparation(AbilityVfxPayload p){return Math.clamp((p.angle()-signal(p))/.4,0,1);}
    public static boolean movingBody(AbilityVfxPayload p){return p.theme()==THEME&&p.kind()==AbilityVfxGeometry.FLIGHT&&p.instance()>0;}
    public static String modelKey(int signal,boolean reduced){return (signal==BASIC?"basic":signal==HEAVY?"heavy":"shell")+(reduced?"_lod":"");}
    public static int modelFaces(int signal,boolean reduced){return MODELS.faces(modelKey(signal,reduced));}
    public static int modelParts(int signal,boolean reduced){return MODELS.parts(modelKey(signal,reduced));}
    public static double modelLength(int signal){return (signal==BASIC?.22:signal==HEAVY?1.2:.62)*RosterVfxTuning.PROJECTILE_SCALE;}
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
        if(p.kind()==AbilityVfxGeometry.FLIGHT&&age>=1)return List.of();
        var g=new YikeVfxGeometry(p,age,reduced);g.draw(p);return RosterVfxTuning.brighten(g.mesh);
    }
    private void face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha){
        face(a,b,c,d,rgb,alpha,YIKE_HD_VEIL);
    }
    private void face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha,int material){
        if(mesh.size()+2>limit||alpha<=0)return;
        if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        float opacity=(float)Math.clamp(alpha*fade,0,1);if(opacity<=0)return;
        // Authored PNGs already contain the white/ice-blue/cobalt palette.
        mesh.add(new Quad(a,b,c,d,0xFFFFFF,opacity,material));mesh.add(new Quad(d,c,b,a,0xFFFFFF,opacity,material,true));
    }
    private void image(Vec3 center,Vec3 right,Vec3 up,double halfWidth,double halfHeight,int material,double alpha){
        Vec3 x=right.normalize().scale(halfWidth),y=up.normalize().scale(halfHeight);
        face(center.subtract(x).subtract(y),center.add(x).subtract(y),center.add(x).add(y),center.subtract(x).add(y),CORE,alpha,material);
    }
    private static Vec3 side(Vec3 f){Vec3 s=f.cross(UP);return s.lengthSqr()<1E-8?X:s.normalize();}
    /** Constant white texel, not a mostly transparent trail sprite: a visible volumetric beam. */
    private void beamFace(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double alpha){
        if(mesh.size()+2>limit||alpha<=0||b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        float opacity=(float)Math.clamp(alpha*fade,0,1);if(opacity<=0)return;
        mesh.add(new Quad(a,b,c,d,rgb,opacity,0));
        mesh.add(c.equals(d)?new Quad(c,b,a,a,rgb,opacity,0,true)
                :new Quad(d,c,b,a,rgb,opacity,0,true));
    }
    private void beamTube(Vec3 from,Vec3 to,Vec3 f,double radius,int rgb,double alpha,boolean capped){
        if(from.distanceToSqr(to)<1E-10||radius<=0)return;
        var s=side(f);var v=s.cross(f).normalize();
        for(int i=0;i<6;i++){
            double a=i*Math.PI/3,b=(i+1)*Math.PI/3;
            Vec3 x=s.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius));
            Vec3 y=s.scale(Math.cos(b)*radius).add(v.scale(Math.sin(b)*radius));
            beamFace(from.add(x),to.add(x),to.add(y),from.add(y),rgb,alpha);
            if(capped){
                beamFace(from,from.add(y),from.add(x),from.add(x),rgb,alpha);
                beamFace(to,to.add(x),to.add(y),to.add(y),rgb,alpha);
            }
        }
    }
    private void ribbon(Vec3 a,Vec3 b,Vec3 normal,double width,int color,double alpha){
        Vec3 dir=b.subtract(a);if(dir.lengthSqr()<1E-10)return;Vec3 s=dir.cross(normal);
        if(s.lengthSqr()<1E-10)s=side(dir.normalize());s=s.normalize().scale(width*.5);
        // STREAM's long tail -> tip runs from low U to high U, along the real path.
        face(a.add(s),b.add(s),b.subtract(s),a.subtract(s),color,alpha,YIKE_HD_STREAM);
    }
    /** Crossed textured discharge highlights; flying solids come from the actual BBModel cache. */
    private void photonCore(Vec3 tip,Vec3 f,double length,double width,double alpha){
        Vec3 s=side(f),v=s.cross(f).normalize(),center=tip.subtract(f.scale(length*.5));
        image(center,s,f,width,length*.5,YIKE_HD_SPARK,alpha);
        image(center,v,f,width,length*.5,YIKE_HD_SPARK,alpha*.7);
    }
    /** Open hard-edged electronic ring segments; no continuously filled disc. */
    private void electronicRing(Vec3 center,Vec3 axis,double radius,double start,double sweep,int color,double alpha,double width){
        Vec3 s=axis.cross(Z);if(s.lengthSqr()<1E-8)s=axis.cross(X);s=s.normalize();Vec3 v=axis.cross(s).normalize();
        int n=reduced?3:6;
        for(int i=0;i<n;i++){
            double a=start+sweep*i/n,b=start+sweep*(i+.72)/n;
            ribbon(center.add(s.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius)),
                    center.add(s.scale(Math.cos(b)*radius)).add(v.scale(Math.sin(b)*radius)),axis,width,color,alpha);
        }
    }
    /** Three right-angle targeting strokes, inset from the authoritative radius. */
    private void bracket(Vec3 center,Vec3 axis,double radius,double angle,double span,int color,double alpha){
        bracket(center,axis,radius,angle,span,color,alpha,.035);
    }
    private void bracket(Vec3 center,Vec3 axis,double radius,double angle,double span,int color,double alpha,double stroke){
        Vec3 basis=axis.cross(Z);if(basis.lengthSqr()<1E-8)basis=axis.cross(X);basis=basis.normalize();Vec3 vertical=axis.cross(basis).normalize();
        Vec3 radial=basis.scale(Math.cos(angle)).add(vertical.scale(Math.sin(angle)));
        double halfStroke=stroke*.5,outerTangent=span*.5+halfStroke;
        double insetRadius=Math.max(0,Math.sqrt(Math.max(0,radius*radius-outerTangent*outerTangent))-halfStroke);
        Vec3 tangent=axis.cross(radial).normalize(),base=center.add(radial.scale(insetRadius));
        Vec3 a=base.subtract(tangent.scale(span*.5)),b=base.add(tangent.scale(span*.5));
        ribbon(a,b,axis,stroke,color,alpha);
        ribbon(a,a.subtract(radial.scale(.10)),axis,stroke,color,alpha);
        ribbon(b,b.subtract(radial.scale(.10)),axis,stroke,color,alpha);
    }
    /** A clipped quadrilateral fin/impact panel rather than a curved magic petal. */
    private void panel(Vec3 root,Vec3 along,Vec3 out,double height,double reach,int color,double alpha){
        Vec3 normal=along.cross(out).normalize();
        Vec3 a=root,b=root.add(out.scale(reach*.78)).add(along.scale(height*.08));
        Vec3 c=root.add(out.scale(reach)).add(along.scale(height*.78)),d=root.add(out.scale(reach*.35)).add(along.scale(height));
        face(a,b,c,d,color,alpha,color==CORE?YIKE_HD_SHIELD:YIKE_HD_ARC);
        ribbon(a,b,normal,.018,PHOTON,alpha*.85);ribbon(c,d,normal,.018,CORE,alpha*.75);
    }
    /** Original event envelope: core flash, planar shock sectors, digital fragments, heavy afterglow. */
    private void impact(Vec3 at,double radius,int signal){
        boolean heavy=signal==HEAVY_IMPACT,denial=signal==DENIAL_BURST;
        double r=radius,panelT=Math.clamp((age-1)/2.6,0,1),energy=1-progress;
        if(age<1.8){double shrink=1-age/2;photonCore(at.add(0,.25*r,0),UP,Math.min(.9,r*.45)*shrink,Math.min(.28,r*.18)*shrink,.98);}
        if(age<3.5)image(at.add(0,.03,0),X,Z,r*.34,r*.34,YIKE_HD_IMPACT,(1-age/3.5)*.95);
        if(age>=1&&age<9){int n=reduced?3:6;double alpha=Math.min(1,(age-1)*1.5)*Math.min(1,(9-age)/4)*.74;
            for(int i=0;i<n;i++){double a=i*Math.PI*2/n+.17;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));
                panel(at.add(out.scale(r*.08)),UP,out,r*(denial?.29:.12+.17*(i%2))*panelT,r*(.2+.62*panelT),i%3==0?CORE:PHOTON,alpha);}
        }
        if(age>=3.6){int n=reduced?4:8;double t=Math.clamp((age-3.6)/(heavy?10.4:4.4),0,1);
            for(int i=0;i<n;i++){double a=i*2.399963;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),from=at.add(out.scale(r*(.45+.34*t))).add(0,r*(.09+(i%3)*.05)*(1-t),0);
                ribbon(from,from.add(out.scale(r*.12*(1-t))).add(0,.04*(1-t),0),UP,.026,i%4==0?ACCENT:PHOTON,energy*.72);}
            if(!reduced)image(at.add(0,r*.13,0),X,UP,r*.66,r*.36,YIKE_HD_MOTES,energy*.65);
        }
        if(age>=1.5&&age<8)image(at.add(0,r*.08,0),X,Z,r*.61,r*.61,denial?YIKE_HD_SHIELD:YIKE_HD_VEIL,.48*energy);
        if(heavy&&age>=1&&age<9)for(int i=0;i<(reduced?2:3);i++)electronicRing(at.add(0,.09+i*.17,0),UP,r*(.24+.62*panelT),i*2.1,.7,i==2?ACCENT:PHOTON,energy*.42,.045);
    }
    private void tails(Vec3 from,Vec3 to,double width,Vec3 f,double alpha){
        Vec3 s=side(f),v=s.cross(f).normalize();
        // Three continuous layers use the real swept segment. No extrapolated tail across a collision.
        // Fixed transverse axes are essential: a taper-derived line normal can protrude past an endpoint.
        tailLayer(from,to,s.scale(width*.90),s.scale(width*.70),PHOTON,.50*alpha);
        tailLayer(from.add(s.scale(width*.8)),to.add(s.scale(width*.12)),v.scale(width*.32),v.scale(width*.15),COBALT,.48*alpha);
        tailLayer(from.subtract(s.scale(width*.65)),to.subtract(s.scale(width*.10)),v.scale(width*.15),v.scale(width*.08),CORE,.62*alpha);
    }
    private void tailLayer(Vec3 from,Vec3 to,Vec3 widthFrom,Vec3 widthTo,int color,double alpha){
        face(from.add(widthFrom),to.add(widthTo),to.subtract(widthTo),from.subtract(widthFrom),color,alpha,YIKE_HD_STREAM);
    }
    private void draw(AbilityVfxPayload p){
        Vec3 at=p.from(),end=p.to(),delta=end.subtract(at),f=delta.lengthSqr()<1E-10?Z:delta.normalize(),s=side(f),v=s.cross(f).normalize();
        int signal=signal(p);double r=Math.max(.03,p.radius());
        if(p.kind()==AbilityVfxGeometry.TRAIL){
            double alpha=Math.pow(1-progress,1.5);
            if(signal==BASIC){
                Vec3 start=end.lerp(at,.22);tailLayer(start,end,s.scale(.014),s.scale(.006),PHOTON,.55*alpha);
                for(int i=0;i<2;i++){Vec3 center=start.lerp(end,.35+i*.32).add(v.scale(i==0?.025:-.025));double length=start.distanceTo(end)*.04;
                    face(center.add(f.scale(length)).add(s.scale(.007)),center.subtract(f.scale(length)).add(s.scale(.007)),center.subtract(f.scale(length)).subtract(s.scale(.007)),center.add(f.scale(length)).subtract(s.scale(.007)),ACCENT,.6*alpha);}
            }else tails(at,end,r,f,alpha);
            if(age>.35)for(int i=0;i<(reduced?1:3);i++){
                Vec3 point=at.lerp(end,.22+i*.27);image(point,s,v,r*.55,r*.55,YIKE_HD_MOTES,alpha*.68);
            }
            return;
        }
        if(p.kind()==AbilityVfxGeometry.FLIGHT){
            Vec3 head=at.lerp(end,Math.clamp(age,0,1));boolean basic=signal==BASIC,heavy=signal==HEAVY;
            double length=modelLength(signal);
            MODELS.append(mesh,modelKey(signal,reduced),head,f,length,YIKE_PROJECTILE_MODEL,1);
            Vec3 rear=head.subtract(f.scale(length*.95)),front=head.subtract(f.scale(length*.08));
            tailLayer(rear,front,s.scale(r*(basic?.75:1.05)),s.scale(r*.24),PHOTON,.50);
            tailLayer(rear,front,v.scale(r*.55),v.scale(r*.15),CORE,.42);
            image(head.subtract(f.scale(length*.28)),s,v,r*.75,r*.75,YIKE_HD_ARC,.55);
            if(age>.12)for(int i=0;i<(reduced?1:3);i++)image(head.subtract(f.scale(length*(.35+i*.18))),s,v,r*.9,r*.9,YIKE_HD_MOTES,.7);
            return;
        }
        switch(signal){
            case BASIC -> {photonCore(at.add(f.scale(.12)),f,.18,.045,.8);ribbon(at.subtract(f.scale(.14)),at,f,.025,PHOTON,.55);
                image(at,s,v,.10,.10,YIKE_HD_ARC,.52);if(age>.6)image(at.subtract(f.scale(.06)),s,v,.12,.12,YIKE_HD_MOTES,.65);}
            case BASIC_HIT -> {photonCore(at.add(0,.08,0),UP,.16*(1-progress),.075*(1-progress),.9);
                if(age>.4)image(at,X,UP,.14,.14,YIKE_HD_IMPACT,.6*(1-progress));
                if(!reduced)for(int i=0;i<2;i++){Vec3 d=new Vec3(i==0?1:-1,.5,0).scale(.11*progress);image(at.add(d),X,UP,.06,.06,YIKE_HD_MOTES,.8*(1-progress));}}
            case IMPACT,DENIAL_BURST,HEAVY_IMPACT,BEAM_HIT,MARK_BURST -> impact(at,r,signal);
            case DISSIPATE -> {for(int i=0;i<(reduced?3:5);i++){double a=i*2.4;Vec3 out=new Vec3(Math.cos(a),.25,Math.sin(a));ribbon(at.add(out.scale(r*.2)),at.add(out.scale(r*(.3+.5*progress))),UP,.025,i%4==0?ACCENT:PHOTON,.65*(1-progress));}
                image(at.add(0,r*.15,0),X,UP,r*.6,r*.4,YIKE_HD_MOTES,.8*(1-progress));}
            case DENIAL_WARNING,HEAVY_WARNING -> {
                int n=signal==HEAVY_WARNING?8:6;double contraction=signal==DENIAL_WARNING?progress:((time%20)/20);
                for(int i=0;i<n;i++){double a=i*Math.PI*2/n;bracket(at,UP,r,a,.28,PHOTON,.95,.085);
                    Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a));double inner=r*(.78-.42*contraction);
                    ribbon(at.add(out.scale(inner)),at.add(out.scale(inner+.12)),UP,.024,PHOTON,.66);}
                image(at.add(0,.012,0),X,Z,r*.62,r*.62,signal==HEAVY_WARNING?YIKE_HD_RING:YIKE_HD_SHIELD,.52);
                if(signal==HEAVY_WARNING||age>1)image(at.add(0,.018,0),X,Z,r*.41,r*.41,YIKE_HD_VEIL,.25);
                if(!reduced&&(signal==HEAVY_WARNING?time%8>2:age>3))image(at.add(0,.025,0),X,Z,r*.48,r*.48,YIKE_HD_MOTES,.45);
                if(signal==HEAVY_WARNING){ribbon(at.add(-.15,.015,0),at.add(.15,.015,0),UP,.019,CORE,.7);ribbon(at.add(0,.015,-.15),at.add(0,.015,.15),UP,.019,CORE,.7);}
            }
            case MARK1,MARK2 -> {
                int n=signal==MARK1?1:2;double shimmer=.75+.12*Math.sin(time*.25);
                for(int i=0;i<n;i++)bracket(at.add(s.scale(.45)),f,r*.48,-.8+i*2.1,.16,PHOTON,shimmer);
                image(at.add(s.scale(.45)),s,v,r*.28,r*.28,YIKE_HD_ARC,.55);
                image(at.add(s.scale(.45)),s,v,r*.13,r*.13,YIKE_HD_SPARK,.38+.3*Math.max(0,Math.sin(time*.55)));
            }
            case BARRIER -> {
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),base=at.add(out.scale(.5)),tangent=new Vec3(-Math.sin(a),0,Math.cos(a)).scale(.23);
                    Vec3 a0=base.add(tangent).add(0,-.4,0),b=base.subtract(tangent).add(0,-.4,0),c=base.subtract(tangent.scale(.75)).add(0,.4,0),d=base.add(tangent.scale(.75)).add(0,.4,0);
                    face(d,c,b,a0,COBALT,.62,YIKE_HD_SHIELD);ribbon(a0,d,out,.024,PHOTON,.82);ribbon(b,c,out,.024,PHOTON,.82);ribbon(c,d,out,.020,CORE,.8);
                    Vec3 center=base.add(0,.04,0);ribbon(center.subtract(tangent.scale(.2)),center.add(tangent.scale(.2)),out,.022,CORE,.8);
                    if(!reduced&&age>1)image(center,tangent,UP,.20,.32,YIKE_HD_MOTES,.55);
                }
            }
            case FIXED,MOBILE -> {
                boolean mobile=signal==MOBILE;double rr=mobile?.43:.65;int n=mobile?2:3;
                for(int i=0;i<n;i++){double a=i*Math.PI*2/n;Vec3 out=new Vec3(Math.cos(a),0,Math.sin(a)),base=at.add(out.scale(rr));
                    bracket(at,UP,rr,a,.20,PHOTON,.65);ribbon(base,base.add(0,mobile?.06:.16,0),out,.022,CORE,.55);
                    image(base.add(0,.012,0),out,new Vec3(-out.z,0,out.x),.11,.16,YIKE_HD_SHIELD,.65);
                    if(mobile)ribbon(base.subtract(f.scale(.13)),base.add(f.scale(.07)),UP,.025,PHOTON,.38);}
                if(!reduced&&age>1)image(at.add(0,.03,0),X,Z,rr*.46,rr*.46,YIKE_HD_MOTES,.6);
            }
            case DEPLOY -> {
                double t=preparation(p),unlock=Math.clamp(t/.16,0,1),open=Math.clamp((t-.15)/.57,0,1),ready=Math.clamp((t-.78)/.22,0,1);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a))),base=at.add(out.scale(.08+.22*open));
                    ribbon(base,base.add(f.scale(.20+.18*ready)),v,.018,PHOTON,(.3+.6*ready)*unlock);}
                image(at.add(f.scale(.15)),s,v,.12+.15*open,.12+.15*open,YIKE_HD_RING,.6*unlock);
                if(open>0)image(at.add(f.scale(.21)),s,v,.18,.18,YIKE_HD_VEIL,.4*open);
                if(ready>0)photonCore(at.add(f.scale(.35)),f,.2,.045,ready*.8);
            }
            case MUZZLE_CHARGE,BEAM_CHARGE -> {
                double t=preparation(p),rr=(signal==BEAM_CHARGE?.34:.2)*(1-t*.65);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    ribbon(at.add(out.scale(rr)).subtract(f.scale(.08)),at.add(out.scale(rr*.32)).add(f.scale(.2)),v,.018,PHOTON,.62);
                    bracket(at.add(f.scale(.16)),f,rr+.07,a,.10,PHOTON,.65);}
                image(at.add(f.scale(.16)),s,v,rr,rr,YIKE_HD_RING,.75);
                image(at.add(f.scale(.19)),s,v,rr*.85,rr*.85,YIKE_HD_VEIL,.36);
                if(t>.55)photonCore(at.add(f.scale(.24)),f,.11,.025,.5+t*.35);
            }
            case MUZZLE,LAST_MUZZLE -> {
                if(age<1.4)photonCore(at.add(f.scale(.35)),f,signal==LAST_MUZZLE?.4:.27,.045,.95);
                if(age<2)image(at.add(f.scale(.15)),s,v,.21,.21,YIKE_HD_IMPACT,.75*(1-age/2));
                double t=Math.clamp(age/5,0,1);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;Vec3 out=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    ribbon(at.add(out.scale(.08)).subtract(f.scale(.16+t*.3)),at.add(f.scale(.18)).add(out.scale(.06)),v,.025,signal==LAST_MUZZLE&&i==0?PULSE:PHOTON,(1-t)*.65);}
                if(age>.7)image(at.subtract(f.scale(.05+t*.12)),s,v,.23,.23,YIKE_HD_MOTES,.8*(1-t));
            }
            case BEAM,BEAM_END -> {
                double length=at.distanceTo(end),stop=signal==BEAM_END?Math.pow(1-progress,1.7):1;
                Vec3 tip=at.add(f.scale(length*stop));double power=signal==BEAM_END?1-progress:1;
                // Keep the solid start clear of the owner's near plane. End remains server-clipped.
                Vec3 coreStart=at.add(f.scale(Math.min(.5,length*stop*.25)));
                beamTube(coreStart,tip,f,.11*power,CORE,.96*power,true);
                beamTube(coreStart,tip,f,.21*power,PHOTON,.52*power,false);
                ribbon(at,tip,v,.13*power,CORE,.94*power);ribbon(at,tip,s,.10*power,CORE,.88*power);
                ribbon(at,tip,v,.45*power,PHOTON,.52*power);
                // The actual PNG contains substantial negative space. Let its colored
                // curtain occupy the supported width without painting an opaque slab.
                Vec3 veil=v.scale(.58*power);
                face(at.add(veil),at.subtract(veil),tip.subtract(veil),tip.add(veil),PHOTON,.70*power,YIKE_HD_VEIL);
                Vec3 veilSide=s.scale(.45*power);
                face(at.add(veilSide),at.subtract(veilSide),tip.subtract(veilSide),tip.add(veilSide),PHOTON,.48*power,YIKE_HD_VEIL);
                for(int sign:new int[]{-1,1})ribbon(at.add(s.scale(sign*.24*power)),tip.add(s.scale(sign*.24*power)),v,.023*power,PHOTON,.62*power);
                int n=reduced?3:7;
                for(int i=0;i<n;i++){double t=((time/8.0+i/(double)n)%1+1)%1,a=time*.32+i*Math.PI*.8;
                    Vec3 offset=s.scale(Math.cos(a)*.27*power).add(v.scale(Math.sin(a)*.27*power));
                    Vec3 a0=at.add(f.scale(length*stop*t)).add(offset),b0=at.add(f.scale(Math.min(length*stop,length*stop*t+.8))).add(offset);
                    ribbon(a0,b0,v,.09*power,i%4==0?ACCENT:PHOTON,.7*power);
                    if(!reduced&&i%2==0)image(a0,s,v,.13*power,.13*power,YIKE_HD_MOTES,.65*power);}
            }
            default -> {}
        }
    }
}
