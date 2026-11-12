package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Giant sword silhouette, stable overhead pivot, layered painted energy and a full-length impact. */
public final class HeavenSwordVfx {
    private static final int CYAN=0x39E8ED,GOLD=0xFFD45C,WHITE=0xF5FCFF;
    private HeavenSwordVfx(){}
    public static boolean owns(AbilityVfxPayload p){return p.theme()==5&&p.variant()==7&&(p.kind()==CHARGE||p.kind()==ARC||p.kind()==BEAM);}
    public static AABB bounds(AbilityVfxPayload p){return new AABB(p.from(),p.to()).inflate(11,19,11);}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        var b=new AbilityMeshBuilder(p,age,low);var out=new ArrayList<Quad>();
        var px=new SwordPixelLayers(out,b.fade,low,p.started(),age);
        Vec3 raw=direction(p.from(),p.to()),f=raw.multiply(1,0,1).normalize();
        if(f.lengthSqr()<1E-8)f=Z;
        Vec3 right=side(f),at=p.from(),end=at.add(f.scale(HeavenSwordSkill.REACH));
        double phase=((p.started()%80)+age)*Math.PI/40;
        if(p.kind()==CHARGE){
            double progress=Math.clamp(p.angle()/360.0+age/HeavenSwordSkill.CHARGE_TICKS,0,1);
            double length=12+5*progress;
            Vec3 blade=bladeDirection(f,Math.toRadians(-18));
            Vec3 pivot=at.subtract(f.scale(.6)).add(0,1.2,0);
            append(out,low,pivot,blade,right,length,(float)(b.fade*(.45+.5*progress)));
            Vec3 middle=pivot.add(blade.scale(length*.42));
            px.halo(middle,raw,1.0+progress*.45,phase*.2);
            px.crown(middle,raw,.65+progress*.25,-phase,6);
            px.wake(pivot.add(blade),pivot.add(blade.scale(length-2)),.3+progress*.08,phase);
            footprint(b,at,end,right,progress,true);
        }else if(p.kind()==ARC){
            double progress=Math.clamp(age/HeavenSwordSkill.CHOP_TICKS,0,1);
            // Acceleration into the ground, followed by a brief held silhouette, not a flying sword.
            double eased=progress*progress;
            double angle=Math.toRadians(-18+112*eased);
            Vec3 pivot=at.subtract(f.scale(.6)).add(0,1.2,0),blade=bladeDirection(f,angle);
            append(out,low,pivot,blade,right,17,b.fade*.96f);
            if(!low&&progress>0&&progress<1){
                Vec3 previous=bladeDirection(f,angle-Math.toRadians(16));
                SwordModelAsset.appendOriented(out,"body_lod",pivot.add(previous.scale(17*.5-1.4)),previous,cuttingEdge(previous,f),17,b.fade*.22f);
            }
            SwordSweepLayers.append(out,p,age,low,pivot,f,angle,progress,b.fade);
            px.wake(pivot.add(blade.scale(1.5)),pivot.add(blade.scale(15.6)),.38,phase);
            px.slash(pivot.add(blade.scale(10)),raw,1.35,Math.PI/2);
            footprint(b,at,end,right,1,false);
        }else{
            // Visible fissure occupies the same 15x3 footprint as the server hit, not a moving beam.
            footprint(b,at,end,right,1,false);
            px.wake(at.add(0,.18,0),end.add(0,.18,0),.3,phase);
            int count=low?3:5;
            for(int i=0;i<count;i++){
                Vec3 hit=at.lerp(end,(i+.5)/count).add(0,.22,0);
                px.impact(hit,raw,low?.8:1.15,b.progress);
                if(!low)px.chip(hit.add(right.scale((i%2==0?1:-1)*(1+b.progress*.35))).add(0,.3+b.progress,0),.2,GOLD,1-b.progress*.7);
            }
            px.ring(end.add(0,.08,0),1.4*(.65+b.progress*.35),phase*.2,GOLD);
        }
        out.addAll(b.result());
        if(out.size()>4096)throw new IllegalStateException("Giant sword exceeded its complete-mesh budget");
        return List.copyOf(out);
    }
    private static Vec3 bladeDirection(Vec3 f,double angle){return UP.scale(Math.cos(angle)).add(f.scale(Math.sin(angle)));}
    private static void append(List<Quad> out,boolean low,Vec3 pivot,Vec3 blade,Vec3 right,double length,float alpha){
        // The mesh is wide in local X and thin in local Z. Local X must lead the cut,
        // not point sideways (which would hit with the sword's broad flat).
        Vec3 forward=UP.cross(right).normalize();
        SwordModelAsset.appendOriented(out,low?"body_lod":"body",pivot.add(blade.scale(length*.5-1.4)),blade,cuttingEdge(blade,forward),length,alpha);
    }
    /** Local +X is the cutting edge, tangent to the actual overhead swing at every pitch. */
    public static Vec3 cuttingEdge(Vec3 blade,Vec3 forward){
        Vec3 f=forward.multiply(1,0,1).normalize();if(f.lengthSqr()<1E-8)f=Z;
        return blade.normalize().cross(side(f)).normalize();
    }
    private static void footprint(AbilityMeshBuilder b,Vec3 from,Vec3 to,Vec3 right,double progress,boolean warning){
        Vec3 a=from.add(0,.065,0),z=to.add(0,.065,0),s=right.scale(HeavenSwordSkill.WIDTH/2);
        int color=warning?CYAN:GOLD;
        b.quad(a.subtract(s),z.subtract(s),z.add(s),a.add(s),color,warning?.10:.18,0);
        b.line(a.subtract(s),z.subtract(s),UP,.07,color,.95);
        b.line(a.add(s),z.add(s),UP,.07,color,.95);
        b.line(a.subtract(s),a.add(s),UP,.07,color,.95);
        b.line(z.subtract(s),z.add(s),UP,.07,color,.95);
        b.line(a,a.lerp(z,Math.max(.02,progress)),UP,warning?.10:.20,WHITE,.98);
        // Sparse inward chevrons keep the warning legible without a filled opaque floor.
        for(int i=1;i<=4;i++){
            Vec3 mid=a.lerp(z,i/5.0),back=mid.subtract(to.subtract(from).normalize().scale(.45));
            b.line(back.add(s.scale(.6)),mid,UP,.045,color,.8);
            b.line(back.subtract(s.scale(.6)),mid,UP,.045,color,.8);
        }
    }
}
