package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Solid flight body and layered, directional celadon sprite animation. */
public final class WindVfxGeometry {
    private WindVfxGeometry(){}
    private static void rotor(AbilityMeshBuilder g,Vec3 at,double radius,double spin){
        g.bladeArc(at,radius,spin,Math.PI*.8,radius*.3,.14,0xECFFF6,0x387F72);
        g.bladeArc(at.add(0,.015,0),radius,spin+Math.PI,Math.PI*.8,radius*.25,.14,0xA3D6C3,0x245B56);
    }
    private static void feather(AbilityMeshBuilder g,Vec3 at,Vec3 forward,Vec3 across,double length,double width){
        g.frame(at,forward,across,length,width*1.8,WIND_HD_FEATHER,(int)g.age/2,.98);
        Vec3 airy=at.subtract(forward.scale(length*.31)).add(forward.cross(across).normalize().scale(.018));
        g.frame(airy,forward,across,length*1.15,width*1.97,
                WIND_HD_FILAMENTS,(int)(g.age*.62)+1,.82);
    }
    private static void cut(AbilityMeshBuilder g,Vec3 at,Vec3 d,Vec3 across,double length,double width,double phase){
        Vec3 normal=d.cross(across).normalize();
        int frame=(int)(phase*3.99);
        g.frame(at,d,across,length,width,WIND_HD_CUT,frame,.99);
        g.frame(at.add(normal.scale(.025)).add(d.scale(length*.07)),d,across,length*1.07,width*1.03,
                WIND_HD_SWEEP_EDGE,(frame+1)%4,.90);
        if(!g.reduced){
            g.frame(at.subtract(normal.scale(.085)).subtract(d.scale(length*.21)),d,across,length*1.03,width*1.17,
                    WIND_HD_VAPOR,(int)(g.age*.73)+3,.75);
            if(phase>.18)g.frame(at.add(normal.scale(.09+phase*.08)).subtract(d.scale(length*.33)),d,across,
                    length*.85,width*1.07,WIND_HD_SHARDS,(int)(g.age*.88),.73);
        }
    }
    private static void ribbon(AbilityMeshBuilder g,Vec3 from,Vec3 to,Vec3 across,double width,int material,int frame,double alpha){
        if(from.distanceToSqr(to)<1E-8)return;
        g.frame(from.lerp(to,.5),direction(from,to),across,from.distanceTo(to)*.55,width,
                material,frame,alpha);
    }
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload p){
        if(p.theme()!=4||p.kind()!=FLIGHT||p.variant()!=5||p.from().distanceToSqr(p.to())<.016)return null;
        boolean returning=p.angle()==180;
        return new AbilityVfxPayload(p.owner(),-1,4,p.variant(),TRAIL,p.from(),p.to(),
                returning?.14f:.23f,returning?43:42,4,p.started(),false);
    }
    private static void foldback(AbilityMeshBuilder g,Vec3 at,Vec3 d,Vec3 s,double r){
        double reach=r*.63;
        Vec3 outward=at.subtract(s.scale(r*.38)),inward=at.add(s.scale(r*.38));
        ribbon(g,outward.subtract(d.scale(r*1.65)),outward,s,.25,WIND_HD_FLOW,(int)g.age,.94);
        ribbon(g,inward,inward.subtract(d.scale(r*1.65)),s,.14,WIND_HD_FLOW,(int)g.age,.94);
        int segments=g.reduced?5:9;
        for(int i=0;i<segments;i++){
            double a=Math.PI*i/segments,b=Math.PI*(i+1)/segments;
            Vec3 start=at.subtract(s.scale(Math.cos(a)*r*.38)).add(d.scale(Math.sin(a)*reach));
            Vec3 end=at.subtract(s.scale(Math.cos(b)*r*.38)).add(d.scale(Math.sin(b)*reach));
            Vec3 center=start.lerp(end,.5);
            Vec3 axis=direction(start,end);
            g.frame(center,axis,side(axis),start.distanceTo(end)*1.04,.13-i*.065/segments,
                    WIND_HD_FLOW,(int)g.age+i,.98);
            if(!g.reduced)g.frame(center.add(0,.015,0),axis,side(axis),start.distanceTo(end)*1.03,.16,
                    WIND_HD_FILAMENTS,(int)(g.age*.72)+i,.74);
        }
        if(!g.reduced)g.frame(at.add(d.scale(reach*.64)).add(0,.14,0),d,s,.44,.41,
                WIND_HD_SHARDS,(int)(g.progress*4),.83);
    }
    private static void followingWind(AbilityMeshBuilder g,Vec3 at,Vec3 forward,Vec3 side,double radius,boolean adverse){
        double t=(g.progress+.15)%1;
        double signed=adverse?-1:1;
        for(int wing:new int[]{-1,1}){
            Vec3 start=at.add(side.scale(wing*radius)).add(0,.18+t*.18,0)
                    .add(forward.scale(signed*(t-.55)*.8));
            Vec3 end=start.add(forward.scale(signed*(adverse?.42:.85)))
                    .subtract(side.scale(wing*(adverse?.22:.065))).add(0,.07,0);
            ribbon(g,start,end,side,.12,WIND_HD_FLOW,(int)(g.age+wing),.92);
            ribbon(g,start.add(0,.025,0),end.add(0,.025,0),side,.17,WIND_HD_FILAMENTS,
                    (int)(g.age*.58)+wing,adverse?.82:.91);
        }
    }
    public static List<AbilityVfxGeometry.Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        var g=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();
        double r=Math.max(.08,p.radius()),yaw=Math.atan2(d.z,d.x);
        double clock=p.started()%200+age;
        switch(p.kind()){
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1E-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                boolean returning=p.angle()==180;
                // 16 ticks per revolution, so modulo wrapping has the same pose.
                double phase=(p.started()%16+age)*Math.PI/8*(returning?-1:1);
                WindBladeModel.draw(g,head,d,s.scale(-1),phase,r*.10);
                Vec3 tail=at.subtract(d.scale(returning?.53:.86));
                Vec3 trailEnd=head.subtract(d.scale(r*.47));
                ribbon(g,tail,trailEnd,s,returning?.17:.28,WIND_HD_FLOW,(int)clock,.97);
                ribbon(g,tail.add(v.scale(.027)),trailEnd.add(v.scale(.027)),s,returning?.22:.38,
                        WIND_HD_FILAMENTS,(int)(clock*.71)+2,.88);
                if(!reduced){
                    ribbon(g,tail,trailEnd,v,returning?.16:.29,WIND_HD_VAPOR,(int)(clock*.57)+1,.78);
                    g.frame(trailEnd.subtract(d.scale(.33)),d,v,returning?.42:.61,returning?.22:.37,
                            WIND_HD_SHARDS,(int)(clock*.83),.78);
                }
            }
            case ARC -> {
                if(p.variant()==0||p.variant()==1||p.variant()==2){
                    double scale=p.variant()==1?.78:1;
                    if(p.variant()==2){
                        double travel=Math.min(.24,g.progress*.53);
                        Vec3 point=at.add(d.scale(r*(.59+travel)));
                        g.frame(point,d,s,r*.63,.46,WIND_HD_NEEDLE,(int)(g.progress*4),.99);
                        g.frame(point.add(0,.032,0),d,s,r*.74,.52,WIND_HD_FILAMENTS,(int)(g.age*.72),.93);
                        if(!reduced){
                            g.frame(point,d,UP,r*.63,.30,WIND_HD_NEEDLE,(int)(g.progress*4),.82);
                            g.frame(point.subtract(d.scale(r*.22)).add(0,.12,0),d,s,r*.78,.55,
                                    WIND_HD_SHARDS,(int)(g.age*.91),.70);
                        }
                        break;
                    }
                    cut(g,at.add(d.scale(r*.32)).add(0,.02,0),d,s.add(UP.scale(.88)).normalize(),
                            r*scale,.66,g.progress);
                    if(p.variant()==1&&age>1.25){
                        double follow=Math.clamp((age-1.25)/4,0,1);
                        cut(g,at.add(d.scale(r*.15)).add(0,.2+follow*.25,0),
                                d,s.subtract(UP.scale(.78)).normalize(),r*.80,.61,follow);
                    }
                    break;
                }
                if(p.variant()==4){
                    for(int i=-1;i<=1;i++){
                        Vec3 foot=at.add(s.scale(i*.45));
                        feather(g,foot.add(0,.32+i*.08,0),s,UP,.39,.115);
                    }
                    break;
                }
                cut(g,at.add(d.scale(r*.38)),d,s.add(UP.scale(.68)).normalize(),r*.94,r*.51,g.progress);
            }
            case BEAM,TRAIL -> {
                if(p.kind()==TRAIL&&p.variant()==5&&(p.angle()==42||p.angle()==43)){
                    ribbon(g,at,end,s,r,WIND_HD_FLOW,(int)clock,.69);
                    if(!reduced)ribbon(g,at.add(v.scale(.02)),end.add(v.scale(.02)),v,r*.76,
                            WIND_HD_FILAMENTS,(int)(clock*.7)+2,.53);
                    break;
                }
                if(at.distanceToSqr(end)<1E-8)break;
                if(p.variant()==7&&p.anchor()>=0){
                    followingWind(g,at,d,s,r,p.angle()==180);
                    break;
                }
                if(p.kind()==TRAIL&&(p.variant()==2||p.variant()==6)){
                    boolean longDash=p.variant()==6;
                    for(int wing:new int[]{-1,1}){
                        Vec3 left=at.add(s.scale(wing*(longDash?.54:.34)));
                        Vec3 right=end.add(s.scale(wing*(longDash?.33:.19))).add(0,longDash?.22:.105,0);
                        ribbon(g,left.subtract(d.scale(longDash?.63:.4)),right.add(d.scale(.18)),
                                s,longDash?.19:.16,WIND_HD_FLOW,(int)clock+wing,.97);
                        ribbon(g,left.add(0,.055,0).subtract(d.scale(.41)),right.add(0,.08,0),
                                s,longDash?.27:.21,WIND_HD_FILAMENTS,(int)(clock*.79)+wing,.86);
                        if(!reduced)ribbon(g,left.subtract(d.scale(.35)),right,v,longDash?.27:.21,
                                WIND_HD_VAPOR,(int)(clock*.48)+1,.74);
                    }
                    if(longDash&&age<2.2){
                        Vec3 point=at.lerp(end,Math.clamp(age,0,1)).add(0,.53,0);
                        g.frame(point,d,UP,1.18,.75,WIND_HD_SPINDLE,(int)clock,.88*(1-age/2.2));
                        g.frame(point.add(0,.016,0),d,s,1.14,.68,WIND_HD_SPINDLE,(int)clock,.74*(1-age/2.2));
                        if(!reduced)g.frame(point.subtract(d.scale(.39)),d,UP,1.24,.78,WIND_HD_SHARDS,
                                (int)(clock*.83),.73*(1-age/2.2));
                    }
                    break;
                }
                ribbon(g,at,end,v,.42,WIND_HD_FLOW,(int)clock,.96);
                if(!reduced)ribbon(g,at,end,s,.31,WIND_HD_FLOW,(int)clock+1,.86);
                int n=reduced?2:3;for(int i=0;i<n;i++){
                    Vec3 point=at.lerp(end,(i+.65)/n);
                    feather(g,point,d,s,.42,.22);
                }
            }
            case BURST -> {
                if(p.variant()==5&&p.angle()==180){
                    foldback(g,at.subtract(d.scale(r*.63)).add(0,.045,0),d,s,r);
                    break;
                }
                if(p.variant()==3&&p.anchor()>=0){
                    double gap=r*(.7-.49*g.progress);
                    for(int wing:new int[]{-1,1})feather(g,at.add(s.scale(wing*gap)).add(0,1.2,0),
                            d,s.add(UP.scale(wing*.72)).normalize(),.38,.12);
                    g.frame(at.add(0,1.18+g.progress*.11,0),s,UP,.43,.25,
                            WIND_HD_SHARDS,(int)(g.progress*4),.85);
                    break;
                }
                if(p.variant()==4&&p.angle()==270){
                    for(int wing:new int[]{-1,1}){
                        Vec3 edge=at.add(s.scale(wing*r*(.45+g.progress))).add(0,.2+g.progress*.5,0);
                        ribbon(g,edge,at.add(0,.04,0),UP,.19,WIND_HD_WALL,(int)(g.progress*4),.98);
                    }
                    g.frame(at.add(d.scale(.043)).add(0,.25,0),s,UP,.62,.44,
                            WIND_HD_SHARDS,(int)(g.progress*4),.82);
                    break;
                }
                if(p.variant()==2||p.variant()==6||p.variant()==7){
                    int spokes=reduced?3:6;
                    for(int i=0;i<spokes;i++){
                        double a=p.variant()==2?yaw-.65+i*1.3/Math.max(1,spokes-1):i*Math.PI*2/spokes;
                        Vec3 start=circle(at,r*(.14+.48*g.progress),a,.16);
                        Vec3 endPoint=circle(at,r*(.38+.58*g.progress),a,.25+g.progress*.31);
                        ribbon(g,start,endPoint,UP,.20,WIND_HD_FLOW,(int)(g.progress*4),.96);
                        if(!reduced)ribbon(g,start.add(0,.04,0),endPoint.add(0,.10,0),UP,.24,
                                WIND_HD_FILAMENTS,(int)(g.age*.73)+i,.78);
                    }
                    if(p.variant()!=2)g.frame(at.add(0,.083,0),X,Z,r*(.6+.57*g.progress),r*(.6+.57*g.progress),
                            WIND_HD_RING_FLECKS,(int)(g.progress*4),.87);
                    break;
                }
                if(p.angle()==1){rotor(g,at,.3*(1-g.progress*.5),age*.15);break;}
                g.frame(at.add(0,.015,0),X,Z,r,r,WIND_HD_RING,(int)(g.progress*4),.93);
                rotor(g,at.add(0,.12,0),r*(.35+.5*g.progress),age*.06);
                g.sparks(at,r,0xD5F6E4);
            }
            case SEAL -> {
                boolean warning=p.angle()==359;
                if(p.variant()==3&&p.anchor()>=0){
                    for(int wing:new int[]{-1,1})feather(g,at.add(s.scale(wing*r)).add(0,1.18+.06*Math.sin(clock*.32),0),
                            d,s.add(UP.scale(wing*.65)).normalize(),.39,.15);
                    break;
                }
                if(p.personal()&&(p.variant()==0||p.variant()==1)){
                    double height=p.variant()==0?.16:.72;
                    for(int wing:new int[]{-1,1}){
                        Vec3 point=at.add(s.scale(wing*r)).subtract(d.scale(.22)).add(0,height,0);
                        feather(g,point,d,p.variant()==0?s:UP,p.variant()==0?.31:.22,.087);
                    }
                    break;
                }
                if(p.variant()==7&&p.anchor()>=0){
                    if(age<8){
                        double opening=r*(.42+.58*Math.min(1,age/7));
                        g.frame(at.add(0,.06,0),X,Z,opening,opening,WIND_HD_RING,(int)age/2,.96);
                    }
                    g.frame(at.add(0,.075,0),X,Z,r*1.13,r*1.13,WIND_HD_RING,(int)clock/4,.88);
                    g.frame(at.add(0,.096,0),X,Z,r*1.12,r*1.12,WIND_HD_RING_FLECKS,(int)(clock*.39),.81);
                    break;
                }
                if(p.variant()==6&&!warning){
                    g.frame(at.add(0,.04,0),X,Z,r,r,WIND_HD_RING,(int)(g.progress*4),.90);
                    g.frame(at.add(0,.065,0),X,Z,r*1.07,r*1.07,WIND_HD_RING_FLECKS,(int)(g.progress*4)+2,.78);
                    break;
                }
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
                        feather(g,point,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.60,.20);
                    }
                    for(int i=0;i<2;i++)g.bladeArc(at.add(0,.22,0),r*.83,age*.014+i*Math.PI,Math.PI*.25,Math.min(.18,r*.15),.12,0xD6F3E3,0x3A7C69);
                    g.decal(at.add(0,.025,0),Math.min(r*.55,.8),WIND_SIGIL,0xFFFFFF,.85);
                }
            }
            case SHIELD -> {
                for(int i=0;i<(reduced?3:4);i++){
                    double a=i*Math.PI*2/(reduced?3:4)+age*.05;
                    Vec3 point=circle(at,r,a,.65+.15*Math.sin(a));
                    feather(g,point,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.48,.17);
                }
                rotor(g,at.add(0,.12,0),r,age*.035);
            }
            case CHARGE -> {
                if(p.variant()==6){
                    for(int wing:new int[]{-1,1})for(int i=0;i<3;i++){
                        double t=(clock*.09+i/3.0)%1;
                        Vec3 start=at.subtract(d.scale(.35+t*1.15)).add(s.scale(wing*(.22+t*.86))).add(0,.11+t*.21,0);
                        Vec3 endPoint=start.lerp(at.add(0,.16,0),.48);
                        ribbon(g,start,endPoint,s,.14,WIND_HD_FLOW,(int)clock+i,.97);
                        if(!reduced)ribbon(g,start.add(0,.03,0),endPoint.add(0,.03,0),s,.21,
                                WIND_HD_FILAMENTS,(int)(clock*.66)+i,.83);
                    }
                    break;
                }
                rotor(g,at.add(0,.12,0),.65,age*.12);
                for(int i=0;i<4;i++){
                    Vec3 point=at.lerp(end,(i+.4)/4);
                    g.plane(point.add(0,.035,0),s,d,Math.max(.15,r),.35,WIND_SIGIL,0xFFFFFF,.62);
                }
            }
            case WALL -> {
                int n=reduced?5:8;
                for(int i=0;i<n;i++){
                    double t=(clock*.055+i/(double)n)%1;
                    double y=.37+t*3.15,wave=Math.sin(clock*.24+i)*.12;
                    Vec3 centre=at.add(0,y,0).add(d.scale(wave));
                    g.frame(centre,s.scale(i%2==0?1:-1),UP,r,.44,WIND_HD_WALL,(int)(clock/2)+i,.92);
                    if(i%2==0&&!reduced)
                        g.frame(centre.add(d.scale(.084)).add(0,.035,0),s,UP,r*.91,.42,
                                WIND_HD_VAPOR,(int)(clock*.43)+i,.79);
                    g.frame(centre.subtract(d.scale(.056)).add(0,.034,0),s,UP,r*.94,.30,
                            WIND_HD_FILAMENTS,(int)(clock*.73)+i,.80);
                }
                g.frame(at.add(0,3.55,0),s,UP,r*.78,.32,WIND_HD_WALL,(int)clock,.68);
            }
            case PILLAR -> {ribbon(g,at,end,s,.35,WIND_HD_FLOW,(int)clock,.96);rotor(g,at.add(0,.12,0),r,age*.08);}
        }
        return g.result();
    }
}
