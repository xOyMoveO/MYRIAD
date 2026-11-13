package com.myriad.character.skills.keheng;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.*;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Chronometric planes built in world space; refresh TTL and animation time are independent. */
public final class KehengVfx {
    public static final int THEME=11,TEAL=0x42D9BD,GOLD=0xF1C76B,WHITE=0xF2F6EA,ECHO=0x8C7CA6;
    public static final int WARNING=300,NATURAL_END=200,INTERRUPTED_END=201,SHIELD_BREAK=202,SHIELD_EXPIRE=203,BASIC=210;
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
    public static AABB bounds(AbilityVfxPayload p){return new AABB(p.from(),p.to()).inflate(p.radius()+.5).expandTowards(0,p.variant()==7?3:p.variant()==0?1.3:0,0);}
    private static double clock(AbilityVfxPayload p,float age){return (double)p.started()+age;}
    private static void boundary(AbilityMeshBuilder g,Vec3 at,double radius,double turn,int count,boolean warning,double connect){
        for(int i=0;i<count;i++){
            double a=i*2*Math.PI/count;
            if(connect>0)g.line(circle(at,radius,a+turn,0),circle(at,radius,a+turn+2*Math.PI/count*.62*connect,0),UP,.065,warning?GOLD:TEAL,warning?.86:.70);
            if(i%(count/4)==0||!warning)g.line(circle(at,radius-.13,a,0),circle(at,radius,a,0),UP,.028,GOLD,.82);
        }
    }
    private static void openArc(AbilityMeshBuilder g,Vec3 center,Vec3 right,Vec3 up,double radius,double start,double span,double width,int color,double alpha,int steps){
        for(int i=0;i<steps;i++){
            double a=start+span*i/steps,b=start+span*(i+.86)/steps;
            var x=center.add(right.scale(radius*Math.cos(a))).add(up.scale(radius*Math.sin(a)));
            var y=center.add(right.scale(radius*Math.cos(b))).add(up.scale(radius*Math.sin(b)));
            g.line(x,y,right.cross(up),width,color,alpha);
        }
    }
    private static void needleStar(AbilityMeshBuilder g,Vec3 at,Vec3 forward,double size,int color,double alpha){
        Vec3 side=side(forward),up=side.cross(forward).normalize();
        int i=0;for(Vec3 axis:new Vec3[]{forward,side,up}){
            double length=size*(1-i++*.24);g.line(at.subtract(axis.scale(length*.45)),at.add(axis.scale(length)),side(axis),.018,color,alpha);
        }
    }
    private static void impact(AbilityMeshBuilder g,AbilityVfxPayload p,Vec3 at,Vec3 d,Vec3 side,Vec3 up){
        double t=g.progress;
        needleStar(g,at,d,(p.angle()==BASIC?.28:.44)*(1-t*.75),WHITE,1-t*.65);
        if(p.angle()!=BASIC)openArc(g,at,side,up,.18+.26*t,-.6,Math.PI*1.35,.02,TEAL,.65*(1-t),g.reduced?4:7);
        for(int i=0;i<(g.reduced?2:3);i++){
            double a=i*2.399;var x=at.add(side.scale(Math.cos(a)*(.08+.24*t))).add(up.scale(Math.sin(a)*(.08+.24*t)));
            g.line(x,x.add(up.scale(.04)).add(side.scale(.07)),d,.02,GOLD,.85*(1-t));
        }
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced);var at=p.from();var end=p.to();var d=direction(at,end);var s=side(d);var up=s.cross(d).normalize();
        double radius=p.radius(),time=clock(p,age),phase=time*.035;
        switch(p.kind()){
            case FLIGHT -> {
                var head=at.lerp(end,Math.clamp(age,0,1));
                // The forward tip is a real tapered solid; tails remain thinner and dimmer.
                g.rod(head.subtract(d.scale(.42)),head,.018,WHITE,TEAL,0x1C2C35,.96);
                g.triangle(head.add(d.scale(.20)),head.subtract(s.scale(.035)),head.add(s.scale(.035)),WHITE,.98);
                g.triangle(head.add(d.scale(.20)),head.subtract(up.scale(.035)),head.add(up.scale(.035)),WHITE,.98);
                for(double sign:new double[]{-1,1}){
                    var tail=at.subtract(d.scale(.34)).add(s.scale(.055*sign));
                    g.line(tail,head.subtract(d.scale(.08)).add(s.scale(.022*sign)),up,.018,TEAL,.60);
                }
                double flicker=.8+.2*Math.sin(time*Math.PI/4);
                g.line(at.subtract(d.scale(.25)).add(up.scale(.07)),head.subtract(d.scale(.24)).add(up.scale(.07)),s,.016,ECHO,.35*flicker);
                needleStar(g,head.add(d.scale(.04)),d,.075,GOLD,.9);
            }
            case TRAIL -> {
                if(at.distanceToSqr(end)<1E-6)break;
                double fade=1-g.progress;
                if(p.variant()==2){
                    for(double sign:new double[]{-1,1})g.line(at.add(s.scale(.045*sign)),end.add(s.scale(.02*sign)),up,.012,TEAL,.30*fade);
                    if(!reduced)g.line(at.add(up.scale(.06)),end.add(up.scale(.03)),s,.012,ECHO,.19*fade);
                    break;
                }
                g.line(at,end,UP,.10,TEAL,.33*fade);
                for(int i=0;i<(reduced?2:3);i++){
                    var a=at.lerp(end,i/4.0).add(up.scale(.12+i*.18));var b=at.lerp(end,(i+2)/4.0).add(up.scale(.21+i*.18));
                    g.line(a,b,s,.045,ECHO,.26*fade);
                    var tick=at.lerp(end,(i+.5)/3.0).add(0,-.25,0);
                    g.line(tick.subtract(s.scale(.10)),tick.add(s.scale(.10)),UP,.024,GOLD,.75*fade);
                }
            }
            case SEAL -> {
                if(p.variant()==0){
                    for(int i=0;i<(int)p.angle();i++){
                        var tick=at.add(.36,.8+i*.13,0);g.line(tick,tick.add(.10,.025,0),Z,.025,GOLD,.76+.12*Math.sin(time*.2+i));
                    }break;
                }
                if(p.angle()>=WARNING){
                    double elapsed=p.angle()-WARNING+age;
                    boundary(g,at,radius,0,reduced?8:12,true,Math.clamp((elapsed-3)/6,0,1));
                    for(int i=0;i<4;i++){
                        var node=circle(at,radius,i*Math.PI/2,.025);
                        g.rod(node,node.add(0,.24,0),.038,GOLD,GOLD,TEAL,.93);
                        g.quad(node.add(.075,0,0),node.add(0,0,.075),node.add(-.075,0,0),node.add(0,0,-.075),GOLD,.9,0);
                    }break;
                }
                boundary(g,at,radius,0,reduced?8:12,false,1);
                double deploy=Math.clamp((p.angle()+age+1)/6,0,1);
                if(p.variant()==6){
                    for(int k=0;k<(reduced?3:5);k++){
                        double yaw=k*Math.PI/(reduced?3:5),height=deploy*Math.PI;
                        Vec3 horizontal=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
                        openArc(g,at,horizontal,UP,radius,0,height,.055,TEAL,.20,reduced?5:9);
                    }
                    for(int i=0;i<(reduced?2:4);i++){
                        double a=phase+i*2.4;var x=circle(at,radius*.46,a,.5+i*.35);
                        g.line(x,x.add(.16,.10,.08),d,.035,ECHO,.2);
                    }
                }else if(p.variant()==5){
                    for(int i=0;i<2;i++){
                        double tilt=i==0?.32:-.28;Vec3 tiltedUp=new Vec3(0,Math.cos(tilt),Math.sin(tilt));
                        openArc(g,at.add(0,.10,0),X,tiltedUp,radius*.88,.15+i*Math.PI+phase,Math.PI*.8,.075,TEAL,.24,reduced?6:10);
                        openArc(g,at.add(.08,.10,-.09),X,tiltedUp,radius*.88,.15+i*Math.PI+phase-.10,Math.PI*.8,.030,ECHO,.17,reduced?4:7);
                    }
                    double a=time*Math.PI/10;var tick=circle(at,radius*.86,a,.15);
                    g.line(tick,tick.add(0,.13,0),side(tick.subtract(at)),.025,GOLD,.8);
                }else if(p.variant()!=3){
                    for(int layer=0;layer<3;layer++){
                        double yaw=phase*(layer%2==0?1:-1)+layer*2.1,y=.26+layer*.46;
                        openArc(g,at.add(0,y,0),X,Z,radius*(.55+layer*.10),yaw,Math.PI*.65,.075,TEAL,.21,reduced?4:7);
                        var shard=circle(at,radius*.42,yaw+.5,y+.12);g.line(shard,shard.add(.08,.06,.04),UP,.023,ECHO,.22);
                    }
                }
            }
            case SHIELD -> {
                for(int i=0;i<3;i++){
                    double a=i*Math.PI*2/3+phase;var center=at.add(0,.85+i*.09,0);
                    openArc(g,center,X,Z,.62,a,.62,.12,TEAL,.26,reduced?3:5);
                    var tick=circle(center,.62,a,0);g.line(tick,tick.add(0,.13,0),side(tick.subtract(center)),.025,GOLD,.86);
                }
            }
            case CHARGE -> {
                double length=at.distanceTo(end);
                for(double sign:new double[]{-1,1}){
                    var offset=s.scale(radius*sign);g.line(at.add(offset),end.add(offset),UP,.022,GOLD,.65);
                    // Three metre corridor is only an open outline, never an opaque wall.
                    g.line(at.add(offset).add(0,3,0),end.add(offset).add(0,3,0),s,.012,TEAL,.13);
                }
                for(int i=0;i<(reduced?5:9);i++){
                    double t=(i+1-(time*.15)%1)/(reduced?6:10);
                    var tick=at.add(d.scale(length*t));g.line(tick.subtract(s.scale(.13)),tick.add(s.scale(.13)),UP,.023,GOLD,.85);
                }
                needleStar(g,at.add(0,1.25,0),d,.10+.05*Math.sin(time*.4),TEAL,.55);
            }
            case BEAM -> {
                if(p.angle()==BASIC){g.rod(at,end,.012,WHITE,TEAL,0x1C2C35,.76);break;}
                double t=g.progress,extent=at.distanceTo(end),brightness=t<.2?1:Math.pow(1-t,1.5);
                g.rod(at,end,.014+.012*(1-t),WHITE,TEAL,0x1C2C35,brightness);
                for(double sign:new double[]{-1,1}){
                    var a=at.add(s.scale(.08*sign));var b=end.subtract(d.scale(extent*.07)).add(s.scale(.28*(1-t)*sign));
                    g.line(a,b,up,.075,TEAL,.45*brightness);
                    g.line(a.add(up.scale(.07)),b.add(up.scale(.07)),s,.028,ECHO,.28*brightness);
                }
                needleStar(g,end,d,.23*(1-t),WHITE,brightness);
            }
            case ARC -> {
                // This is emitted once by each real ten-tick pulse, including pulse six at expiry.
                double sweep=g.progress*Math.PI*2;
                for(int i=0;i<(reduced?3:6);i++){
                    double a=sweep+i*Math.PI*2/(reduced?3:6);
                    var tick=circle(at,radius*.88,a,.35+g.progress*.35);
                    g.line(tick,tick.add(0,.14,0),side(tick.subtract(at)),.023,WHITE,.70*(1-g.progress));
                }
            }
            case BURST -> {
                if(p.angle()==SHIELD_EXPIRE)break;
                if(p.angle()==SHIELD_BREAK){
                    for(int i=0;i<3;i++){
                        double a=i*Math.PI*2/3;var center=circle(at,.62,a,.85).add(new Vec3(-Math.sin(a),0,Math.cos(a)).scale(g.progress*.38));
                        g.line(center,center.add(-Math.sin(a)*.18,.06,Math.cos(a)*.18),UP,.065,TEAL,.55*(1-g.progress));
                        g.line(center,center.add(0,.11,0),X,.022,GOLD,.8*(1-g.progress));
                    }break;
                }
                if(p.angle()==NATURAL_END||p.angle()==INTERRUPTED_END){
                    boolean natural=p.angle()==NATURAL_END;double t=g.progress;
                    if(p.variant()==7)break; // The corridor's actual BEAM owns its narrow release.
                    if(p.variant()==5&&natural){
                        double r=radius*Math.max(.04,1-t*1.6);
                        openArc(g,at.add(0,.1,0),X,UP,r,phase,Math.PI*1.3,.04,TEAL,.34*(1-t),reduced?7:12);
                        if(t>.5)needleStar(g,at.add(0,.4,0),UP,.2+.3*(t-.5),WHITE,.65*(1-t));
                    }else if(p.variant()==6){
                        for(int i=0;i<(reduced?4:8);i++){
                            double a=i*Math.PI*2/(reduced?4:8);var x=circle(at,radius*Math.sqrt(Math.max(0,1-(1-t)*(1-t))),a,radius*(1-t));
                            g.line(x,x.add(Math.cos(a)*.10,-.18,Math.sin(a)*.10),side(x.subtract(at)),.035,natural?TEAL:ECHO,.34*(1-t));
                        }
                    }else {
                        boundary(g,at,radius*(natural?1-.28*t:1+.12*t),phase,reduced?8:12,false,1-t);
                        if(!natural)for(int i=0;i<3;i++){
                            double a=i*Math.PI*2/3;var x=circle(at,radius*.72,a,.22).add(-Math.sin(a)*t*.3,0,Math.cos(a)*t*.3);
                            g.line(x,x.add(-Math.sin(a)*.22,.10,Math.cos(a)*.22),UP,.045,ECHO,.38*(1-t));
                        }
                    }
                    break;
                }
                if(p.variant()==0&&p.angle()==3){
                    for(int i=0;i<3;i++){
                        double a=i*Math.PI*2/3;var edge=at.add(s.scale(Math.cos(a)*.35*(1-g.progress))).add(up.scale(Math.sin(a)*.35*(1-g.progress)));
                        g.line(edge,at,d,.022,WHITE,.94*(1-g.progress));
                    }
                    double shard=.10+.18*g.progress;
                    g.quad(at.add(up.scale(shard)),at.add(s.scale(shard*.65)),at.subtract(up.scale(shard)),at.subtract(s.scale(shard*.65)),TEAL,.42*(1-g.progress),0);
                    break;
                }
                impact(g,p,at,d,s,up);
            }
        }
        return g.result();
    }
}
