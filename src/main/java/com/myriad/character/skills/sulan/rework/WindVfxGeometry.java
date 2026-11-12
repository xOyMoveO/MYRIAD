package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Broad celadon cutting planes, shaded thickness and separated airflow slats. */
public final class WindVfxGeometry {
    private WindVfxGeometry(){}
    private static void rotor(AbilityMeshBuilder g,Vec3 at,double radius,double spin){
        g.bladeArc(at,radius,spin,Math.PI*.8,radius*.3,.14,0xECFFF6,0x387F72);
        g.bladeArc(at.add(0,.015,0),radius,spin+Math.PI,Math.PI*.8,radius*.25,.14,0xA3D6C3,0x245B56);
    }
    public static List<AbilityVfxGeometry.Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();
        double r=Math.max(.08,p.radius()),yaw=Math.atan2(d.z,d.x);
        switch(p.kind()){
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                rotor(g,head,r*1.15,age*(p.angle()==180?-.32:.32));
                g.decal(head.add(0,.155,0),r,WIND_BLADE,0xFFFFFF,.94);
                g.trail(at,head,.16,WIND_STREAK,0xCCE5D8,.7);
            }
            case ARC -> {
                double sweep=Math.toRadians(p.angle());
                g.bladeArc(at,r,yaw-sweep*.5+g.progress*.15,sweep,Math.min(.45,r*.3),.15,0xDDF7EA,0x377969);
                g.plane(at.add(d.scale(r*.38)),s,v,r*.65,r*.4,WIND_BLADE,0xFFFFFF,.95);
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1E-8)break;
                g.trail(at,end,.38,WIND_STREAK,0xE8FFF4,.98);
                int n=reduced?2:3;for(int i=0;i<n;i++){
                    Vec3 point=at.lerp(end,(i+.65)/n);
                    g.plane(point,d,s,.42,.22,WIND_BLADE,0xFFFFFF,.8);
                }
            }
            case BURST -> {
                if(p.angle()==1){rotor(g,at,.3*(1-g.progress*.5),age*.15);break;}
                g.decal(at.add(0,.015,0),r,WIND_IMPACT,0xFFFFFF,.95);
                rotor(g,at.add(0,.12,0),r*(.35+.5*g.progress),age*.06);
                g.sparks(at,r,0xD5F6E4);
            }
            case SEAL -> {
                boolean warning=p.angle()==359;
                g.ring(at,r,.065,warning?0xD4D2A4:0x87BBA7,.9);
                if(p.personal()){g.decal(at.add(0,.015,0),r*.85,WIND_SIGIL,0xFFFFFF,.95);break;}
                if(warning){g.decal(at.add(0,.015,0),r*.75,WIND_SIGIL,0xFFFFFF,.5);break;}
                if(p.variant()==3){ // Enemy mark is above the feet, not an implied large damage field.
                    g.plane(at.add(0,.8,0),s,UP,.3,.4,WIND_SIGIL,0xFFFFFF,.96);
                    rotor(g,at.add(0,.12,0),Math.min(r,.6),age*.035);
                }else{
                    int n=reduced?3:4;
                    for(int i=0;i<n;i++){
                        double a=i*Math.PI*2/n+age*.012;Vec3 point=circle(at,r*.82,a,.55+.12*Math.sin(age*.15+i));
                        g.plane(point,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.6,.25,WIND_BLADE,0xFFFFFF,.95);
                    }
                    for(int i=0;i<2;i++)g.bladeArc(at.add(0,.22,0),r*.83,age*.014+i*Math.PI,Math.PI*.25,Math.min(.18,r*.15),.12,0xD6F3E3,0x3A7C69);
                    g.decal(at.add(0,.025,0),Math.min(r*.55,.8),WIND_SIGIL,0xFFFFFF,.85);
                }
            }
            case SHIELD -> {
                for(int i=0;i<(reduced?3:4);i++){
                    double a=i*Math.PI*2/(reduced?3:4)+age*.05;
                    Vec3 point=circle(at,r,a,.65+.15*Math.sin(a));
                    g.plane(point,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.48,.22,WIND_BLADE,0xFFFFFF,.95);
                }
                rotor(g,at.add(0,.12,0),r,age*.035);
            }
            case CHARGE -> {
                rotor(g,at.add(0,.12,0),.65,age*.12);
                for(int i=0;i<4;i++){
                    Vec3 point=at.lerp(end,(i+.4)/4);
                    g.plane(point.add(0,.035,0),s,d,Math.max(.15,r),.35,WIND_SIGIL,0xFFFFFF,.62);
                }
            }
            case WALL -> {
                int n=reduced?5:8;
                for(int i=0;i<n;i++){
                    double y=.14+i*3.5/(n-1),wave=Math.sin(age*.4+i)*.12;
                    Vec3 centre=at.add(0,y,0).add(d.scale(wave));
                    g.plane(centre,s,UP,r,.28,WIND_STREAK,0xD7F2E4,.9);
                    Vec3 left=centre.subtract(s.scale(r*.96)),mid=centre.add(d.scale(.12)),right=centre.add(s.scale(r*.96));
                    g.line(left,mid,d,.11,0x82B9A1,.92);g.line(mid,right,d,.11,0xBDDCCA,.94);
                    if(i%2==0)g.line(centre.subtract(s.scale(r*.85)),centre.add(s.scale(r*.85)),d,.035,0xEDFDF4,.95);
                }
                g.rod(at.subtract(s.scale(r)),at.subtract(s.scale(r)).add(0,3.6,0),.025,0xCBE7DA,0x579788,0x27534C,.75);
                g.rod(at.add(s.scale(r)),at.add(s.scale(r)).add(0,3.6,0),.025,0xCBE7DA,0x579788,0x27534C,.75);
            }
            case PILLAR -> {g.trail(at,end,.35,WIND_STREAK,0xE5FFF1,.95);rotor(g,at.add(0,.12,0),r,age*.08);}
        }
        return g.result();
    }
}
