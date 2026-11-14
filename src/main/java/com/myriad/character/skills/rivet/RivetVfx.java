package com.myriad.character.skills.rivet;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Industrial solids, staged high-resolution layers and sparse pixel sparks; never a combat instruction. */
public final class RivetVfx {
    public static final int THEME=13,STEEL=0x9CAFB5,AMBER=0xEBAF4C,CYAN=0x70D8E0,DARK=0x35464F;
    private static final RosterProjectileModels MODELS=RosterProjectileModels.load("rivet_models_mesh.json");
    private RivetVfx(){}
    public static boolean stationary(AbilityVfxPayload p){return p.theme()==THEME&&p.anchor()<0&&(p.kind()==WALL||p.kind()==SEAL);}
    public static boolean replaces(AbilityVfxPayload a,AbilityVfxPayload b){
        return a.theme()==THEME&&b.theme()==THEME&&a.owner()==b.owner()&&a.variant()==b.variant()&&a.kind()==b.kind()
                &&(a.kind()==WALL||a.kind()==CHARGE||a.kind()==BEAM||a.kind()==SEAL||a.kind()==SHIELD&&a.anchor()==b.anchor());
    }
    public static boolean usesModel(AbilityVfxPayload p){return p.theme()==THEME&&(p.kind()==WALL||p.kind()==ARC||p.kind()==CHARGE&&p.variant()==2);}
    private static void world(Cast c,int kind,Vec3 from,Vec3 to,double radius,double stage,int life){
        var p=new AbilityVfxPayload(c.player.getId(),-1,THEME,c.skill.index(),kind,from,to,(float)radius,(float)stage,life,c.now(),false);
        if(!p.valid())return;
        for(var viewer:c.player.level().players())if(viewer.position().distanceToSqr(from)<=Math.pow(64+radius,2)
                &&(viewer==c.player||VisionSystem.canSeePosition(viewer,from)))PacketDistributor.sendToPlayer(viewer,p);
    }
    private static void action(Cast c,int kind,Vec3 from,Vec3 to,double radius,double stage,int life){AbilityVisuals.emit(c,kind,from,to,radius,stage,life,false);}
    public static void hammerWindup(Cast c){
        RivetAudio.play(c.player,"hammer_lift");
        c.whileActive(2400,age->{if(!AbilityRuntime.preparingSkill(c.player).equals(c.skill.getId()))return false;
            if(age%2==0)action(c,CHARGE,c.player.position(),c.player.position().add(c.forward(3)),3,c.windupProgress(),3);return true;});
    }
    public static void hammer(Cast c){
        RivetAudio.play(c.player,"hammer_swing");
        action(c,ARC,c.player.position(),c.player.position().add(c.forward(3)),3,110,7);
    }
    public static void anchor(Cast c,Vec3 point,Vec3 normal,boolean ready){
        var memory=c.memory("anchor_visual");
        if(memory.point==null||memory.point.distanceToSqr(point)>.0001||c.now()-memory.last>8)RivetAudio.playAt(c.player,point,"anchor");
        memory.point=point;memory.last=c.now();
        world(c,WALL,point,point.add(normal),.38,ready?1:0,5);
    }
    public static void anchorInactive(ServerPlayer player){
        var skill=com.myriad.character.rework.FirstGroupSkills.node("rivet",3);if(skill==null)return;
        var c=AbilityRuntime.context(player,skill);var memory=c.memory("anchor_visual");
        if(memory.point!=null){AbilityVisuals.cancelSkill(c);memory.point=null;}
    }
    public static void wall(Cast c,Vec3 point,boolean anchored){
        RivetAudio.playAt(c.player,point,anchored?"anchor_hit":"wall_hit");
        action(c,BURST,point,point.add(c.forward(1)),anchored?.9:.6,anchored?1:0,10);
    }
    public static void shield(Cast c,LivingEntity target,double radius){
        RivetAudio.playAt(c.player,target.position(),"strut");
        AbilityVisuals.following(c,SHIELD,target,radius,0,12);
    }
    public static void cable(Cast c,Vec3 from,Vec3 to){
        var memory=c.memory("cable_visual");if(c.now()-memory.last>8){RivetAudio.play(c.player,"cable");}memory.last=c.now();
        action(c,BEAM,from,to,.045,0,3);
    }
    public static void field(Cast c,Vec3 center,int ticks){
        RivetAudio.playAt(c.player,center,"protocol");
        var state=c.memory("field_visual");int token=++state.count;
        c.whileActive(ticks,age->{if(token!=state.count)return false;if(age%4==0)world(c,SEAL,center,center.add(0,1,0),4,age,6);return true;});
    }
    public static void chargeWindup(Cast c){
        RivetAudio.play(c.player,"charge");
        c.whileActive(2400,age->{if(!AbilityRuntime.preparingSkill(c.player).equals(c.skill.getId()))return false;
            if(age%2==0)action(c,CHARGE,c.player.position(),c.player.position().add(c.forward(5)),.7,c.windupProgress(),3);return true;});
    }
    /** Called at committed launch, after the private movement lease has been created. */
    public static void chargeStart(Cast c){
        RivetAudio.play(c.player,"charge_release");
        final Vec3[] previous={c.player.position()};
        c.whileActive(2400,age->{if(!AbilityRuntime.moving(c.player))return false;
            chargeTrail(c,previous[0],c.player.position());previous[0]=c.player.position();return true;});
    }
    public static void chargeTrail(Cast c,Vec3 from,Vec3 to){if(from.distanceToSqr(to)>.001)action(c,TRAIL,from.add(0,.3,0),to.add(0,.3,0),.8,0,7);}
    public static void chargeHit(Cast c,LivingEntity target){
        RivetAudio.playAt(c.player,target.position(),"wall_hit");
        action(c,BURST,target.position().add(0,.7,0),target.position().add(c.forward(1)).add(0,.7,0),1,2,10);
    }
    private static void box(AbilityMeshBuilder g,Vec3 at,Vec3 a,Vec3 b,Vec3 n,double w,double h,double depth,int rgb,double alpha){
        Vec3 x=a.scale(w),y=b.scale(h),z=n.scale(depth);Vec3 p=at.subtract(z),q=at.add(z);
        g.quad(p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y),rgb,alpha,0);
        g.quad(q.subtract(x).subtract(y),q.subtract(x).add(y),q.add(x).add(y),q.add(x).subtract(y),rgb,alpha,0);
        g.quad(p.subtract(x).subtract(y),q.subtract(x).subtract(y),q.add(x).subtract(y),p.add(x).subtract(y),DARK,alpha,0);
        g.quad(p.subtract(x).add(y),p.add(x).add(y),q.add(x).add(y),q.subtract(x).add(y),STEEL,alpha,0);
        g.quad(p.subtract(x).subtract(y),p.subtract(x).add(y),q.subtract(x).add(y),q.subtract(x).subtract(y),DARK,alpha,0);
        g.quad(p.add(x).subtract(y),q.add(x).subtract(y),q.add(x).add(y),p.add(x).add(y),rgb,alpha,0);
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||age<0||age>=p.life())return List.of();
        var g=new AbilityMeshBuilder(p,age,reduced);var solids=new ArrayList<Quad>();
        Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();double t=g.progress;
        switch(p.kind()){
            case WALL->{
                MODELS.append(solids,"anchor",at.add(d.scale(.19)),d,.65,RIVET_MODEL,1);
                Vec3 center=at.add(d.scale(.22));int color=p.angle()>0?CYAN:AMBER;
                g.plane(center,s,v,.32,.32,YIKE_HD_RING,color,.65);
                for(int i=0;i<4;i++){
                    double a=i*Math.PI/2;Vec3 node=center.add(s.scale(Math.cos(a)*.32)).add(v.scale(Math.sin(a)*.32));
                    g.plane(node,s,v,.035,.035,KEHENG_HD_SPARK,color,p.angle()>0?.9:.45+.25*Math.sin((p.started()+age)*.14));
                }
                if(p.angle()==0){g.line(center.add(s.scale(-.13)).add(v.scale(-.13)),center.add(s.scale(.13)).add(v.scale(.13)),d,.045,AMBER,.9);}
            }
            case CHARGE->{
                if(p.variant()==2){
                    Vec3 head=at.add(d.scale(.9)).add(s.scale(-.65)).add(0,1.4+.2*p.angle(),0);
                    MODELS.append(solids,"hammer",head,UP,1.45,RIVET_MODEL,.95f);
                    g.plane(head.add(d.scale(.015)),s,UP,.34,.16,YIKE_HD_VEIL,AMBER,.65);
                    g.line(at.add(0,.06,0).add(d.scale(.3)),end.add(0,.06,0),UP,.035,AMBER,.6);
                }else{
                    double length=at.distanceTo(end);
                    for(int i=0;i<(reduced?3:5);i++){
                        Vec3 c=at.add(d.scale(.7+i*(length-.7)/5)).add(0,.055,0);
                        g.line(c.subtract(s.scale(.45)),c.add(d.scale(.28)),UP,.05,AMBER,.7);
                        g.line(c.add(s.scale(.45)),c.add(d.scale(.28)),UP,.05,AMBER,.7);
                    }
                    g.plane(at.add(0,.8,0),s,UP,.6,.55,YIKE_HD_VEIL,CYAN,.28+.42*p.angle());
                    if(!reduced)g.sparks(at.add(0,.25,0),.6,AMBER);
                }
            }
            case ARC->{
                double half=Math.toRadians(55),angle=-half+2*half*t;Vec3 f=d.scale(Math.cos(angle)).add(s.scale(Math.sin(angle)));
                Vec3 head=at.add(f.scale(2.2)).add(0,1,0);
                MODELS.append(solids,"hammer",head,UP,1.5,RIVET_MODEL,(float)(1-.25*t));
                g.bladeArc(at.add(0,.8,0),3,Math.atan2(d.z,d.x)-half,2*half,.16,.08,AMBER,DARK);
                g.decal(at.add(d.scale(1.6)).add(0,.83,0),1.2,SHUO_SLASH,STEEL,.58*(1-t));
                if(age>1)g.sparks(head,.5,AMBER);
            }
            case BEAM->{
                // Closed steel cable in two helically offset strands; the cyan trace follows the real rope.
                g.rod(at,end,.018,STEEL,DARK,DARK,.95);
                int steps=reduced?4:7;
                for(int i=0;i<steps;i++){
                    Vec3 a=at.lerp(end,i/(double)steps).add(s.scale((i%2==0?1:-1)*.018));
                    Vec3 b=at.lerp(end,(i+1.0)/steps).add(s.scale((i%2==0?-1:1)*.018));
                    g.line(a,b,UP,.012,AMBER,.8);
                }
                g.trail(at,end,.035,SHUO_STREAM,CYAN,.4);
            }
            case SHIELD->{
                double radius=p.radius();
                for(int i=0;i<(reduced?3:4);i++){
                    double a=i*Math.PI/2;Vec3 f=new Vec3(Math.cos(a),0,Math.sin(a)),right=side(f),c=at.add(f.scale(radius)).add(0,.8,0);
                    box(g,c,right,UP,f,.25,.43,.025,STEEL,.32);
                    g.plane(c.add(f.scale(.03)),right,UP,.28,.48,YIKE_HD_SHIELD,CYAN,.46);
                }
                g.ring(at.add(0,.05,0),radius,.035,AMBER,.68);
            }
            case SEAL->{
                // The exact four-block perimeter has sparse raised safety pylons, not an opaque floor disc.
                double radius=p.radius();g.ring(at,radius,.045,AMBER,.85);
                if(!reduced)g.decal(at.add(0,.014,0),radius*.98,YIKE_HD_RING,STEEL,.28);
                // Two sparse vertical hoops expose the real spherical area around a possibly raised wall anchor.
                for(Vec3 axis:new Vec3[]{X,Z})for(int segment=0;segment<(reduced?8:12);segment++){
                    int n=reduced?8:12;double a=segment*Math.PI*2/n,b=(segment+1)*Math.PI*2/n;
                    Vec3 x=at.add(axis.scale(radius*Math.cos(a))).add(UP.scale(radius*Math.sin(a)));
                    Vec3 y=at.add(axis.scale(radius*Math.cos(b))).add(UP.scale(radius*Math.sin(b)));
                    g.line(x,y,axis.cross(UP),.018,CYAN,.24);
                }
                for(int i=0;i<4;i++){
                    double a=i*Math.PI/2;Vec3 f=new Vec3(Math.cos(a),0,Math.sin(a)),right=side(f),node=at.add(f.scale(radius-.1));
                    box(g,node.add(0,.15,0),right,UP,f,.10,.15,.10,AMBER,.82);
                    g.plane(node.add(0,.4,0),right,UP,.06,.10,KEHENG_HD_SPARK,CYAN,.8);
                    g.line(node.add(right.scale(-.3)),node.add(right.scale(.3)),UP,.04,STEEL,.7);
                }
            }
            case TRAIL->{
                g.trail(at,end,.18,SHUO_STREAM,AMBER,.65*(1-t));
                if(!reduced){g.trail(at.add(s.scale(.4)),end.add(s.scale(.4)),.08,YIKE_HD_STREAM,CYAN,.45*(1-t));g.sparks(end,.3,AMBER);}
            }
            case BURST->{
                int color=p.angle()==1?CYAN:AMBER;
                g.plane(at,s,UP,.3+.5*t,.3+.5*t,YIKE_HD_IMPACT,color,.9*(1-t));
                if(age>1)g.plane(at.add(d.scale(.025)),s,UP,.65+.4*t,.65+.4*t,SHUO_IMPACT,STEEL,.7*(1-t));
                g.ring(at.add(0,.025,0),p.radius()*(.3+.7*t),.06,color,.7*(1-t));
                g.sparks(at,p.radius(),AMBER);
            }
            default->{}
        }
        solids.addAll(g.result());return List.copyOf(solids);
    }
}
