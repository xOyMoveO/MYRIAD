package com.myriad.character.skills.qingluo.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.FrostHerbLayers.*;

public final class HerbVfxGeometry {
    private HerbVfxGeometry() {}

    public static List<Quad> build(AbilityVfxPayload p, float age, boolean reduced) {
        var b = new AbilityMeshBuilder(p,age,reduced,maxQuads(p));
        Vec3 at=p.from(),end=p.to(),forward=direction(at,end),right=side(forward);
        double r=p.kind()==TRAIL&&p.angle()==270?p.radius():Math.max(.28,p.radius()),clock=p.started()%240+age;
        switch(p.kind()){
            case FLIGHT -> {
                if (at.distanceToSqr(end)<1e-8) break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                Vec3 up=right.cross(forward);
                com.myriad.character.ability.FrostHerbModels.herb(b,head,forward,p.variant(),clock);
                Vec3 tail=head.subtract(forward.scale(p.variant()==0?1.02:1.46));
                Vec3 heel=head.subtract(forward.scale(.25));
                ribbon(b,tail,heel,right,.14,HERB_HD_THREAD,clock,.90);
                ribbon(b,tail,heel,up,.19,HERB_HD_VINE,clock*.60+1,.84);
                b.frame(head.subtract(forward.scale(.32)),right,up,.25,.20,HERB_HD_DEW,(int)(clock*.83),.76);
                if(!reduced)b.frame(tail.lerp(heel,.54),forward,up,.44,.28,HERB_HD_LEAF,(int)(clock*.68),.73);
            }
            case BEAM,TRAIL -> {
                Vec3 up=right.cross(forward).normalize();
                Vec3 offset=up.scale(.046);
                boolean tether=p.variant()==5&&p.kind()==BEAM;
                ribbon(b,at,end,right,tether?.128:r,HERB_HD_THREAD,clock,.93);
                ribbon(b,at.add(offset),end.add(offset),up,tether?.09:r*.78,
                        tether?HERB_HD_THREAD:HERB_HD_VINE,clock*.72+2,.78);
                if (p.kind()==BEAM) {
                    if(at.distanceToSqr(end)>1e-6){
                        b.line(at,end,UP,tether?.016:.021,0xE8F5B8,.96);
                        double advance=(clock%14)/14;
                        Vec3 bead=at.lerp(end,advance);
                        b.frame(bead,right,up,.18,.22,HERB_HD_DEW,(int)clock,.93);
                    }else {
                        b.frame(at,X,UP,.28,.34,HERB_HD_DEW,(int)clock,.87);
                    }
                } else {
                    chips(b,end,r*1.8,.28,HERB_HD_PETAL,clock,3,.58);
                }
            }
            case SEAL -> field(b,p,at,r,clock);
            case SHIELD -> canopy(b,p.variant(),at,r,clock);
            case CHARGE -> {
                int count=p.variant()==1?(int)Math.clamp(Math.round(p.radius()),1,3):3;
                for (int i=0;i<count;i++){
                    double a=clock*.14+i*2.094;
                    Vec3 point=circle(at,.55,a,.78+.12*Math.sin(clock*.1+i));
                    Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
                    b.frame(point,tangent,UP,.16,.23,HERB_HD_DEW,(int)clock+i,.91);
                    b.frame(point.add(0,-.09,0),tangent,UP,.18,.19,HERB_HD_LEAF,(int)(clock*.57)+i,.78);
                }
            }
            case BURST -> flourish(b,p,at,r,clock);
            case PILLAR -> flourish(b,p,end,r,clock);
            case ARC -> {
                Vec3 point=at.add(forward.scale(.36));
                b.frame(point,right,UP,.36,.49,HERB_HD_LEAF,(int)clock,.84);
                b.frame(point.add(forward.scale(.048)),forward,UP,.63,.37,HERB_HD_VINE,(int)(clock*.65),.92);
                chips(b,point,r*.77,.72,HERB_HD_DEW,clock,5,.84);
            }
            default -> {
                rim(b,at,r,HERB_HD_RIM,clock*.32,.92);
                chips(b,at,r*.85,.5,HERB_HD_DEW,clock,4,.84);
            }
        }
        return b.result();
    }

    private static void field(AbilityMeshBuilder b,AbilityVfxPayload p,Vec3 at,double r,double clock){
        if(p.variant()==2||p.variant()==5){
            b.frame(at.add(0,.20,0),X,UP,r*.62,.36,HERB_HD_LEAF,(int)clock,.91);
            b.frame(at.add(0,.46,0),Z,UP,r*.46,.29,HERB_HD_DEW,(int)(clock*.78),.80);
            return;
        }
        boolean warning=p.angle()==359;
        double expand=Math.min(1,.77+b.progress*(p.life()>35?2.5:.30));
        int border=p.variant()==6 ? HERB_HD_GROVE_BORDER : HERB_HD_RIM;
        rim(b,at,r*expand,border,clock*.36,warning?.68:.95);
        rim(b,at.add(0,.024,0),r*expand*.89,HERB_HD_RIM,clock*.51+2,warning?.36:p.variant()==4?.52:.67);
        if(warning)return;
        if(p.variant()==4||p.variant()==6||p.variant()==7){
            int count=b.reduced?4:p.variant()==6?9:7;
            for(int i=0;i<count;i++){
                double angle=i*Math.PI*2/count+.11;
                Vec3 tangent=new Vec3(-Math.sin(angle),0,Math.cos(angle));
                double height=p.variant()==7?.62+b.progress*.62:p.variant()==6?.37+(i%3)*.16:.23;
                Vec3 point=circle(at,r*.85*expand,angle,height*.81);
                int foliage=p.variant()==6 ? HERB_HD_SPROUT : HERB_HD_LEAF;
                b.frame(point,tangent,UP,.36+r*.032,height,foliage,(int)(clock*.6+i),p.variant()==7?.78:.67);
                if(!b.reduced) b.frame(point.add(0,height*.74,0),tangent,UP,.34,.30,
                        p.variant()==6?HERB_HD_PETAL:HERB_HD_DEW,(int)(clock*.82+i),.63);
            }
        }else{
            chips(b,at,r,.53,HERB_HD_DEW,clock,4,.90);
        }
    }

    private static void canopy(AbilityMeshBuilder b,int variant,Vec3 at,double r,double clock){
        int count=b.reduced?3:variant==7?6:4;
        double width=variant==7?.41:variant==5?.29:.32;
        for(int i=0;i<count;i++){
            double angle=i*Math.PI*2/count+clock*.054;
            Vec3 tangent=new Vec3(-Math.sin(angle),0,Math.cos(angle));
            Vec3 inward=new Vec3(-Math.cos(angle),0,-Math.sin(angle));
            Vec3 lean=UP.add(inward.scale(variant==7?.43:.25)).normalize();
            Vec3 point=circle(at,r+.13,angle,variant==7?.92+.11*(i%2):.73);
            b.frame(point,tangent,lean,width,variant==7?.69:.51,HERB_HD_LEAF,(int)(clock*.54+i),.90);
            b.frame(point.subtract(inward.scale(.027)).add(0,.17,0),tangent,lean,width*.76,.48,
                    HERB_HD_DEW,(int)(clock*.85+i),.77);
        }
    }

    private static void flourish(AbilityMeshBuilder b,AbilityVfxPayload p,Vec3 at,double r,double clock){
        boolean bind=p.variant()==3&&p.angle()!=HerbVisuals.RESTORATION;
        boolean fieldPulse=p.anchor()<0&&(p.variant()==4||p.variant()==6||p.variant()==7);
        double reach=r*(.61+b.progress*.38);
        int count=b.reduced?3:fieldPulse?8:5;
        for(int i=0;i<count;i++){
            double a=i*2.399963+.11;
            Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
            Vec3 rise=UP.add(Math.cos(a)*.27,0,Math.sin(a)*.27).normalize();
            Vec3 point=circle(at,reach*(.36+(i%3)*.21),a,.20+b.progress*(bind?.20:.70));
            b.frame(point,tangent,rise,.23+reach*.082,.32+reach*.07,
                    bind?HERB_HD_VINE:p.variant()==6?HERB_HD_SPROUT:HERB_HD_LEAF,(int)(clock*.75+i),.88);
        }
        if(fieldPulse)rim(b,at.add(0,.025,0),reach,HERB_HD_RIM,clock*.55,.85);
        chips(b,at,reach+.2,bind?.72:1.12,bind?HERB_HD_PETAL:HERB_HD_DEW,clock,6,.88);
        if(!b.reduced&&p.variant()!=4) chips(b,at,reach*.72,.75,HERB_HD_PETAL,clock*.69+1,4,.65);
    }

}
