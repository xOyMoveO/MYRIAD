package com.myriad.character.skills.keheng;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Chronometric textured layers in world space, separate from the finite physical needle body. */
public final class KehengVfx {
    public static final int THEME=11,TEAL=0x42D9BD,GOLD=0xF1C76B,WHITE=0xF2F6EA,ECHO=0x8C7CA6;
    public static final int WARNING=300,NATURAL_END=200,INTERRUPTED_END=201,SHIELD_BREAK=202,SHIELD_EXPIRE=203,BASIC=210;
    private static final RosterProjectileModels MODELS=RosterProjectileModels.load("keheng_projectiles_mesh.json");
    private KehengVfx(){}
    public static boolean persistent(AbilityVfxPayload p){return p.theme()==THEME&&p.instance()>0&&(p.kind()==SEAL||p.kind()==CHARGE);}
    public static boolean replaces(AbilityVfxPayload incoming,AbilityVfxPayload old){
        if(incoming.theme()!=THEME||old.theme()!=THEME||incoming.owner()!=old.owner()||incoming.variant()!=old.variant())return false;
        if(incoming.kind()==BURST&&(incoming.angle()==SHIELD_BREAK||incoming.angle()==SHIELD_EXPIRE))return old.kind()==SHIELD&&old.anchor()==incoming.anchor();
        return incoming.kind()==old.kind()&&incoming.anchor()==old.anchor()&&incoming.instance()==old.instance()
                &&(persistent(incoming)||incoming.kind()==SHIELD||incoming.kind()==SEAL&&incoming.variant()==0);
    }
    public static boolean obsolete(AbilityVfxPayload incoming,AbilityVfxPayload old){return replaces(incoming,old)&&incoming.started()<old.started();}
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload p){
        return p.theme()==THEME&&p.kind()==FLIGHT&&p.from().distanceToSqr(p.to())>1E-8
                ?new AbilityVfxPayload(p.owner(),-1,THEME,p.variant(),TRAIL,p.from(),p.to(),.12f,0,4,p.started(),false):null;
    }
    public static boolean usesProjectileModel(AbilityVfxPayload p){return p.theme()==THEME&&(p.kind()==FLIGHT||p.kind()==BEAM&&p.angle()!=BASIC);}
    public static int modelFaces(boolean low){return MODELS.faces(low?"needle_lod":"needle");}
    public static int modelParts(boolean low){return MODELS.parts(low?"needle_lod":"needle");}
    public static AABB bounds(AbilityVfxPayload p){
        double extent=p.kind()==FLIGHT?1.02:p.radius()+.6;
        // Frustum bounds include the full rotating sprite canvas, including its transparent corners.
        // Visible field alpha has its own exact-radius contract and is not enlarged here.
        if(p.kind()==ARC)extent=Math.max(extent,p.radius()*.94*Math.sqrt(2)+.05);
        if(p.kind()==BURST&&(p.angle()==NATURAL_END||p.angle()==INTERRUPTED_END))
            extent=Math.max(extent,p.radius()*(p.angle()==INTERRUPTED_END?1.08:1)*Math.sqrt(2)+.05);
        return new AABB(p.from(),p.to()).inflate(extent).expandTowards(0,p.variant()==7?3:p.variant()==0?1.3:0,0);
    }
    private static double clock(AbilityVfxPayload p,float age){return (double)p.started()+age;}
    private static void stamp(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,double w,double h,int material,double alpha){g.plane(at,right,up,w,h,material,0xFFFFFF,alpha);}
    private static void flat(AbilityMeshBuilder g,Vec3 at,double size,double turn,int material,double alpha){
        var right=new Vec3(Math.cos(turn),0,Math.sin(turn));var across=new Vec3(-Math.sin(turn),0,Math.cos(turn));
        stamp(g,at,right,across,size,size,material,alpha);
    }
    private static void strip(AbilityMeshBuilder g,Vec3 from,Vec3 to,Vec3 normal,double width,int material,double alpha){
        var d=to.subtract(from);if(d.lengthSqr()<1E-10)return;var s=d.cross(normal);if(s.lengthSqr()<1E-10)s=side(d.normalize());s=s.normalize().scale(width*.5);
        if(material==KEHENG_HD_VEIL)g.quad(from.subtract(s),from.add(s),to.add(s),to.subtract(s),0xFFFFFF,alpha,material);
        else g.quad(from.subtract(s),to.subtract(s),to.add(s),from.add(s),0xFFFFFF,alpha,material);
    }
    private static void motes(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,double size,double age,double alpha){
        stamp(g,at.add(right.scale(Math.sin(age*.2)*size*.1)),right,up,size,size,KEHENG_HD_MOTES,alpha);
    }
    /** Four explicit boundary nodes remain readable before any connecting arc appears. */
    private static void boundary(AbilityMeshBuilder g,Vec3 at,double r,double time,boolean warning,double connect){
        if(connect>0)flat(g,at,r/.984,time*.002,KEHENG_HD_RING,(warning?.88:.83)*connect);
        for(int i=0;i<4;i++){
            double angle=i*Math.PI/2;var node=circle(at,r-.065,angle,.035);var tangent=new Vec3(-Math.sin(angle),0,Math.cos(angle));
            stamp(g,node.add(0,.09,0),tangent,UP,warning?.15:.09,warning?.23:.15,KEHENG_HD_SPARK,warning?.95:.62);
            if(warning)g.line(node,node.add(0,.20,0),tangent,.04,GOLD,.92);
            if(!g.reduced)flat(g,node,.105,angle,KEHENG_HD_SPARK,.80);
        }
    }
    private static void slicedArc(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,double r,double begin,double span,double width,double alpha){
        int n=g.reduced?5:8;Vec3 normal=right.cross(up);
        for(int i=0;i<n;i++){
            double a=begin+span*i/n,b=begin+span*(i+.88)/n;
            var x=at.add(right.scale(r*Math.cos(a))).add(up.scale(r*Math.sin(a)));
            var y=at.add(right.scale(r*Math.cos(b))).add(up.scale(r*Math.sin(b)));
            strip(g,x,y,normal,width,KEHENG_HD_VEIL,alpha);
        }
    }
    private static void contact(AbilityMeshBuilder g,AbilityVfxPayload p,Vec3 at,Vec3 d,Vec3 s,Vec3 up){
        double t=g.progress,small=p.angle()==BASIC?.22:.38;
        stamp(g,at,s,up,small*(1+t*.35),small*(1+t*.35),KEHENG_HD_IMPACT,.95*(1-t*.6));
        if(g.age>=.5)stamp(g,at.subtract(d.scale(.015)),s,up,small*1.8,small*1.8,KEHENG_HD_ARC,.62*(1-t));
        if(g.age>=1||!g.reduced)motes(g,at.add(up.scale(t*.12)),s,up,small*2,g.age,.78*(1-t));
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||age<0||age>=p.life())return List.of();
        if(p.kind()==FLIGHT&&age>=1)return List.of();
        var g=new AbilityMeshBuilder(p,age,reduced);var at=p.from();var end=p.to();var d=direction(at,end);var s=side(d);var up=s.cross(d).normalize();
        var models=new ArrayList<Quad>();double r=p.radius(),time=clock(p,age),phase=time*.035;
        switch(p.kind()){
            case FLIGHT -> {
                var head=at.lerp(end,age);MODELS.append(models,reduced?"needle_lod":"needle",head,d,.92,KEHENG_PROJECTILE_MODEL,.98f);
                // Small transverse glints cannot run past the confirmed forward endpoint.
                stamp(g,head.subtract(d.scale(.025)),s,up,.08,.08,KEHENG_HD_SPARK,.70);
                if(!reduced)motes(g,head.subtract(d.scale(.23)),s,up,.13,time,.42);
            }
            case TRAIL -> {
                if(at.distanceToSqr(end)<1E-6)break;double alpha=1-g.progress;
                if(p.variant()==2){
                    for(double sign:new double[]{-1,1})strip(g,at.add(s.scale(.035*sign)),end.add(s.scale(.018*sign)),up,.055,KEHENG_HD_STREAM,.72*alpha);
                    if(!reduced)strip(g,at.add(up.scale(.055)),end.add(up.scale(.03)),s,.035,KEHENG_HD_VEIL,.35*alpha);
                    motes(g,at.lerp(end,.35),s,up,.12,time,.45*alpha);break;
                }
                strip(g,at,end,UP,.25,KEHENG_HD_STREAM,.78*alpha);
                strip(g,at.add(0,.18,0),end.add(0,.34,0),s,.20,KEHENG_HD_VEIL,.46*alpha);
                if(!reduced)strip(g,at.add(0,.50,0),end.add(0,.62,0),s,.14,KEHENG_HD_ARC,.32*alpha);
                for(int i=0;i<(reduced?2:3);i++)flat(g,at.lerp(end,(i+.5)/3).add(0,-.25,0),.12,i*.7,KEHENG_HD_SPARK,.80*alpha);
            }
            case SEAL -> {
                if(p.variant()==0){
                    int stacks=Math.min(2,(int)p.angle());
                    for(int i=0;i<stacks;i++)stamp(g,at.add(.35,.82+i*.14,0),X,UP,.09,.055,KEHENG_HD_SPARK,.84);
                    if(!reduced)motes(g,at.add(.35,.91,0),X,UP,.17,time,.28);break;
                }
                if(p.angle()>=WARNING){
                    double elapsed=p.angle()-WARNING+age,connect=Math.clamp((elapsed-3)/6,0,1);
                    boundary(g,at,r,0,true,connect);
                    if(connect>0)flat(g,at.add(0,.018,0),r*.82,-phase*.3,KEHENG_HD_ARC,.40*connect);
                    break;
                }
                boundary(g,at,r,0,false,1);double deploy=Math.clamp((p.angle()+age+1)/6,0,1);
                if(p.variant()==6){
                    for(int k=0;k<(reduced?3:4);k++){
                        double yaw=k*Math.PI/(reduced?3:4);slicedArc(g,at,new Vec3(Math.cos(yaw),0,Math.sin(yaw)),UP,r,0,deploy*Math.PI,.14,.50);
                    }
                    if(!reduced)for(int i=0;i<2;i++){
                        double a=phase+i*Math.PI;var x=circle(at,r*.34,a,.8+i*.45);
                        stamp(g,x,side(x.subtract(at).normalize()),UP,.18,.50,KEHENG_HD_VEIL,.28);
                    }
                    motes(g,at.add(0,.4,0),X,Z,r*.62,time,.38);
                }else if(p.variant()==5){
                    for(int i=0;i<2;i++){
                        var tilt=new Vec3(0,Math.cos(i==0?.32:-.28),Math.sin(i==0?.32:-.28));
                        stamp(g,at.add(0,.14,0),X,tilt,r*.88,r*.88,KEHENG_HD_ARC,.47);
                        slicedArc(g,at.add(0,.12,0),X,tilt,r*.84,phase+i*Math.PI,.9,.11,.30);
                    }
                    if(!reduced)motes(g,at.add(0,.25,0),X,Z,r*.52,time,.36);
                }else if(p.variant()==3){
                    flat(g,at.add(0,.02,0),r*.82,phase,KEHENG_HD_ARC,.52);
                    motes(g,at.add(0,.03,0),X,Z,r*.65,time,.40);
                }else{
                    for(int layer=0;layer<(reduced?2:3);layer++){
                        double turn=phase*(layer%2==0?1:-1)+layer*2.1;
                        flat(g,at.add(0,.28+layer*.44,0),r*(.55+layer*.10),turn,KEHENG_HD_ARC,.44-layer*.04);
                    }
                    motes(g,at.add(0,.14,0),X,Z,r*.66,time,.42);
                }
            }
            case SHIELD -> {
                for(int i=0;i<3;i++){
                    double a=i*Math.PI*2/3+phase;var radial=new Vec3(Math.cos(a),0,Math.sin(a));var tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
                    var center=at.add(radial.scale(.62)).add(0,.85+i*.06,0);
                    stamp(g,center,tangent,UP,.28,.35,KEHENG_HD_SHIELD,.68);
                    stamp(g,center.add(tangent.scale(.20)),tangent,UP,.06,.11,KEHENG_HD_SPARK,.88);
                    if(!reduced)motes(g,center,tangent,UP,.32,time,.25);
                }
            }
            case CHARGE -> {
                double length=at.distanceTo(end);
                for(double sign:new double[]{-1,1}){
                    var offset=s.scale(r*sign);g.line(at.add(offset),end.add(offset),UP,.025,GOLD,.78);
                    strip(g,at.add(offset).add(0,.04,0),end.add(offset).add(0,.04,0),UP,.10,KEHENG_HD_STREAM,.54);
                }
                for(int i=0;i<(reduced?4:7);i++){
                    double t=(i+1-(time*.15)%1)/(reduced?5:8);var x=at.add(d.scale(length*t));
                    stamp(g,x,s,UP,.11,.07,KEHENG_HD_SPARK,.85);
                }
                if(!reduced)strip(g,at.add(0,1.25,0),end.add(0,1.25,0),up,.10,KEHENG_HD_VEIL,.18);
            }
            case BEAM -> {
                if(p.angle()==BASIC){
                    strip(g,at,end,up,.055,KEHENG_HD_STREAM,.82);
                    stamp(g,at.lerp(end,.72),s,up,.09,.09,KEHENG_HD_SPARK,.65);
                    if(!reduced)motes(g,at.lerp(end,.3),s,up,.13,time,.40);break;
                }
                double t=g.progress,brightness=t<.2?1:Math.pow(1-t,1.5);
                MODELS.append(models,reduced?"needle_lod":"needle",end,d,at.distanceTo(end),.72,KEHENG_PROJECTILE_MODEL,(float)brightness);
                strip(g,at,end,up,.16,KEHENG_HD_STREAM,.76*brightness);
                for(double sign:new double[]{-1,1})
                    strip(g,at.add(s.scale(.12*sign)),end.add(s.scale(.12*sign)),s,.12,KEHENG_HD_VEIL,.42*brightness);
                stamp(g,end.subtract(d.scale(.025)),s,up,.25,.25,KEHENG_HD_IMPACT,.85*brightness);
                if(age>=.75)motes(g,end.subtract(d.scale(.08)),s,up,.34,time,.62*brightness);
            }
            case ARC -> {
                double t=g.progress;flat(g,at,r*.94,t*Math.PI*.35,KEHENG_HD_ARC,.66*(1-t));
                for(int i=0;i<(reduced?3:6);i++){
                    double a=t*Math.PI*2+i*Math.PI*2/(reduced?3:6);var x=circle(at,r*.88,a,.35+t*.35);
                    stamp(g,x,side(x.subtract(at).normalize()),UP,.075,.14,KEHENG_HD_SPARK,.9*(1-t));
                }
                if(!reduced)motes(g,at.add(0,.15,0),X,Z,r*.68,time,.40*(1-t));
            }
            case BURST -> {
                if(p.angle()==SHIELD_EXPIRE)break;
                double t=g.progress;
                if(p.angle()==SHIELD_BREAK){
                    for(int i=0;i<3;i++){
                        double a=i*Math.PI*2/3;var tangent=new Vec3(-Math.sin(a),0,Math.cos(a));var center=circle(at,.62,a,.85).add(tangent.scale(t*.38));
                        stamp(g,center,tangent,UP,.23*(1-t*.4),.30,KEHENG_HD_SHIELD,.68*(1-t));
                        motes(g,center,tangent,UP,.34,time,.72*(1-t));
                    }break;
                }
                if(p.angle()==NATURAL_END||p.angle()==INTERRUPTED_END){
                    boolean natural=p.angle()==NATURAL_END;if(p.variant()==7)break;
                    double size=r*(natural?Math.max(.04,1-t*1.6):1+.08*t);
                    flat(g,at,size,phase,KEHENG_HD_ARC,.60*(1-t));
                    if(p.variant()==6)for(int i=0;i<(reduced?3:5);i++){
                        double a=i*Math.PI*2/(reduced?3:5);var x=circle(at,r*Math.sqrt(Math.max(0,1-(1-t)*(1-t))),a,r*(1-t));
                        stamp(g,x,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.12,.24,KEHENG_HD_VEIL,.48*(1-t));
                    }
                    if(natural&&p.variant()==5&&t>.5)stamp(g,at.add(0,.32,0),X,UP,.055,.25,KEHENG_HD_STREAM,.8*(1-t));
                    motes(g,at.add(0,.12,0),X,Z,Math.max(.2,size*.8),time,.75*(1-t));break;
                }
                if(p.variant()==0&&p.angle()==3){
                    stamp(g,at,s,up,.30,.30,KEHENG_HD_IMPACT,.92*(1-t));
                    if(age>=.5)stamp(g,at,s,up,.42,.42,KEHENG_HD_ARC,.55*(1-t));
                    motes(g,at.add(up.scale(t*.1)),s,up,.45,time,.82*(1-t));break;
                }
                contact(g,p,at,d,s,up);
            }
        }
        models.addAll(g.result());return List.copyOf(models);
    }
}
