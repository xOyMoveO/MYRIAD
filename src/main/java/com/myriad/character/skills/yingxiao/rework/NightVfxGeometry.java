package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Authored ash-gray sprite layers with real depth separation, never a generic voxel projectile. */
public final class NightVfxGeometry {
    public static net.minecraft.world.phys.AABB bounds(AbilityVfxPayload p) {
        return new net.minecraft.world.phys.AABB(p.from(),p.to()).inflate(Math.max(2,p.radius()*1.7+.4));
    }
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private static final int WHITE=0xFFFFFF,ASH=0xB8B8B8,SILVER=0xD8D8D8;
    private final List<Quad> out=new ArrayList<>();
    private final AbilityVfxPayload p;
    private final double age,progress,phase;
    private final float fade;
    private final boolean low;
    private NightVfxGeometry(AbilityVfxPayload p,float age,boolean low) {
        this.p=p;this.age=age;this.low=low;progress=Math.clamp(age/p.life(),0,1);
        phase=(Math.floorMod(p.started(),120)+age)*.07;
        fade=(float)Math.min(1,(p.life()-age)/Math.max(1,p.life()*.3));
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low) {
        if(!p.valid()||age<0||age>=p.life())return List.of();
        var g=new NightVfxGeometry(p,age,low);g.draw();return List.copyOf(g.out);
    }
    private static Vec3 side(Vec3 d) {var s=d.cross(UP);return s.lengthSqr()<1E-8?X:s.normalize();}
    private void plane(Vec3 at,Vec3 right,Vec3 up,double width,double height,int material,int tint,double alpha,int offset,boolean once) {
        if(out.size()+2>(low?80:192)||alpha<=0||width<=0||height<=0)return;
        var cell=once?NightFlipbook.once(progress):NightFlipbook.loop(material,p.started(),age,offset);
        var r=right.normalize().scale(width);var u=up.normalize().scale(height);
        var a=at.subtract(r).subtract(u);var b=at.add(r).subtract(u);var c=at.add(r).add(u);var d=at.subtract(r).add(u);
        float opacity=(float)Math.clamp(alpha*fade,0,1);if(opacity<=0)return;
        out.add(new Quad(a,b,c,d,tint,opacity,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,tint,opacity,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
    }
    private void smoke(Vec3 at,double size,double angle,double alpha,int offset) {
        var r=new Vec3(Math.cos(angle),0,Math.sin(angle));
        plane(at,r,UP,size,size*.8,NIGHT_GRAY_SMOKE,WHITE,alpha,offset,false);
        if(!low)plane(at.add(0,.035,0),r.cross(UP),UP,size*.78,size*.66,NIGHT_GRAY_SMOKE,ASH,alpha*.58,offset+3,false);
    }
    /** The sprite's convex leading side is +U: map +U to travel, not to the plane normal. */
    private void slash(Vec3 at,Vec3 forward,double size,double roll,double alpha) {
        var s=side(forward);var v=s.cross(forward).normalize();
        var across=v.scale(Math.cos(roll)).add(s.scale(Math.sin(roll)));
        plane(at,forward,across,size,size*.80,NIGHT_GRAY_SLASH,WHITE,alpha,0,true);
        var crossed=v.scale(-Math.sin(roll)).add(s.scale(Math.cos(roll)));
        plane(at.add(forward.scale(-.025)),forward,crossed,size*.88,size*.70,NIGHT_GRAY_SLASH,SILVER,alpha*.74,0,true);
        if(!low)plane(at.add(s.scale(.045)),forward,across,size*1.1,size*.84,NIGHT_GRAY_SLASH,ASH,alpha*.47,3,false);
    }
    private void wake(Vec3 from,Vec3 to,double width,double alpha) {
        var delta=to.subtract(from);if(delta.lengthSqr()<1E-8)return;
        var f=delta.normalize();var s=side(f);var u=s.cross(f).normalize();var at=from.lerp(to,.5);
        double length=delta.length()*.56;
        plane(at,f,s,length,width,NIGHT_GRAY_WAKE,WHITE,alpha,0,false);
        plane(at.add(u.scale(.035)),f,u,length,width*.85,NIGHT_GRAY_WAKE,ASH,alpha*.68,3,false);
        if(!low)plane(at.add(s.scale(width*.22)),f,s.add(u).normalize(),length*.86,width*.52,
                NIGHT_GRAY_WAKE,SILVER,alpha*.45,5,false);
    }
    private void blade(Vec3 tip,Vec3 forward,double roll,double size,double alpha) {
        var s=side(forward);var v=s.cross(forward).normalize();
        var across=s.scale(Math.cos(roll)).add(v.scale(Math.sin(roll)));
        var perpendicular=across.cross(forward).normalize();
        var center=tip.subtract(forward.scale(.61*size));
        plane(center,forward,across,.76*size,.37*size,NIGHT_GRAY_BLADE,WHITE,alpha,0,false);
        plane(center.add(across.scale(.015)),forward,perpendicular,.76*size,.25*size,NIGHT_GRAY_BLADE,ASH,alpha*.8,0,false);
    }
    private void seal(Vec3 at,double size,double alpha,boolean warning) {
        var r=new Vec3(Math.cos(phase*.2),0,Math.sin(phase*.2));var u=r.cross(UP);
        plane(at,r,u,size,size,NIGHT_GRAY_SEAL,WHITE,alpha,0,false);
        if(!low&&!warning)plane(at.add(0,.075,0),u,r.scale(-1),size*.83,size*.83,NIGHT_GRAY_SEAL,ASH,alpha*.52,4,false);
    }
    private void impact(Vec3 at,Vec3 forward,double size,double alpha) {
        var s=side(forward);var v=s.cross(forward).normalize();
        plane(at,s,v,size,size,NIGHT_GRAY_IMPACT,WHITE,alpha,0,true);
        plane(at.add(0,.02,0),forward,v,size*.8,size*.8,NIGHT_GRAY_IMPACT,SILVER,alpha*.72,0,true);
        if(!low)plane(at.add(forward.scale(.04)),s.add(v),v.subtract(s),size*.78,size*.78,
                NIGHT_GRAY_IMPACT,ASH,alpha*.58,0,true);
        smoke(at.add(0,-.08,0),size*.56,phase,alpha*.48,3);
    }
    private void fragments(Vec3 at,Vec3 f,double size,int count) {
        var s=side(f);var v=s.cross(f).normalize();
        for(int i=0;i<(low?Math.min(count,2):count);i++) {
            double angle=i*2.399963+phase*.2,spread=size*(.25+.75*progress);
            var point=at.add(s.scale(Math.cos(angle)*spread)).add(v.scale(Math.sin(angle)*spread));
            plane(point,f,s,.12,.09,NIGHT_GRAY_SLASH,WHITE,.55*(1-progress),i,false);
        }
    }
    private void draw() {
        var at=p.from();var end=p.to();var delta=end.subtract(at);
        var f=delta.lengthSqr()<1E-8?Z:delta.normalize();var s=side(f);
        double r=Math.max(.08,p.radius());
        if(p.kind()==ARC&&p.angle()>=NightBasicVisuals.STAB&&p.angle()<=NightBasicVisuals.CUT){
            basicSwing(at,f);return;
        }
        if(p.kind()==BURST&&p.angle()==NightBasicVisuals.HIT){
            impact(at,f,.38*(1-progress*.25),.95);
            fragments(at,f,.38,3);return;
        }
        switch(p.kind()) {
            case FLIGHT -> {
                if(delta.lengthSqr()<1E-8)return;
                var tip=at.lerp(end,Math.clamp(age,0,1));
                wake(at.subtract(f.scale(.85)),tip,.50,.96);
                // One packet is one tick of travel. Old frames fade as smoke, never park a knife.
                if(age<1){
                    if(p.variant()==2)NightNeedleModel.append(out,tip,f,low,fade);
                    else blade(tip,f,.25+phase*.15,1,.98);
                }
                if(!low)for(int i=0;i<3;i++)smoke(tip.subtract(f.scale(.65+i*.32)),.23+i*.04,phase+i,.36-i*.06,i*2);
            }
            case TRAIL,BEAM -> {
                if(delta.lengthSqr()<1E-8)return;
                wake(at,end,p.variant()==6?.56:.46,.94);
                smoke(at,.36,phase,.56,0);
                if(!low)smoke(at.lerp(end,.52),.29,phase+1,.44,3);
                if(p.kind()==TRAIL) {
                    // Broken silhouettes stay below head height, leaving the caster's view open.
                    smoke(at.add(0,.12,0),.34,phase,.6,2);
                    if(!low)smoke(at.add(0,.55,0),.2,phase+1,.42,5);
                    slash(end,f,p.variant()==6?.55:.34,.7,.72);
                }
            }
            case ARC -> {
                var centre=at.add(f.scale(r*.28));
                slash(centre,f,r*.95,(p.variant()%2==0?.6:-.6)-progress*.28,.98);
                if(!low){smoke(centre.subtract(f.scale(r*.2)),r*.35,phase,.48,3);fragments(centre,f,r*.65,3);}
            }
            case BURST -> {
                if(p.angle()==1) {smoke(at,.28+progress*.23,phase,.67,3);return;}
                if(p.variant()==3) {
                    double spread=.24+progress*.58;
                    for(int i=0;i<(low?3:6);i++) {
                        double a=i*2.399963;
                        smoke(at.add(Math.cos(a)*spread,.15+(i%3)*.24,Math.sin(a)*spread),
                                .44*(1-progress*.2),a,.62,i);
                    }
                    seal(at.add(0,-.2,0),.7,.72,false);
                } else {
                    var centre=at.add(0,.22,0);
                    impact(centre,f,.48+r*.28,.98);
                    slash(centre.add(f.scale(.035)),f,.4+r*.25,-.65,.76);
                    fragments(centre,f,.65+r*.25,5);
                }
            }
            case SEAL -> {
                boolean warning=p.personal()||p.angle()==359;
                seal(at,r,warning?.95:.9,warning);
                if(warning)return;
                if(p.variant()==5) {
                    smoke(at.add(0,.32,0),.35,phase,.52,2);
                    plane(at.add(0,.65,0),s,UP,.24,.36,NIGHT_GRAY_SEAL,WHITE,.66,2,false);
                    if(!low)smoke(at.add(0,.88,0),.18,phase+1,.4,5);
                } else if(p.variant()==6) {
                    slash(at.add(0,.55,0),f,.52,.65,.86);
                    if(!low)slash(at.add(0,.58,0),f,.45,-.65,.7);
                } else if(!low)smoke(at.add(0,.1,0),Math.min(.4,r*.4),phase,.32,4);
            }
            case CHARGE -> {
                var c=at.add(0,.72,0);double pull=1-progress;
                seal(at,.58+pull*.25,.8,false);
                for(int i=0;i<(low?2:4);i++) {
                    double a=i*Math.PI*.5+phase;
                    var start=c.add(Math.cos(a)*(.28+pull*.55),Math.sin(a)*.12,Math.sin(a)*(.28+pull*.55));
                    wake(start,c,.11,.74);
                    if(!low)smoke(start,.23,a,.4,i*2);
                }
                blade(c.add(f.scale(.6)),f,.35,.62+progress*.18,.88);
            }
            case SHIELD -> {
                int count=low?3:5;
                for(int i=0;i<count;i++) {
                    double a=i*Math.PI*2/count+phase*.5;
                    var point=at.add(Math.cos(a)*r,.52+Math.sin(a*2)*.13,Math.sin(a)*r);
                    var tangent=new Vec3(-Math.sin(a),.14,Math.cos(a)).normalize();
                    blade(point,tangent,a,.4,.92);
                    if(!low)wake(point.subtract(tangent.scale(.48)),point,.13,.56);
                }
                seal(at.add(0,.055,0),r,.7,false);
            }
            case PILLAR,WALL -> {wake(at,end,.35,.9);slash(at.add(0,.5,0),f,r,.6,.95);}
        }
    }
    private void basicSwing(Vec3 at,Vec3 f){
        var s=side(f);var u=s.cross(f).normalize();
        double t=Math.min(1,progress*1.25);
        if(p.angle()==NightBasicVisuals.STAB){
            Vec3 tip=at.add(f.scale(.2+t*.62));
            wake(at.subtract(f.scale(.2)),tip,.13,.9);
            plane(tip,f,u,.28,.18,NIGHT_GRAY_SLASH,SILVER,.9,0,true);
            if(!low)smoke(at.subtract(f.scale(.1)),.15,phase,.4,2);
        }else if(p.angle()==NightBasicVisuals.HAMMER){
            Vec3 head=at.add(f.scale(.15)).add(u.scale(.55-t*.95));
            // Narrow vertical cut: not a large horizontal crescent covering the crosshair.
            plane(head,u,f,.48,.21,NIGHT_GRAY_SLASH,WHITE,.95,0,true);
            plane(head.add(s.scale(.02)),u,s,.44,.17,NIGHT_GRAY_SLASH,ASH,.67,2,true);
            wake(head.add(u.scale(.45)),head,.13,.7);
        }else{
            Vec3 head=at.add(s.scale(.4-t*.8)).add(f.scale(.25));
            slash(head,f,.42,1.1-t*.45,.92);
            if(!low)wake(at.add(s.scale(.45)),head,.12,.55);
        }
    }
}
