package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Ink-dark bindings, folded parchment and brass frames make a constructed array. */
public final class InkVfxGeometry {
    private InkVfxGeometry(){}
    private static void paper(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,double scale){
        Vec3 normal=right.cross(up).normalize();
        g.plane(at,right,up,.21*scale,.4*scale,INK_TALISMAN,0xFFFFFF,.98);
        g.line(at.subtract(up.scale(.39*scale)),at.add(up.scale(.39*scale)),normal,.035,0x997A48,.95);
        g.plane(at.add(normal.scale(.025)),right,up,.15*scale,.32*scale,INK_BIND,0xFFFFFF,.8);
    }
    private static void pillar(AbilityMeshBuilder g,Vec3 feet,double height,double angle){
        g.rod(feet,feet.add(0,height,0),.055,0xE9CC8B,0x977949,0x3A2D29,.94);
        paper(g,feet.add(0,height*.65,0),new Vec3(Math.cos(angle),0,Math.sin(angle)),UP,.72);
    }
    public static List<AbilityVfxGeometry.Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();
        double r=Math.max(.08,p.radius());
        switch(p.kind()){
            case SEAL -> {
                boolean warning=p.angle()==359;
                g.decal(at,r,INK_SEAL,0xFFFFFF,warning?.58:.94);
                if(p.personal()||warning){g.ring(at.add(0,.015,0),r,.055,0xC9A46B,.85);break;}
                if(p.variant()==4){
                    pillar(g,circle(at,r*.78,0,.02),1.65,0);
                    pillar(g,circle(at,r*.78,Math.PI,.02),1.65,Math.PI);
                    g.line(at.add(-r*.78,1.65,0),at.add(r*.78,1.65,0),Z,.08,0xC9A46B,.9);
                    g.decal(at.add(0,.16,0),r*.65,INK_SEAL,0xFFFFFF,.75);
                }else{
                    int n=reduced?3:4;
                    for(int i=0;i<n;i++)pillar(g,circle(at,r*.82,i*Math.PI*2/n,.02),p.variant()==6?1.65:.9,i*Math.PI*2/n);
                    g.ring(at.add(0,.13,0),r*.88,.055,0xD5B77C,.9);
                }
            }
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                g.triangle(head.add(s.scale(-.19)).add(v.scale(-.36)),head.add(v.scale(.38)).add(d.scale(.04)),head.add(s.scale(.19)).add(v.scale(-.36)),0xB39A67,.94);
                paper(g,head,s,v,1);
                g.trail(at.subtract(d.scale(.35)),head,.24,INK_BIND,0xFFFFFF,.94);
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1E-8)break;
                g.rod(at,end,Math.clamp(r*.3,.035,.075),0xC9B58A,0x4F5962,0x202631,.94);
                g.trail(at,end,.18,INK_BIND,0xFFFFFF,.85);
                int n=reduced?2:4;for(int i=0;i<n;i++)paper(g,at.lerp(end,(i+.5)/n),s,v,.38);
            }
            case SHIELD -> {
                int n=reduced?3:4;
                for(int i=0;i<n;i++){
                    double a=i*Math.PI*2/n+Math.sin(age*.06)*.1;
                    Vec3 right=new Vec3(-Math.sin(a),0,Math.cos(a)),feet=circle(at,r,a,.18);
                    paper(g,feet.add(0,.6,0),right,UP,.9);
                    g.rod(feet,feet.add(0,1.2,0),.035,0xE7CA90,0x9C7C4A,0x322A25,.9);
                }
                g.decal(at.add(0,.04,0),r,INK_SEAL,0xFFFFFF,.85);
            }
            case BURST -> {
                if(p.angle()==1){g.plane(at,s,v,.3,.3,INK_BREAK,0xFFFFFF,.85);g.sparks(at,.3,0xCBB17A);break;}
                g.decal(at.add(0,.015,0),Math.min(r,1.5),INK_BREAK,0xFFFFFF,.95);
                g.ring(at.add(0,.11,0),r*(.4+.55*g.progress),.075,0xC8A269,.9);
                for(int i=0;i<(reduced?3:5);i++){
                    double a=i*2.399963;
                    Vec3 point=circle(at,r*(.2+.4*g.progress),a,.22+g.progress*.5);
                    paper(g,point,new Vec3(Math.cos(a),.25,Math.sin(a)).normalize(),UP,.3);
                }
            }
            case ARC -> {
                double yaw=Math.atan2(d.z,d.x),sweep=Math.toRadians(p.angle());
                g.bladeArc(at,r,yaw-sweep*.5,sweep,Math.min(.28,r*.2),.12,0xD6C291,0x37414D);
                g.plane(at.add(d.scale(r*.45)),s,v,r*.48,r*.45,INK_BIND,0xFFFFFF,.85);
            }
            case CHARGE -> {
                g.decal(at,.75,INK_SEAL,0xFFFFFF,.9);
                for(int i=0;i<(reduced?3:4);i++){
                    double a=i*Math.PI/2+age*.04;
                    paper(g,circle(at,.65,a,.75),new Vec3(Math.cos(a),0,Math.sin(a)),UP,.6);
                }
            }
            case PILLAR -> {pillar(g,at,Math.min(5,at.distanceTo(end)),0);g.decal(at,r,INK_SEAL,0xFFFFFF,.95);}
            case WALL -> {for(int i=0;i<3;i++)pillar(g,at.add(s.scale(r*(i-1))),1.8,i);}
        }
        return g.result();
    }
}
