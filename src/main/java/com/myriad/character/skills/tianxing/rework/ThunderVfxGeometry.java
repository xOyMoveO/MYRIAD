package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** White lightning cores, cyan forks, blue afterimages: authored layered 2D art, no cube beams. */
public final class ThunderVfxGeometry {
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private static final int WHITE=0xFFFFFF,CYAN=0xB8FDFF,BLUE=0x8FC8FF;
    private final List<Quad> out=new ArrayList<>();
    private final AbilityVfxPayload p;
    private final double age,progress,phase;
    private final float fade;
    private final boolean low;
    private ThunderVfxGeometry(AbilityVfxPayload p,float age,boolean low){
        this.p=p;this.age=age;this.low=low;progress=Math.clamp(age/p.life(),0,1);
        phase=(Math.floorMod(p.started(),160)+age)*.075;
        fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));
    }
    public static AABB bounds(AbilityVfxPayload p){return new AABB(p.from(),p.to()).inflate(Math.max(2.5,p.radius()*1.6+1));}
    public static float ownerOpacity(int kind){return kind==SHIELD?.24f:kind==CHARGE?.32f:1;}
    public static boolean usesProjectileModel(AbilityVfxPayload p){return p.theme()==1&&p.kind()==FLIGHT&&(p.variant()==3||p.variant()==5);}
    private static final int WAKE_MARKER=358;
    /** Local decoration of a received authoritative segment; never extrapolate past its clipped end. */
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload flight){
        if(!flight.valid()||!usesProjectileModel(flight)||flight.from().distanceToSqr(flight.to())<1E-8)return null;
        return new AbilityVfxPayload(flight.owner(),-1,1,flight.variant(),TRAIL,flight.from(),flight.to(),
                flight.radius(),WAKE_MARKER,flight.variant()==5?7:6,flight.started(),flight.personal(),0);
    }
    public static boolean isProjectileWake(AbilityVfxPayload p){
        return p.theme()==1&&p.kind()==TRAIL&&(p.variant()==3||p.variant()==5)&&p.angle()==WAKE_MARKER;
    }
    public static int budget(AbilityVfxPayload p,boolean low){return usesProjectileModel(p)?(low?320:1200):(low?80:192);}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||age<0||age>=p.life())return List.of();
        var g=new ThunderVfxGeometry(p,age,reduced);g.draw();return List.copyOf(g.out);
    }
    private static Vec3 direction(Vec3 from,Vec3 to){var d=to.subtract(from);return d.lengthSqr()<1E-9?Z:d.normalize();}
    private static Vec3 side(Vec3 f){var s=f.cross(UP);return s.lengthSqr()<1E-9?X:s.normalize();}
    private void plane(Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,int tint,double opacity,int offset,boolean once){
        if(out.size()+2>budget(p,low)||w<=0||h<=0||right.lengthSqr()<1E-9||up.lengthSqr()<1E-9)return;
        var cell=once?ThunderFlipbook.once(progress,offset):ThunderFlipbook.loop(p.started(),age,offset,material==THUNDER_PIXEL_SEAL||material==THUNDER_PIXEL_CORONA);
        var r=right.normalize().scale(w);var u=up.normalize().scale(h);
        var a=at.subtract(r).subtract(u);var b=at.add(r).subtract(u);var c=at.add(r).add(u);var d=at.subtract(r).add(u);
        float alpha=(float)Math.clamp(opacity*fade,0,1);if(alpha<=0)return;
        out.add(new Quad(a,b,c,d,tint,alpha,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,tint,alpha,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
    private void seal(Vec3 at,double r,boolean warning){
        var right=new Vec3(Math.cos(phase*.17),0,Math.sin(phase*.17));var up=right.cross(UP);
        plane(at,right,up,r,r,THUNDER_PIXEL_SEAL,WHITE,warning?.9:.98,0,false);
        if(!low)plane(at.add(0,.028,0),up,right.scale(-1),r*.88,r*.88,THUNDER_PIXEL_CORONA,CYAN,warning?.36:.66,3,false);
        if(!warning&&!p.personal()){
            int count=low?2:4;
            for(int i=0;i<count;i++){
                double a=i*Math.PI*2/count+phase*.2;
                Vec3 s=new Vec3(Math.cos(a),0,Math.sin(a));
                plane(at.add(s.scale(r*.78)).add(0,.20,0),s,UP,.12,.22,THUNDER_PIXEL_BOLT,CYAN,.8,i,false);
            }
        }
    }
    private void impact(Vec3 at,Vec3 f,double r){
        var s=side(f);var v=s.cross(f).normalize();
        plane(at,s,v,r,r,THUNDER_PIXEL_IMPACT,WHITE,1,0,true);
        plane(at.add(f.scale(.025)),f,v,r*.87,r*.87,THUNDER_PIXEL_IMPACT,CYAN,.82,1,true);
        if(!low)plane(at.add(0,.018,0),X,Z,r*1.08,r*1.08,THUNDER_PIXEL_CORONA,BLUE,.65,2,false);
    }
    private void ribbon(Vec3 a,Vec3 b,double w,int tint,double alpha,int offset){
        if(a.distanceToSqr(b)<1E-9)return;
        var f=direction(a,b);var s=side(f);var v=s.cross(f).normalize();var center=a.lerp(b,.5);
        double length=a.distanceTo(b)*.56;
        plane(center,f,s,length,w,THUNDER_PIXEL_BOLT,tint,alpha,offset,false);
        plane(center.add(s.scale(.012)),f,v,length,w*.85,THUNDER_PIXEL_BOLT,tint,alpha*.86,offset+2,false);
    }
    private void bolt(Vec3 from,Vec3 to,double w,boolean branches){
        if(from.distanceToSqr(to)<1E-9)return;
        Vec3 f=direction(from,to),s=side(f),v=s.cross(f).normalize(),last=from;
        int count=low?3:5;
        for(int i=1;i<=count;i++){
            Vec3 next=from.lerp(to,i/(double)count);
            if(i<count)next=next.add(s.scale(Math.sin(i*7.3+Math.floor(phase))*.11)).add(v.scale(Math.cos(i*4.7)*.07));
            ribbon(last,next,w,WHITE,1,i);
            if(!low)ribbon(last.add(s.scale(.022)),next.add(s.scale(.022)),w*1.65,BLUE,.22,i+3);
            if(branches&&i<count&&(low?i==1:i%2==0))
                ribbon(next,next.add(s.scale((i%2==0?1:-1)*.42)).add(v.scale(.20)),w*.55,CYAN,.82,i+1);
            last=next;
        }
    }
    /** Convex crescent edge is +U; both crossed planes map +U to real travel. */
    private void slash(Vec3 at,Vec3 f,double r,double roll,boolean flight){
        var s=side(f);var v=s.cross(f).normalize();
        var u=v.scale(Math.cos(roll)).add(s.scale(Math.sin(roll)));
        var cross=v.scale(-Math.sin(roll)).add(s.scale(Math.cos(roll)));
        plane(at,f,u,r,r*.72,THUNDER_PIXEL_SLASH,WHITE,1,0,!flight);
        plane(at.subtract(f.scale(.04)),f,cross,r*.92,r*.65,THUNDER_PIXEL_SLASH,CYAN,.88,2,!flight);
        if(!low)plane(at.subtract(f.scale(.12)),f,u,r*1.13,r*.81,THUNDER_PIXEL_SLASH,BLUE,.48,4,false);
    }
    private void pillar(Vec3 from,Vec3 to,double width,int offset){
        if(from.distanceToSqr(to)<1E-9)return;
        var axis=direction(from,to);var s=side(axis);var v=s.cross(axis).normalize();
        var center=from.lerp(to,.5);double height=from.distanceTo(to)*.54;
        plane(center,s,axis,width,height,THUNDER_PIXEL_PILLAR,WHITE,1,offset,true);
        plane(center.add(s.scale(.025)),v,axis,width*.9,height,THUNDER_PIXEL_PILLAR,CYAN,.86,offset+1,true);
        if(!low)plane(center.add(v.scale(.04)),s,axis,width*1.45,height,THUNDER_PIXEL_PILLAR,BLUE,.24,offset+2,true);
    }
    private Vec3 fanDirection(Vec3 forward,double angle){
        return forward.scale(Math.cos(angle)).add(side(forward).scale(Math.sin(angle)));
    }
    /** The warning and release share the actual 120-degree sector, never a full circle. */
    private void fan(Vec3 at,Vec3 forward,double radius,boolean warning){
        Vec3 flat=new Vec3(forward.x,0,forward.z);flat=flat.lengthSqr()<1E-9?Z:flat.normalize();
        double half=Math.toRadians(Math.clamp(p.angle(),1,180)*.5);
        int arcs=low?8:16;
        if(warning){
            for(double a:new double[]{-half,half})groundLine(at,at.add(fanDirection(flat,a).scale(radius)),.035,.62,0);
            for(int i=0;i<arcs;i++)groundLine(at.add(fanDirection(flat,-half+2*half*i/arcs).scale(radius)),
                    at.add(fanDirection(flat,-half+2*half*(i+1)/arcs).scale(radius)),.035,.52,i);
            return;
        }
        int rays=low?3:7;
        for(int i=0;i<rays;i++){
            var dir=fanDirection(flat,-half+2*half*i/(rays-1));
            Vec3 previous=at.add(dir.scale(.4));
            for(int j=1;j<=3;j++){
                Vec3 next=at.add(dir.scale(.4+(radius-.4)*j/3));
                if(j<3)next=next.add(side(dir).scale(Math.sin(i*3.1+j*6.7+Math.floor(phase))*.16)).add(0,.18+j*.12,0);
                ribbon(previous,next,.27,WHITE,1,i+j);previous=next;
            }
        }
        // A rising curved shock front makes the fan readable in perspective, with gaps between forks.
        double reach=radius*(.62+.38*progress);
        for(int i=0;i<arcs;i++){
            Vec3 a=at.add(fanDirection(flat,-half+2*half*i/arcs).scale(reach)),
                    b=at.add(fanDirection(flat,-half+2*half*(i+1)/arcs).scale(reach));
            ribbon(a,b,.20,CYAN,.88,i);
        }
        impact(at.add(flat.scale(.8)),flat,.58);
        if(!low)slash(at.add(flat.scale(radius*.45)).add(0,.5,0),flat,1.6,.5,false);
        for(int i=0;i<(low?1:3);i++){
            var hit=at.add(fanDirection(flat,(i-(low?0:1))*half*.65).scale(radius*.76));
            pillar(hit,hit.add(0,1.4,0),.20,i);
        }
    }
    private void groundLine(Vec3 a,Vec3 b,double width,double alpha,int offset){
        var dir=direction(a,b);
        plane(a.lerp(b,.5),dir,side(dir),a.distanceTo(b)*.5,width,THUNDER_PIXEL_BOLT,CYAN,alpha,offset,false);
    }
    private void draw(){
        Vec3 at=p.from(),end=p.to(),f=direction(at,end),s=side(f),v=s.cross(f).normalize();
        double r=Math.max(.08,p.radius());
        if(isProjectileWake(p)){flightWake(at,end,f,s,v,p.variant()==5);return;}
        switch(p.kind()){
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-9)return;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                boolean sword=p.variant()==5;
                ribbon(at.subtract(f.scale(.8)),head,sword?.35:.23,WHITE,1,0);
                if(!low)ribbon(at.subtract(f.scale(1.1)).add(v.scale(.035)),head,.34,BLUE,.55,3);
                // Only one tick owns the moving crescent. Remaining lifetime contains a decaying wake, never a parked body.
                if(age<1){
                    if(usesProjectileModel(p)){
                        ThunderProjectileModels.append(out,head,f,sword,low,fade);
                        impact(head.subtract(f.scale(sword?.25:.4)),f,sword?.20:.14);
                        // Separate animated forks/tails decorate the solid core instead of replacing it.
                        for(int i=0;i<(low?1:2);i++){
                            double sign=i==0?1:-1;
                            var branch=head.add(v.scale(sign*(sword?.72:.28))).subtract(f.scale(sword?.45:.4));
                            ribbon(branch.subtract(f.scale(sword?1.1:.75)).add(v.scale(sign*.15)),branch,.10,WHITE,.85,i+2);
                        }
                        flightLayers(head,f,s,v,sword);
                    }else {slash(head,f,sword?.95:.53,sword?.6:phase*.13,true);impact(head,f,sword?.24:.16);}
                }
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1E-9)return;
                double width=p.variant()==7?Math.max(.30,r*.32):p.variant()==5?.28:.11;
                bolt(at,end,width,true);
                if(p.variant()==7){
                    plane(at.lerp(end,.5),f,v,at.distanceTo(end)*.53,Math.max(.65,r*.72),THUNDER_PIXEL_SLASH,WHITE,.96,0,true);
                    slash(end,f,1.05,-.55,false);impact(end,f,.75);
                    if(!low)ribbon(at,end,Math.max(.35,r*.58),BLUE,.25,4);
                }else if(p.variant()==5){
                    impact(end,f,.48);
                    int count=low?1:3;
                    for(int i=0;i<count;i++){
                        var center=at.lerp(end,Math.clamp(.18+progress*.60+i*.11,0,1));
                        plane(center,s,v,.42,.42,THUNDER_PIXEL_CORONA,WHITE,.88,i,false);
                        if(!low)plane(center.add(f.scale(.025)),v,s,.29,.29,THUNDER_PIXEL_SEAL,CYAN,.7,i+3,false);
                    }
                    if(!low)ribbon(at,end,.40,CYAN,.60,4);
                }
                else if(p.variant()==2){seal(at,.6,false);impact(end,f,.65);}
            }
            case PILLAR -> {
                pillar(at,end,p.variant()==6?.82:.42,0);
                if(p.variant()==6){
                    int count=low?2:5;double spread=Math.min(4,r*.73);
                    for(int i=0;i<count;i++){
                        double a=i*Math.PI*2/count+phase*.13;
                        var delta=new Vec3(Math.cos(a)*spread,0,Math.sin(a)*spread);
                        pillar(at.add(delta),end.add(delta).add(0,-.8-i*.14,0),.44,i%2);
                    }
                }
                seal(at,Math.min(1.3,Math.max(.65,r*.5)),false);impact(at.add(0,.25,0),Z,Math.min(1,r));
            }
            case ARC -> {if(p.variant()==7)fan(at,f,r,false);else {slash(at.add(f.scale(r*.35)),f,r*.70,.65,false);impact(at.add(f.scale(r*.5)),f,r*.32);}}
            case BURST -> {
                if(p.angle()==1){impact(at,f,.26);break;}
                impact(at.add(0,.18,0),f,Math.min(1.5,r*.72));
                seal(at.add(0,.01,0),r*(.5+.45*progress),false);
                if(p.variant()==2||p.variant()==6)
                    pillar(at,at.add(0,p.variant()==6?1.6:1,0),Math.min(.42,r*.3),2);
            }
            case SEAL -> {seal(at,r,p.angle()==359);}
            case SHIELD -> {
                if(p.variant()==1){
                    plane(at.add(s.scale(.48)).add(0,1.28,0),s,UP,.25,.25,THUNDER_PIXEL_SEAL,WHITE,.92,0,false);
                    plane(at.add(s.scale(.48)).add(0,1.28,.018),s,UP,.34,.34,THUNDER_PIXEL_CORONA,CYAN,.62,3,false);break;
                }
                seal(at.add(0,.035,0),r,false);
                int count=low?3:5;
                for(int i=0;i<count;i++){
                    double a=i*Math.PI*2/count+phase*.45;
                    var radial=new Vec3(Math.cos(a),0,Math.sin(a));var tangent=radial.cross(UP);
                    var center=at.add(radial.scale(r*.93)).add(0,.87+Math.sin(phase+i)*.18,0);
                    plane(center,tangent,UP,.32,.64,THUNDER_PIXEL_CORONA,WHITE,.92,i,false);
                    if(!low)plane(center.add(radial.scale(.025)),tangent,UP,.20,.51,THUNDER_PIXEL_PILLAR,CYAN,.68,i,false);
                }
            }
            case CHARGE -> {
                if(p.variant()==7){fan(at,f,r,true);break;}
                seal(at.add(0,.035,0),.68,false);
                var center=at.add(f.scale(.65)).add(0,.85,0);
                plane(center,s,UP,.38,.38,THUNDER_PIXEL_CORONA,WHITE,.97,0,false);
                plane(center.add(f.scale(.025)),v,s,.27,.27,THUNDER_PIXEL_SEAL,CYAN,.83,3,false);
                // Telegraph is deliberately thin/faint, not an already damaging full thunder beam.
                ribbon(at.add(0,.015,0),end.add(0,.015,0),Math.clamp(r,.06,.20),CYAN,.30,4);
            }
            case WALL -> {bolt(at,end,.09,false);impact(at,f,.45);}
        }
    }
    /** Segmented electric ribbons persist briefly after the body has passed, then narrow and fade.
     * No solid body, detached crescent or particles beyond the actual swept path. */
    private void flightWake(Vec3 from,Vec3 to,Vec3 f,Vec3 s,Vec3 v,boolean sword){
        double energy=Math.pow(1-progress,1.15),shrink=.55+.45*energy;
        int segments=low?2:4;
        for(int i=0;i<segments;i++){
            double t0=i/(double)segments,t1=(i+1)/(double)segments;
            Vec3 a=from.lerp(to,t0),b=from.lerp(to,t1);
            double taper=.45+.55*t1;
            ribbon(a,b,(sword?.18:.16)*taper*shrink,WHITE,.98*energy,i);
            if(!low)ribbon(a.add(s.scale(.018)),b.add(s.scale(.018)),(sword?.50:.40)*taper*shrink,BLUE,.46*energy,i+3);
            int wings=low?1:2;
            for(int wing=0;wing<wings;wing++){
                double sign=wing==0?1:-1;
                double spread=sword?.52:.27;
                // A split, wavy sheath. Both ends remain behind or at this segment's tip.
                Vec3 left=a.add(v.scale(sign*spread*(.55+.45*t0)*shrink)).add(s.scale(Math.sin(t0*5+phase)*.06));
                Vec3 right=b.add(v.scale(sign*spread*(.55+.45*t1)*shrink)).add(s.scale(Math.sin(t1*5+phase)*.06));
                ribbon(left,right,(sword?.16:.12)*taper*shrink,CYAN,.86*energy,i+wing+2);
            }
        }
        // The general ribbon deliberately overlaps neighbours. A contact wake must instead
        // stop at the authoritative swept segment's end plane, including tilted wide wings.
        double length=from.distanceTo(to);
        out.replaceAll(q->new Quad(wakePoint(q.a(),from,f,length),wakePoint(q.b(),from,f,length),
                wakePoint(q.c(),from,f,length),wakePoint(q.d(),from,f,length),q.color(),q.alpha(),q.material(),q.reverseV(),
                q.startU(),q.endU(),q.startV(),q.endV()));
        out.removeIf(q->q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()<=1E-12);
    }
    private static Vec3 wakePoint(Vec3 point,Vec3 from,Vec3 forward,double length){
        double along=point.subtract(from).dot(forward);
        return point.add(forward.scale(Math.clamp(along,0,length)-along));
    }
    /** Native 256px animated planes: thin curved filaments for Slash, crossed sheath for Arc. */
    private void flightLayers(Vec3 head,Vec3 f,Vec3 s,Vec3 v,boolean sword){
        if(sword){
            for(int sign:new int[]{-1,1}){
                var tip=head.add(v.scale(sign*.98)).subtract(f.scale(.66));
                var bend=head.add(v.scale(sign*.52)).subtract(f.scale(.20));
                var middle=head.subtract(f.scale(.10));
                if(low)ribbon(tip,middle,.045,CYAN,.90,sign+3);
                else {ribbon(tip,bend,.055,WHITE,.90,sign+3);ribbon(bend,middle,.045,CYAN,.82,sign+5);}
            }
            plane(head.subtract(f.scale(.14)),s,v,.16,.26,THUNDER_PIXEL_CORONA,WHITE,.58,4,false);
        }else{
            var center=head.subtract(f.scale(.85));
            plane(center,f,v,.85,.13,THUNDER_PIXEL_BOLT,WHITE,.86,0,false);
            plane(center.add(s.scale(.035)),f,s,.85,.11,THUNDER_PIXEL_BOLT,CYAN,.75,3,false);
            var ring=head.subtract(f.scale(.65));
            plane(ring,s,v,.31,.31,THUNDER_PIXEL_CORONA,CYAN,.84,1,false);
            if(!low)plane(ring.subtract(f.scale(.04)),v,s,.23,.23,THUNDER_PIXEL_SEAL,WHITE,.62,4,false);
        }
    }
}
