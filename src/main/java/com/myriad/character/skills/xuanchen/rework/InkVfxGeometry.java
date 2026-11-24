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
    private static final int PAPER=0xFFE5AB, BRASS=0xBA9158, INK=0x343238;
    private InkVfxGeometry(){}
    public static boolean portalFrame(AbilityVfxPayload p){
        return p.theme()==3&&p.kind()==SEAL&&p.variant()==4&&p.angle()==0;
    }
    public static boolean bannerField(AbilityVfxPayload p){
        return p.theme()==3&&p.kind()==SEAL&&!p.personal()&&p.angle()==360&&(p.variant()==5||p.variant()==6);
    }
    public static boolean usesModel(AbilityVfxPayload p){
        return p.theme()==3&&(p.kind()==FLIGHT||portalFrame(p)||bannerField(p)
                ||p.kind()==BEAM&&(p.variant()==0||p.variant()==3));
    }
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload p){
        if(p.theme()!=3||p.kind()!=FLIGHT||p.from().distanceToSqr(p.to())<.006)return null;
        return new AbilityVfxPayload(p.owner(),-1,3,p.variant(),TRAIL,p.from(),p.to(),
                p.variant()==2?.29f:.19f,42,4,p.started(),false);
    }
    private static void paper(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,double scale){
        Vec3 normal=right.cross(up).normalize();
        g.frame(at,right,up,.27*scale,.43*scale,INK_HD_PAPER,(int)g.age/2%3,.99);
        Vec3 edge=at.subtract(right.scale(.134*scale));
        g.line(edge.subtract(up.scale(.32*scale)),edge.add(up.scale(.35*scale)),normal,.016,0xA6804D,.94);
        if(!g.reduced){
            Vec3 glint=at.add(normal.scale(.016)).add(up.scale(.31*scale));
            g.frame(glint,right,up,.30*scale,.104*scale,INK_HD_GILT,(int)(g.age*.37),.68);
        }
    }
    private static void brush(AbilityMeshBuilder g,Vec3 start,Vec3 end,double spread,double strength){
        Vec3 axis=direction(start,end);
        Vec3 center=start.lerp(end,.5),across=side(axis);
        double half=start.distanceTo(end)*.55;
        g.frame(center,axis,across,half,spread,
                INK_HD_BRUSH,(int)g.age/3,strength);
        g.frame(center.add(0,.009,0),axis,across,half*.96,spread*1.35,
                INK_HD_GRAIN,(int)(g.age*.61)+1,strength*.79);
    }
    private static void stamp(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 front,double radius,int phase,double alpha){
        g.frame(at,right,front,radius,radius,INK_HD_STAMP,phase,alpha);
    }
    private static void chain(AbilityMeshBuilder g,Vec3 tail,Vec3 head,Vec3 cross,double span,double alpha){
        if(tail.distanceToSqr(head)<1E-8)return;
        Vec3 axis=direction(tail,head);
        g.frame(tail.lerp(head,.5),axis,cross,tail.distanceTo(head)*.56,span,INK_HD_CHAIN,(int)g.age,alpha);
    }
    private static void pillar(AbilityMeshBuilder g,Vec3 feet,double height,double angle){
        g.rod(feet,feet.add(0,height,0),.055,0xE9CC8B,0x977949,0x3A2D29,.94);
        paper(g,feet.add(0,height*.65,0),new Vec3(Math.cos(angle),0,Math.sin(angle)),UP,.72);
        if(!g.reduced)g.frame(feet.add(0,height*.22,0),X,Z,height*.35,height*.35,
                INK_HD_GILT,(int)(g.age*.5+angle),.68);
    }
    private static void gatePapers(AbilityMeshBuilder g,Vec3 at,Vec3 right,double radius,double openness){
        double half=.72;
        for(int side:new int[]{-1,1}){
            Vec3 hinge=at.add(right.scale(side*half)).add(0,1.04,0);
            Vec3 wing=right.scale(-side*Math.cos(openness)).add(right.cross(UP).scale(Math.sin(openness)));
            g.frame(hinge.add(wing.scale(half*.48)),wing,UP,half*.69,.87,INK_HD_PAPER,(int)g.age/3%3,.90);
            Vec3 edge=hinge.add(wing.scale(half*.77)).add(0,.27,0);
            g.frame(edge,wing,UP,half*.43,.23,INK_HD_GILT,(int)(g.age*.57)+side,.81);
            if(!g.reduced)g.frame(edge.add(right.cross(UP).scale(openness*.16)).add(0,-.39,0),
                    wing,UP,.34,.35,INK_HD_FIBERS,(int)(g.progress*4),.72);
        }
    }
    private static void crossLock(AbilityMeshBuilder g,Vec3 at,Vec3 side,double radius,double progress){
        Vec3 forward=UP.cross(side).normalize();
        double half=radius*(.72-.22*progress);
        for(int sign:new int[]{-1,1}){
            Vec3 axis=side.add(forward.scale(sign)).normalize();
            double height=sign<0?.06:.095;
            Vec3 a=at.subtract(axis.scale(half)).add(0,height,0);
            Vec3 b=at.add(axis.scale(half)).add(0,height,0);
            brush(g,a,b,.23,.98);
            g.frame(a.lerp(b,.5).add(0,.018,0),axis,UP,half,.38,INK_BOUNDARY_WEAVE,(int)g.age/2,.92);
            g.line(a.add(0,.012,0),a.lerp(b,.22).add(0,.012,0),UP,.027,PAPER,.85);
        }
        g.frame(at.add(0,.12,0),X,Z,radius*.69,radius*.69,INK_HD_GILT,(int)(g.progress*4),.64);
    }
    public static List<AbilityVfxGeometry.Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced,AbilityVfxGeometry.maxQuads(p));
        Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();
        double r=Math.max(.08,p.radius());
        switch(p.kind()){
            case SEAL -> {
                boolean warning=p.angle()==359;
                if(p.variant()==1&&p.anchor()>=0){
                    stamp(g,at.add(0,.066,0),s,d,r*1.65,0,.98);
                    if(!reduced)g.frame(at.subtract(s.scale(r*.45)).add(0,.083,0),s,d,r*.78,r*.58,
                            INK_HD_GRAIN,(int)(g.age+p.started())/2,.66);
                    break;
                }
                if(p.variant()==4&&!warning&&p.angle()==0){
                    Vec3 normal=direction(Vec3.ZERO,d.multiply(1,0,1));
                    InkModels.gate(g,at,normal);
                    int phase=(int)((p.started()+g.age)/4)%4;
                    g.frame(at.add(0,1.06,0),s,UP,.77,1.03,INK_GATE_VEIL,phase,.88);
                    g.frame(at.add(0,.055,0),s,normal,1.04,.64,INK_HD_GILT,phase,.85);
                    break;
                }
                if(!p.personal()&&(p.variant()==6||p.variant()==7)){
                    InkUltimateLayers.field(g,p,r,warning);
                    break;
                }
                if(p.variant()>=5){
                    double opening=warning?.28+.65*g.progress:p.variant()==7?.60+.4*(1-g.progress):1;
                    border(g,at,r,opening,warning?.9:.96);
                    if(warning||p.personal())break;
                }else{
                    stamp(g,at.add(0,.015,0),s,d,r,warning?0:1,.8);
                    if(warning||p.personal())break;
                }
                if(p.variant()==5){
                    if(!bannerField(p))break;
                    for(int i=0;i<4;i++){
                        double a=Math.PI*(.25+i*.5);
                        double rise=Math.clamp((age-i*1.55)/6,0,1);
                        if(rise<=0)continue;
                        Vec3 normal=new Vec3(Math.cos(a),0,Math.sin(a));
                        Vec3 foot=circle(at,r*.88,a,.025);
                        InkModels.banner(g,foot,normal,false,rise);
                        if(!reduced)g.frame(foot.add(0,.031,0),side(normal),normal,.53,.39,
                                INK_HD_GILT,(int)(g.age/3)+i,.62*rise);
                    }
                    break;
                }
                if(p.variant()<=3){
                    paper(g,at.add(d.scale(.2)).add(0,.32,0),s,UP,.52);
                }
                if(p.variant()==7){
                    for(int i=0;i<4;i++){
                        double a=Math.PI*.5*i+.25*Math.PI;
                        stamp(g,circle(at,r*.85,a,.022),X,Z,.37,3,.76);
                    }
                }
            }
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                boolean bind=p.variant()==2;
                Vec3 tail=at.subtract(d.scale(bind?1.03:.64)),joint=head.subtract(d.scale(bind?.56:.20));
                if(bind)InkModels.dart(g,head,d);else InkModels.slip(g,head,d,.56,(p.started()+g.age)*.09);
                int turn=(int)(p.started()%48+g.age);
                g.frame(tail.lerp(joint,.52).subtract(v.scale(.026)),d,s,tail.distanceTo(joint)*.54,.33,
                        INK_HD_GRAIN,turn/2,.77);
                if(bind)chain(g,tail,joint,s,.35,.94);
                if(!reduced){
                    if(bind)chain(g,tail.add(0,.012,0),joint.add(0,.012,0),v,.29,.68);
                    g.frame(head.subtract(d.scale(r*1.7)),d,v,.54,.29,INK_HD_GILT,turn,.76);
                    g.frame(tail.lerp(joint,.31),d,v,.61,.26,INK_HD_FIBERS,turn/2+2,.70);
                }
            }
            case BEAM,TRAIL -> {
                if(at.distanceToSqr(end)<1E-8)break;
                if(p.kind()==TRAIL&&p.angle()==42){
                    Vec3 mid=at.lerp(end,.5);
                    double length=at.distanceTo(end)*.58+.07;
                    int phase=(int)(p.started()+g.age);
                    g.frame(mid,d,s,length,r,INK_HD_BRUSH,phase,.84);
                    g.frame(mid.add(v.scale(.015)),d,v,length,r*1.45,INK_HD_FIBERS,phase,.74);
                    if(!reduced)g.frame(mid.add(v.scale(.027)),d,s,length,r*.72,INK_HD_GILT,phase,.74);
                    break;
                }
                if(p.kind()==BEAM&&(p.variant()==0||p.variant()==3)){
                    double t=Math.clamp(age/Math.max(1,p.life()-1),0,1);
                    Vec3 slip=at.lerp(end,t).add(0,.42*Math.sin(Math.PI*t),0);
                    Vec3 tangent=direction(Vec3.ZERO,end.subtract(at).add(0,.42*Math.PI*Math.cos(Math.PI*t),0));
                    InkModels.slip(g,slip,tangent,p.variant()==0?.72:1,t);
                    Vec3 trail=at.lerp(end,Math.max(0,t-.29));
                    double length=trail.distanceTo(slip);
                    if(length>.04){
                        g.frame(trail.lerp(slip,.5),direction(trail,slip),s,length*.58,.21,
                                INK_HD_GRAIN,(int)g.age,.71);
                        g.frame(slip.subtract(d.scale(length*.25)),d,v,.32,.20,INK_HD_FIBERS,(int)(t*3.99),.77);
                    }
                    break;
                }
                g.rod(at,end,Math.clamp(r*.3,.035,.075),0xC9B58A,0x4F5962,0x202631,.94);
                chain(g,at,end,v,.24,.85);
                int n=reduced?2:4;for(int i=0;i<n;i++)paper(g,at.lerp(end,(i+.5)/n),s,v,.38);
            }
            case SHIELD -> {
                int n=p.variant()==0?1:2;
                double rise=p.angle()==360?Math.clamp((age-2)/3,0,1):1;
                if(rise<=0)break;
                for(int i=0;i<n;i++){
                    double a=p.variant()==0?Math.atan2(-d.z,-d.x):Math.atan2(d.z,d.x)+(i==0?-.78:.78);
                    Vec3 right=new Vec3(-Math.sin(a),0,Math.cos(a)),feet=circle(at,r,a,.18);
                    paper(g,feet.add(0,.66*rise,0),right,UP,(p.variant()==0?.75:1.03)*rise);
                    g.rod(feet,feet.add(0,1.2*rise,0),.035,0xE7CA90,0x9C7C4A,0x322A25,.9);
                    g.frame(feet.add(0,.16*rise,0),right,UP,.30*rise,.14*rise,INK_HD_GRAIN,
                            (int)(g.age*.38)+i,.80);
                    if(!reduced)g.frame(feet.add(0,.78*rise,0).add(right.scale(.21)),right,UP,.32,.38,
                            INK_HD_FIBERS,(int)(g.age*.44)+i,.53);
                }
            }
            case BURST -> {
                if(p.variant()==4&&(p.angle()==90||p.angle()==180)){
                    double open=p.angle()==90?(1-g.progress)*-1.2:g.progress*1.2;
                    gatePapers(g,at,s,r,open);
                    break;
                }
                if(p.angle()==270&&p.anchor()>=0){
                    Vec3 point=at.add(0,.77-g.progress*.38,0).add(d.scale(.53+g.progress*.44));
                    Vec3 curl=UP.scale(1-g.progress*.38).add(d.scale(g.progress*.83)).normalize();
                    g.frame(point,s,curl,.29,.44,INK_HD_PAPER,3,.96);
                    g.frame(point.add(s.scale(.22)),s,UP,.36,.29,INK_HD_FIBERS,(int)(g.progress*4),.91);
                    g.frame(point.add(0,.1,0),s,curl,.42,.36,INK_HD_GILT,(int)(g.age*.76),.78);
                    break;
                }
                if(p.variant()==6){
                    double a=Math.toRadians(p.angle());
                    Vec3 axis=new Vec3(Math.cos(a),0,Math.sin(a));
                    Vec3 across=side(axis);
                    Vec3 center=at.add(axis.scale((g.progress-.5)*r*1.5)).add(0,.055,0);
                    if(age<3)g.frame(at.add(0,.076,0),across,axis,r*.85,.35,INK_HD_GILT,0,(1-age/3)*.98);
                    g.frame(center,across,axis,r*.74,.78,INK_HD_BRUSH,(int)(g.progress*4),.99);
                    g.frame(center.subtract(axis.scale(.26)).add(0,.021,0),across,axis,r*.72,.89,
                            INK_HD_GRAIN,(int)(g.age*.62)+2,.94);
                    g.frame(center.add(0,.10+g.progress*.16,0),across,UP,r*.73,.39,
                            INK_HD_FIBERS,(int)(g.progress*4),.88);
                    g.frame(center.add(0,.29,0),across,UP,r*.73,.77,INK_ARRAY_WAVE,(int)(g.age*.65),.99);
                    if(!reduced)g.frame(center.add(0,.12,0),across,axis,r*.74,.53,
                            INK_HD_GILT,(int)(g.age*.78),.72);
                    break;
                }
                if(p.variant()==1){
                    int phase=g.progress<.24?1:g.progress<.70?2:3;
                    stamp(g,at.add(0,.058,0),s,d,r,phase,.99);
                    g.frame(at.add(0,.074,0),s,d,r*.94,r*.85,INK_HD_GILT,(int)(g.age*.52),
                            phase==1?.72:.46);
                    if(phase>=2)g.frame(at.add(0,.14+g.progress*.29,0),s,UP,r*.7,r*.38,
                            INK_HD_FIBERS,(int)(g.progress*4),.91);
                    break;
                }
                if(p.variant()==7){
                    InkUltimateLayers.close(g,at,d,r);
                    break;
                }
                if(p.angle()==1||p.variant()==0&&p.angle()==41){
                    g.frame(at,s,v,.42,.42,INK_HD_SCRAPS,(int)(g.progress*4),.94);
                    if(p.angle()==41)g.frame(at.add(d.scale(.026)),s,v,.48,.44,INK_HD_GILT,(int)g.age,.89);
                    break;
                }
                stamp(g,at.add(0,.021,0),s,d,Math.min(r,1.5),3,.77);
                for(int i=0;i<(reduced?3:5);i++){
                    double a=i*2.399963;
                    Vec3 point=circle(at,r*(.2+.4*g.progress),a,.22+g.progress*.5);
                    paper(g,point,new Vec3(Math.cos(a),.25,Math.sin(a)).normalize(),UP,.3);
                }
            }
            case ARC -> {
                double yaw=Math.atan2(d.z,d.x),sweep=Math.toRadians(p.angle());
                g.bladeArc(at,r,yaw-sweep*.5,sweep,Math.min(.28,r*.2),.12,0xD6C291,0x37414D);
                g.frame(at.add(d.scale(r*.45)),s,v,r*.48,r*.45,INK_HD_CHAIN,(int)g.age/2,.88);
            }
            case CHARGE -> {
                stamp(g,at,s,d,.75,0,.91);
                for(int i=0;i<(reduced?3:4);i++){
                    double a=i*Math.PI/2+age*.04;
                    paper(g,circle(at,.65,a,.75),new Vec3(Math.cos(a),0,Math.sin(a)),UP,.6);
                }
            }
            case PILLAR -> {
                if(p.anchor()>=0){
                    crossLock(g,at,s,r,g.progress);
                    paper(g,at.add(s.scale(r*.85)).add(0,.23,0),s,UP,.54);
                    paper(g,at.subtract(s.scale(r*.85)).add(0,.23,0),s,UP,.54);
                }else{
                    pillar(g,at,Math.min(5,at.distanceTo(end)),0);
                    stamp(g,at,s,d,r,1,.88);
                }
            }
            case WALL -> {for(int i=0;i<3;i++)pillar(g,at.add(s.scale(r*(i-1))),1.8,i);}
        }
        return g.result();
    }

    private static void border(AbilityMeshBuilder g,Vec3 at,double radius,double reveal,double alpha){
        for(int i=0;i<4;i++){
            Vec3 corner=circle(at,radius,Math.PI*.25+i*Math.PI*.5,.02);
            Vec3 next=circle(at,radius,Math.PI*.25+(i+1)*Math.PI*.5,.02);
            double painted=Math.clamp(reveal+i*.075,.2,1);
            Vec3 start=corner.lerp(next,.035),end=corner.lerp(next,.04+.91*painted);
            brush(g,start,end,.26,alpha);
            g.line(corner,corner.lerp(next,.077),UP,.038,PAPER,.82);
        }
    }
}
