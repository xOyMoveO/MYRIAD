package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
/** Original chronometric thin planes. Every ribbon is world-oriented, no solid clock-face overlay. */
public final class KehengVfx {
    public static final int TEAL=0x42D9BD,GOLD=0xF1C76B,WHITE=0xF2F6EA,ECHO=0x8C7CA6;
    private KehengVfx(){}
    private static void segments(AbilityMeshBuilder g,Vec3 at,double r,double rotation,int n,boolean warning){
        for(int i=0;i<n;i++){
            double a=rotation+i*2*Math.PI/n,b=a+2*Math.PI/n*.65;
            g.line(circle(at,r,a,0),circle(at,r,b,0),UP,warning?.035:.045,warning?GOLD:TEAL,warning?.85:.55);
            g.line(circle(at,r-.12,a,0),circle(at,r,a,0),UP,.025,GOLD,.85);
        }
    }
    public static List<AbilityVfxGeometry.Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced);var at=p.from();var end=p.to();var d=direction(at,end);var s=side(d);var up=s.cross(d).normalize();double r=p.radius();
        switch(p.kind()){
            case FLIGHT -> {
                var head=at.lerp(end,Math.clamp(age,0,1));
                g.rod(head.subtract(d.scale(.62)),head.add(d.scale(.18)),.022,WHITE,TEAL,0x1C2C35,.96);
                for(double sign:new double[]{-1,1})g.line(at.add(s.scale(.055*sign)),head.add(s.scale(.025*sign)),up,.025,TEAL,.65);
                g.line(at.add(up.scale(.07)),head.subtract(d.scale(.2)),s,.018,ECHO,.4);
                g.line(head.subtract(s.scale(.065)),head.add(s.scale(.065)),up,.018,GOLD,.9);
            }
            case TRAIL -> {
                g.line(at,end,UP,.16,TEAL,.35);g.line(at.add(0,.1,0),end.add(0,.1,0),s,.05,ECHO,.4);
                for(int i=0;i<3;i++){var t=at.lerp(end,i/3.0);g.line(t.subtract(s.scale(.12)),t.add(s.scale(.12)),UP,.025,GOLD,.7);}
            }
            case SEAL -> {
                if(p.variant()==0){
                    // Chest-side ticks are refreshed only while the real fixed-window mark exists.
                    for(int i=0;i<(int)p.angle();i++){
                        var t=at.add(.35,.8+i*.13,0);g.line(t,t.add(.12,.035,0),Z,.035,GOLD,.85);
                    }break;
                }
                boolean warning=p.angle()==359;segments(g,at,r,warning?0:age*.008,reduced?8:12,warning);
                if(warning){for(int i=0;i<4;i++){var t=circle(at,r,i*Math.PI*.5,.015);g.line(t,t.add(0,.22,0),X,.04,GOLD,.9);}break;}
                if(p.variant()==6){
                    // Four sparse meridians describe the actual three-dimensional dome, leaving targets readable.
                    for(int k=0;k<(reduced?3:4);k++){
                        double yaw=k*Math.PI/(reduced?3:4);
                        for(int i=0;i<8;i++){
                            double a=i*Math.PI/8,b=(i+.78)*Math.PI/8;
                            var x=at.add(Math.cos(a)*r*Math.cos(yaw),Math.sin(a)*r,Math.cos(a)*r*Math.sin(yaw));
                            var y=at.add(Math.cos(b)*r*Math.cos(yaw),Math.sin(b)*r,Math.cos(b)*r*Math.sin(yaw));
                            g.line(x,y,new Vec3(-Math.sin(yaw),0,Math.cos(yaw)),.065,TEAL,.24);
                        }
                    }
                }else if(p.variant()!=3){
                    for(int i=0;i<3;i++){
                        double y=.28+i*.55,theta=i*2.1+age*(p.variant()==5?.03:.012);
                        Vec3 a=circle(at,r*.65,theta,y),b=circle(at,r*.65,theta+.8,y+.18);
                        g.line(a,b,UP,.12,TEAL,.20);g.line(a.add(0,.09,0),b.add(0,.09,0),UP,.03,ECHO,.3);
                    }
                }
            }
            case SHIELD -> {
                for(int i=0;i<3;i++){
                    double a=i*Math.PI*2/3+age*.018;Vec3 x=circle(at,.62,a,.65),y=circle(at,.62,a+.6,.8);
                    g.line(x,y,UP,.23,TEAL,.3);g.line(x,y,UP,.035,GOLD,.85);
                }
            }
            case CHARGE -> {
                var side=side(d);double len=at.distanceTo(end);
                for(double sign:new double[]{-1,1}){
                    var offset=side.scale(r*sign);g.line(at.add(offset),end.add(offset),UP,.035,GOLD,.8);
                    g.line(at.add(offset).add(0,3,0),end.add(offset).add(0,3,0),side,.018,TEAL,.25);
                }
                for(int i=0;i<6;i++){
                    var t=at.add(d.scale(len*((i+1-(age*.14)%1)/7)));
                    g.line(t.subtract(side.scale(.2)),t.add(side.scale(.2)),UP,.03,GOLD,.95);
                }
            }
            case BEAM -> {
                g.rod(at,end,.025,WHITE,TEAL,0x1C2C35,1);
                for(double sign:new double[]{-1,1}){
                    Vec3 a=at.add(s.scale(.1*sign)),b=end.add(s.scale(.45*(1-g.progress)*sign));
                    g.line(a,b,up,.11,TEAL,.4);g.line(a.add(up.scale(.08)),b.add(up.scale(.08)),s,.04,ECHO,.35);
                }
            }
            case ARC -> {segments(g,at,r*(.78+.22*g.progress),age*.1,12,false);
                for(int i=0;i<6;i++){Vec3 x=circle(at,r*.8,i*Math.PI/3,.3+g.progress);g.line(x,x.add(0,.14,0),X,.025,WHITE,.75);}}
            case BURST -> {
                if(p.angle()==1){segments(g,at,r*(1-.2*g.progress),0,8,false);break;}
                for(int i=0;i<3;i++){
                    var axis=i==0?d:i==1?s:up;double size=(.24+i*.08)*(1-g.progress);
                    g.line(at.subtract(axis.scale(size*.4)),at.add(axis.scale(size)),side(axis),.025,WHITE,1);
                }
                for(int i=0;i<3;i++){
                    double a=i*2*Math.PI/3;var x=at.add(s.scale(Math.cos(a)*r*.4)).add(up.scale(Math.sin(a)*r*.4));
                    g.line(x,x.add(s.scale(.12)).add(up.scale(.06)),d,.04,p.angle()==3?TEAL:GOLD,.8);
                }
            }
        }
        return g.result();
    }
}
