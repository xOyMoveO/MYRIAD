package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Red/orange layered animated planes: no cube flame, invisible hitbox or third-person pose. */
public final class FlameVfxGeometry {
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private static final int WHITE=0xFFFFFF,RED=0xFF865E,ORANGE=0xFFD09B;
    private final List<Quad> out=new ArrayList<>();
    private final AbilityVfxPayload p;private final double age,progress,phase;private final boolean low;private final float fade;
    private FlameVfxGeometry(AbilityVfxPayload p,float age,boolean low){this.p=p;this.age=age;this.low=low;
        progress=Math.clamp(age/p.life(),0,1);phase=(Math.floorMod(p.started(),160)+age)*.10;
        fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.25));}
    public static AABB bounds(AbilityVfxPayload p){return new AABB(p.from(),p.to()).inflate(Math.max(4,p.radius()*(FlameCrossSlashVfx.owns(p)?3.7:2.7)+2));}
    public static float ownerOpacity(int kind){return kind==SHIELD?.26f:kind==CHARGE?.36f:1;}
    public static float firstPersonOpacity(AbilityVfxPayload p,float ordinaryNear){
        // This recipe now originates at the eye, but all its actual planes are safely ahead.
        return FlameCrossSlashVfx.owns(p)||FlameStepVfx.finish(p)?1:FlameStepVfx.sides(p)?.5f:ordinaryNear*ownerOpacity(p.kind());
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!p.valid()||age<0||age>=p.life())return List.of();var g=new FlameVfxGeometry(p,age,low);g.draw();return List.copyOf(g.out);}
    private static Vec3 direction(Vec3 a,Vec3 b){var f=b.subtract(a);return f.lengthSqr()<1E-9?Z:f.normalize();}
    private static Vec3 side(Vec3 f){var s=f.cross(UP);return s.lengthSqr()<1E-9?X:s.normalize();}
    private void plane(Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,int tint,double opacity,int offset,boolean once){
        if(out.size()+2>(low?80:192)||w<=0||h<=0)return;
        var cell=once?FlameFlipbook.once(progress,offset):FlameFlipbook.loop(p.started(),age,offset);
        var r=right.normalize().scale(w);var u=up.normalize().scale(h);
        var a=at.subtract(r).subtract(u);var b=at.add(r).subtract(u);var c=at.add(r).add(u);var d=at.subtract(r).add(u);
        float alpha=(float)Math.clamp(opacity*fade,0,1);if(alpha<=0)return;
        out.add(new Quad(a,b,c,d,tint,alpha,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,tint,alpha,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
    private Vec3 circle(Vec3 at,double radius,double a,double y){return at.add(Math.cos(a)*radius,y,Math.sin(a)*radius);}
    private void fire(Vec3 feet,double size,int offset){
        double turn=offset*2.399+phase*.14;
        var across=new Vec3(Math.cos(turn),0,Math.sin(turn));
        var lift=new Vec3(Math.sin(phase+offset)*.08,1,Math.cos(phase+offset)*.08).normalize();
        plane(feet.add(0,size*.64,0),across,lift,size*.80,size*1.1,FENJUE_PIXEL_FLAME,WHITE,.98,offset,false);
        if(!low)plane(feet.add(0,size*.59,.025),across.cross(UP),lift,size*.68,size,FENJUE_PIXEL_FLAME,RED,.76,offset+3,false);
    }
    private void ring(Vec3 at,double radius,boolean warning){
        // The padded source ring occupies about half the canvas; compensate visual radius.
        double turn=phase*.025;var right=new Vec3(Math.cos(turn),0,Math.sin(turn));
        plane(at,right,right.cross(UP),radius*2.02,radius*2.02,FENJUE_PIXEL_RING,WHITE,warning?.75:.96,0,false);
        if(!warning&&!low)plane(at.add(0,.018,0),Z,X,radius*1.53,radius*1.53,FENJUE_PIXEL_RING,RED,.48,3,false);
    }
    private void embers(Vec3 at,double size,int offset,boolean once){
        var s=new Vec3(Math.cos(offset+phase*.1),0,Math.sin(offset+phase*.1));
        plane(at.add(0,size*.45+progress*.45,0),s,UP,size*.9,size*.9,FENJUE_PIXEL_EMBERS,WHITE,.70,offset,once);
        if(!low)plane(at.add(0,size*.48+progress*.6,.025),s.cross(UP),UP,size*.8,size*.86,FENJUE_PIXEL_EMBERS,RED,.48,offset+2,once);
    }
    private void ribbon(Vec3 a,Vec3 b,double width,int tint,double opacity,int offset){
        if(a.distanceToSqr(b)<1E-9)return;var f=direction(a,b);var s=side(f);var v=s.cross(f).normalize();var center=a.lerp(b,.5);
        plane(center,f,s,a.distanceTo(b)*.77,width,FENJUE_PIXEL_STREAM,tint,opacity,offset,false);
        plane(center.add(0,.02,0),f,v,a.distanceTo(b)*.77,width*.8,FENJUE_PIXEL_STREAM,tint,opacity*.92,offset+2,false);
    }
    private void burst(Vec3 at,Vec3 f,double radius){
        var s=side(f);var v=s.cross(f).normalize();double spread=radius*(.35+.65*progress);
        plane(at.add(0,.25,0),s,v,radius*1.30,radius*1.30,FENJUE_PIXEL_BURST,WHITE,.98,0,true);
        if(!low)plane(at.add(0,.23,0),f,v,radius*1.13,radius*1.13,FENJUE_PIXEL_BURST,ORANGE,.76,1,true);
        ring(at.add(0,.035,0),Math.max(.18,spread),false);
        int count=low?4:7;
        for(int i=0;i<count;i++)fire(circle(at,spread*.78,i*Math.PI*2/count,.02),Math.min(1.7,.45+radius*.22),i);
        embers(at,Math.min(2.5,radius*.58),0,true);
    }
    private void draw(){
        if(FlameHitVfx.owns(p)){out.addAll(FlameHitVfx.build(p,(float)age,low));return;}
        if(FlameMajorVfx.owns(p)){out.addAll(FlameMajorVfx.build(p,(float)age,low));return;}
        if(FlameStepVfx.owns(p)){out.addAll(FlameStepVfx.build(p,(float)age,low));return;}
        if(FlameCrossSlashVfx.owns(p)){out.addAll(FlameCrossSlashVfx.build(p,(float)age,low));return;}
        Vec3 at=p.from(),end=p.to(),f=direction(at,end),s=side(f),v=s.cross(f).normalize();double r=Math.max(.08,p.radius());
        switch(p.kind()){
            case ARC -> {
                plane(at.add(f.scale(r*.4)),f,s,r*1.5,r*1.5,FENJUE_PIXEL_SLASH,WHITE,.98,0,true);
                plane(at.add(f.scale(r*.35)).add(0,.11,0),f,v,r*1.15,r*.80,FENJUE_PIXEL_SLASH,ORANGE,.86,1,true);
                int count=low?3:5;double yaw=Math.atan2(f.z,f.x),sweep=Math.toRadians(p.angle());
                for(int i=0;i<count;i++)fire(circle(at,r*.74,yaw-sweep*.5+sweep*i/(count-1),-.25),.70,i);
                if(!low)embers(at.add(f.scale(r*.5)),.65,2,true);
            }
            case SEAL -> {
                boolean warning=p.angle()==359;ring(at,r,warning);if(warning||p.personal())break;
                int count=low?5:9;
                for(int i=0;i<count;i++)fire(circle(at,r*.85,i*Math.PI*2/count,.02),p.variant()==4?1.2:1.6,i);
                if(p.life()>=20){
                    for(int i=0;i<(low?2:4);i++){double a=i*2.399+phase*.06;fire(circle(at,r*.42,a,.01),.85,i+1);}
                    if(!low)embers(at,Math.min(2,r*.65),2,false);
                }
            }
            case BURST -> {
                if(p.angle()==1){fire(at,.6,0);embers(at,.4,1,true);break;}
                if(p.variant()==0){fire(at,.75,0);embers(at,.6,1,true);break;}
                burst(at,f,r);
            }
            case SHIELD -> {
                ring(at.add(0,.025,0),r,false);
                if(p.variant()==1){fire(at.add(.42,.65,0),.6,0);break;}
                int count=low?4:7;
                for(int i=0;i<count;i++){
                    double a=i*Math.PI*2/count+phase*.16;
                    fire(circle(at,r,a,.12),p.variant()==7?1.22:.85,i);
                }
                if(!low)embers(at,.75,2,false);
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1E-9)return;
                ribbon(at,end,p.variant()==3?.60:.35,WHITE,.98,0);
                if(!low)ribbon(at.add(0,-.04,0),end.add(0,-.04,0),.72,RED,.62,3);
                int count=low?2:4;for(int i=0;i<count;i++)fire(at.lerp(end,(i+.5)/count).add(0,-.25,0),.7,i);
                if(!low)embers(at.lerp(end,.25),.6,1,true);
            }
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-9)return;
                ribbon(at,end,.48,WHITE,.98,0);
                if(age<1){var head=at.lerp(end,Math.clamp(age,0,1));fire(head.add(0,-.3,0),.7,1);}
            }
            case CHARGE -> {
                ring(at,r,true);double pull=.9-.45*progress;
                int count=low?3:5;
                for(int i=0;i<count;i++){
                    double a=i*Math.PI*2/count+phase*.10;
                    var outer=circle(at,pull*1.3,a,.08);var inner=circle(at,.35,a,.35);
                    ribbon(outer,inner,.32,ORANGE,.72,i);fire(inner,.55,i);
                }
                if(!low)embers(at,.55,0,false);
            }
            case PILLAR -> {fire(at,Math.min(4,at.distanceTo(end)),0);ring(at,.7,false);embers(at,1,2,true);}
            case WALL -> {int count=low?4:7;for(int i=0;i<count;i++)fire(at.add(s.scale(r*(i/(double)(count-1)*2-1))),1.3,i);}
        }
    }
}
