package com.myriad.character.skills.chixiao.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.WarSpiritModels;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Scarlet lacquer and forged edges. Payload phases identify actual action events. */
public final class WarVfxGeometry {
    private static final int IVORY=0xFFF5DF, VERMILION=0xF84963, BRONZE=0xFFC895, DARK=0x842742;

    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced) {
        var b=new AbilityMeshBuilder(p,age,reduced,maxQuads(p));
        Vec3 at=p.from(),end=p.to(),f=direction(at,end),s=side(f),up=s.cross(f).normalize();
        double r=Math.max(.16,p.radius()),t=b.progress;
        int k=(int)(p.started()%16+age);
        switch(p.kind()) {
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1e-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                if(WarSpiritModels.flight(p))WarSpiritModels.wave(b,head,f,s.scale(-1));
                b.frame(head.add(up.scale(.095)).add(f.scale(.36)),s,f,1.16,.67,WAR_HD_EDGE,k,.96);
                b.frame(head.subtract(f.scale(.50)).subtract(up.scale(.13)),s,f,1.07,.98,WAR_HD_LACQUER,k+1,.78);
                if(!reduced) {
                    ribbon(b,at.subtract(f.scale(.85)),head.subtract(f.scale(.31)),s,.35,k,.88);
                    b.frame(head.subtract(f.scale(.63)),s,up,.94,.20,WAR_HD_FLAKES,k,.82);
                }
            }
            case ARC -> {
                double span=Math.toRadians(p.angle()==0?80:p.angle()),yaw=Math.atan2(f.z,f.x);
                double extent=r*Math.sin(span*.5),front=r*Math.cos(span*.5);
                Vec3 center=at.add(f.scale(front*.82));
                boolean counter=p.variant()==4;
                Vec3 tilt=f.add(0,counter?-.23:.14,0).normalize();
                double sweep=Math.min(1,.42+t*1.55);
                b.frame(center.subtract(UP.scale(.13)),s,tilt,extent,.54*r,WAR_HD_LACQUER,k,.90);
                b.frame(center.add(UP.scale(.08)),s,tilt,extent*(.82+sweep*.18),.48*r,WAR_HD_EDGE,k,.98);
                if(!reduced) {
                    b.frame(center.add(0,.21,0),s,f,extent,.52*r,WAR_HD_FLAKES,k+2,.76);
                    if(p.variant()==2||counter)b.bladeArc(at.add(0,-.55,0),r*.97,yaw-span*.5,span,.075,.01,
                            counter?IVORY:BRONZE,DARK);
                }
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1e-8)break;
                boolean returning=p.angle()==180;
                if(p.variant()==6) {
                    ribbon(b,at,end,up,.37,k,.95);
                    b.frame(at.lerp(end,.5).subtract(up.scale(.17)),s,up,.61,.34,WAR_HD_FLAKES,k+1,.78);
                } else {
                    for(int sign:new int[]{-1,1}) {
                        Vec3 offset=s.scale(sign*(returning?.29:.48));
                        ribbon(b,at.add(offset),end.add(offset),up,returning?.10:.23,k,.94);
                        b.line(at.add(offset).add(0,.022,0),end.add(offset).add(0,.022,0),UP,returning?.045:.075,
                                returning?IVORY:VERMILION,.90);
                    }
                    if(!reduced)b.frame(at.lerp(end,.5),s,up,.66,.29,WAR_HD_FLAKES,k,.76);
                }
            }
            case SEAL -> {
                if(p.variant()==0) {
                    int marks=(int)Math.clamp(p.angle(),1,3);
                    for(int i=0;i<marks;i++) {
                        Vec3 pos=at.add(s.scale((i-1)*.26)).add(0,.22,0);
                        b.frame(pos,s,UP,.125,.245,WAR_HD_SIGIL,k+i,marks==3?.99:.74);
                    }
                } else if(p.variant()==7&&p.angle()<=3&&r<1) {
                    int marks=(int)p.angle();
                    for(int i=0;i<marks;i++) {
                        Vec3 pos=at.add(s.scale((i-1)*.27)).add(f.scale(.64)).add(0,.18,0);
                        b.frame(pos,s,UP,.09,.19,WAR_HD_SIGIL,i,.97);
                    }
                } else {
                    double grow=p.angle()==359?1:.83+t*.17;
                    b.frame(at.add(0,.024,0),X,Z,r*grow,r*grow,WAR_HD_RIM,k,.87);
                    if(p.variant()==3||p.variant()==1)b.frame(at.add(0,.08,0),s,f,r*.8,r*.56,WAR_HD_SIGIL,k,.94);
                }
            }
            case SHIELD -> {
                if(p.variant()==4) {
                    Vec3 pos=at.add(f.scale(.78)).add(0,1,0);
                    b.frame(pos,s,UP,.57,.62,WAR_HD_SIGIL,k,.85);
                    for(int sign:new int[]{-1,1}) {
                        Vec3 blade=UP.add(s.scale(sign*.74)).normalize();
                        b.frame(pos.add(f.scale(.032*sign)),blade,f.cross(blade),.78,.28,WAR_HD_RIBBON,k+sign,.99);
                    }
                } else {
                    int n=reduced?2:(p.variant()==7?4:3);
                    for(int i=0;i<n;i++) {
                        double a=i*Math.PI*2/n;
                        Vec3 pos=circle(at,r+.14,a,.86+t*.17),tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
                        b.frame(pos,tangent,UP,.30,.51,WAR_HD_SIGIL,k+i,.89);
                    }
                    b.frame(at.add(0,.08,0),X,Z,r+.26,r+.26,WAR_HD_RIM,k,.83);
                }
            }
            case BURST -> {
                if(p.variant()==6&&p.angle()!=1) {
                    double travel=.34+t*.65;
                    b.frame(at.add(0,.02,0),X,Z,r*travel,r*travel,WAR_HD_RIM,k,.98);
                    for(int i=0;i<(reduced?4:7);i++) {
                        double a=i*2.39996;
                        Vec3 point=circle(at,r*(.3+t*.53),a,.22+t*.76);
                        Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a));
                        b.frame(point,tang,UP,.47,.37,WAR_HD_SIGIL,k+i,.87);
                    }
                    b.frame(at.add(0,.18,0),X,Z,r*.82,r*.82,WAR_HD_FLAKES,k,.83);
                } else {
                    Vec3 pos=at.add(0,p.angle()==1?0:.32,0);
                    b.frame(pos,s,up,r*.86,r*.56,WAR_HD_FLAKES,k,.99);
                    b.frame(pos.add(.019,.065,0),s,up,r*.54,r*.62,WAR_HD_SIGIL,k,.87);
                    if(p.variant()==0)b.frame(at.add(0,.045,0),X,Z,r,r,WAR_HD_RIM,k,.90);
                }
            }
            default -> {
                b.frame(at,s,up,r,.37*r,WAR_HD_SIGIL,k,.96);
                b.frame(at.add(0,.06,0),X,Z,r,r,WAR_HD_RIM,k,.85);
            }
        }
        return b.result();
    }

    private static void ribbon(AbilityMeshBuilder b,Vec3 a,Vec3 z,Vec3 normal,double h,int k,double alpha) {
        double length=a.distanceTo(z);
        if(length>.015)b.frame(a.lerp(z,.5),direction(a,z),normal,length*.52,h,WAR_HD_RIBBON,k,alpha);
    }
}
