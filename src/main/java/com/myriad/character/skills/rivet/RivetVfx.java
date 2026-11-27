package com.myriad.character.skills.rivet;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.myriad.network.Packets;
import java.util.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Industrial solids, staged high-resolution layers and sparse pixel sparks; never a combat instruction. */
public final class RivetVfx {
    public static final int THEME=13,STEEL=0x9CAFB5,AMBER=0xEBAF4C,CYAN=0x70D8E0,DARK=0x35464F;
    public static final int WALL_HIT=0,ANCHOR_HIT=1,CHARGE_HIT=2,ANCHOR_SET=3,ANCHOR_END=4,RHYTHM_HIT=5;
    private static final RosterProjectileModels MODELS=RosterProjectileModels.load("rivet_models_mesh.json");
    private RivetVfx(){}
    public static boolean stationary(AbilityVfxPayload p){return p.theme()==THEME&&p.anchor()<0
            &&(p.kind()==WALL||p.kind()==SEAL||p.kind()==BURST&&p.variant()==3);}
    /** Position-authorized attachments/regions do not require a tracked caster entity beside them. */
    public static boolean survivesOwner(AbilityVfxPayload p,boolean present,boolean alive){return present?alive:stationary(p);}
    public static boolean replaces(AbilityVfxPayload a,AbilityVfxPayload b){
        return a.theme()==THEME&&b.theme()==THEME&&a.owner()==b.owner()&&a.variant()==b.variant()&&a.kind()==b.kind()
                &&(a.kind()==WALL||a.kind()==CHARGE||a.kind()==BEAM||a.kind()==SEAL||a.kind()==SHIELD&&a.anchor()==b.anchor());
    }
    public static boolean usesModel(AbilityVfxPayload p){return p.theme()==THEME&&(p.kind()==WALL||p.kind()==ARC||p.kind()==CHARGE&&p.variant()==2);}
    private static void world(Cast c,int kind,Vec3 from,Vec3 to,double radius,double stage,int life){
        var p=new AbilityVfxPayload(c.player.getId(),-1,THEME,c.skill.index(),kind,from,to,(float)radius,(float)stage,life,c.now(),false);
        if(!p.valid())return;
        for(var viewer:c.player.level().players())if(viewer.position().distanceToSqr(from)<=Math.pow(64+radius,2)
                &&(viewer==c.player||VisionSystem.canSeePosition(viewer,from)))Packets.sendToPlayer(viewer,p);
    }
    private static void action(Cast c,int kind,Vec3 from,Vec3 to,double radius,double stage,int life){AbilityVisuals.emit(c,kind,from,to,radius,stage,life,false);}
    private static final int BASIC_CONTACT=6;
    public static boolean basicContact(AbilityVfxPayload p) {
        return p.theme()==THEME && p.personal() && p.kind()==BURST && p.angle()==BASIC_CONTACT;
    }
    public static void basicContact(ServerPlayer p) {
        if (!"rivet".equals(com.myriad.player.PlayerDataProvider.get(p).getCharacterId())) return;
        var at=p.position();
        com.myriad.network.Packets.sendToPlayer(p,new AbilityVfxPayload(p.getId(),-1,THEME,0,BURST,
                at,at,0,BASIC_CONTACT,3,p.level().getGameTime(),true));
    }
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
        if(memory.point==null||memory.point.distanceToSqr(point)>.0001||c.now()-memory.last>8){
            RivetAudio.playAt(c.player,point,"anchor");world(c,BURST,point,point.add(normal),.55,ANCHOR_SET,10);
        }
        memory.point=point;memory.lastPosition=normal;memory.last=c.now();
        world(c,WALL,point,point.add(normal),.38,ready?1:0,5);
    }
    public static void anchorInactive(ServerPlayer player){
        var skill=com.myriad.character.rework.FirstGroupSkills.node("rivet",3);if(skill==null)return;
        var c=AbilityRuntime.context(player,skill);var memory=c.memory("anchor_visual");
        if(memory.point!=null){AbilityVisuals.cancelSkill(c);
            world(c,BURST,memory.point,memory.point.add(memory.lastPosition),.45,ANCHOR_END,7);memory.point=null;}
    }
    public static void wall(Cast c,Vec3 point,Vec3 direction,boolean anchored){
        RivetAudio.playAt(c.player,point,anchored?"anchor_hit":"wall_hit");
        // Contact plane follows the committed push, even if the owner has turned since the hit.
        action(c,BURST,point,point.add(direction),anchored?.9:.6,anchored?ANCHOR_HIT:WALL_HIT,anchored?14:11);
    }
    public static void shield(Cast c,LivingEntity target,double radius){
        RivetAudio.playAt(c.player,target.position(),"strut");
        AbilityVisuals.following(c,SHIELD,target,radius,0,12);
    }
    public static void cable(Cast c,Vec3 from,Vec3 to){
        var memory=c.memory("cable_visual");if(memory.point==null||c.now()-memory.last>8){RivetAudio.play(c.player,"cable");}memory.last=c.now();
        memory.point=to;
        action(c,BEAM,from,to,.045,0,3);
    }
    public static void cableEnd(Cast c){
        var memory=c.memory("cable_visual");if(memory.point==null)return;
        action(c,BEAM,c.player.getEyePosition(),memory.point,.045,1,6);memory.point=null;
    }
    public static void rhythmHit(Cast c,LivingEntity target){
        action(c,BURST,target.getBoundingBox().getCenter(),target.getBoundingBox().getCenter().add(c.forward(1)),.52,RHYTHM_HIT,8);
    }
    public static void field(Cast c,Vec3 center,int ticks){
        RivetAudio.playAt(c.player,center,"protocol");
        var state=c.memory("field_visual");int token=++state.count;
        c.whileActive(ticks,age->{if(token!=state.count)return false;if(age%4==0)world(c,SEAL,center,center.add(0,1,0),4,age,6);return true;});
    }
    public static void chargeWindup(Cast c){
        RivetAudio.play(c.player,"charge");
        c.whileActive(2400,age->{if(!AbilityRuntime.preparingSkill(c.player).equals(c.skill.getId()))return false;
            if(age%2==0)action(c,CHARGE,c.player.position(),c.player.position().add(c.forward(BreachCharge.DISTANCE)),.7,c.windupProgress(),3);return true;});
    }
    /** Called at committed launch, after the private movement lease has been created. */
    public static void chargeStart(Cast c){
        RivetAudio.play(c.player,"charge_release");
        action(c,CHARGE,c.player.position(),c.player.position().add(c.forward(BreachCharge.DISTANCE)),.7,2,3);
        final Vec3[] previous={c.player.position()};
        c.whileActive(2400,age->{if(!AbilityRuntime.moving(c.player))return false;
            chargeTrail(c,previous[0],c.player.position());previous[0]=c.player.position();return true;});
    }
    public static void chargeTrail(Cast c,Vec3 from,Vec3 to){if(from.distanceToSqr(to)>.001)action(c,TRAIL,from.add(0,.3,0),to.add(0,.3,0),.8,0,7);}
    public static void chargeHit(Cast c,LivingEntity target,Vec3 direction){
        RivetAudio.playAt(c.player,target.position(),"wall_hit");
        action(c,BURST,target.position().add(0,.7,0),target.position().add(direction).add(0,.7,0),1,CHARGE_HIT,10);
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
    /** Four open corners read as a mechanical clamp without painting an opaque targeting disc. */
    private static void brackets(AbilityMeshBuilder g,Vec3 at,Vec3 right,Vec3 up,Vec3 normal,double radius,double arm,int color,double alpha){
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}){
            Vec3 corner=at.add(right.scale(x*radius)).add(up.scale(y*radius));
            g.line(corner,corner.subtract(right.scale(x*arm)),normal,.035,color,alpha);
            g.line(corner,corner.subtract(up.scale(y*arm)),normal,.035,color,alpha);
        }
    }
    private static double smooth(double value){double t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    public static float brightAlpha(float alpha){return (float)(1-Math.pow(1-Math.clamp(alpha,0,1),2));}
    private static Quad brighten(Quad q){
        if(q.material()==RIVET_MODEL)return q;
        int color=0;
        // Lift the luminous layers, retaining dark steel/dent contrast and the original model texture.
        double gain=q.color()==DARK?1.08:1.25;
        for(int shift:new int[]{0,8,16})color|=(int)Math.min(255,Math.round(((q.color()>>shift)&255)*gain))<<shift;
        return new Quad(q.a(),q.b(),q.c(),q.d(),color,brightAlpha(q.alpha()),q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV());
    }
    /** First-person has its own gripped hammer. Keep the world arc, suppress only the duplicate solid. */
    public static List<Quad> firstPerson(AbilityVfxPayload p,List<Quad> mesh){
        if(p.kind()==ARC||p.kind()==CHARGE&&p.variant()==2)return mesh.stream().filter(q->q.material()!=RIVET_MODEL).toList();
        if(p.kind()==SHIELD)return mesh.stream().map(q->new Quad(q.a(),q.b(),q.c(),q.d(),q.color(),q.alpha()*.32f,
                q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV())).toList();
        return mesh;
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(!p.valid()||age<0||age>=p.life())return List.of();
        var g=new AbilityMeshBuilder(p,age,reduced);var solids=new ArrayList<Quad>();
        Vec3 at=p.from(),end=p.to(),d=direction(at,end),s=side(d),v=s.cross(d).normalize();double t=g.progress;
        switch(p.kind()){
            case WALL->{
                // Mesh +Y is the wall-normal. The back plate sits beyond the real wall face.
                String model=reduced?"anchor_lod":"anchor";double tip=MODELS.tipOffset(model,1.2)+.015;
                MODELS.append(solids,model,at.add(d.scale(tip)),d,1.2,RIVET_MODEL,1);
                Vec3 center=at.add(d.scale(tip+.03));int color=p.angle()>0?CYAN:AMBER;
                double pulse=.68+.16*Math.sin((p.started()+age)*.12);
                brackets(g,center,s,v,d,.35,.11,color,p.angle()>0?.85:pulse*.55);
                g.plane(center.add(v.scale(.12)),s,v,.10,.025,RIVET_HD_FRICTION,color,.85);
                if(p.angle()==0){
                    // One amber moving lug denotes recovery; ready has four stationary cyan lugs.
                    double spin=(p.started()+age)*.09;
                    Vec3 lug=center.add(s.scale(Math.cos(spin)*.36)).add(v.scale(Math.sin(spin)*.36));
                    g.plane(lug,s,v,.035,.035,KEHENG_HD_SPARK,AMBER,.9);
                }else for(int i=0;i<4;i++){
                    double a=i*Math.PI/2;Vec3 lug=center.add(s.scale(Math.cos(a)*.35)).add(v.scale(Math.sin(a)*.35));
                    g.plane(lug,s,v,.025,.025,KEHENG_HD_SPARK,CYAN,.85);
                }
            }
            case CHARGE->{
                if(p.variant()==2){
                    double lift=smooth(p.angle());Vec3 head=at.add(d.scale(.55-.22*lift)).add(s.scale(-.64)).add(0,1.75+.30*lift,0);
                    double ready=-Math.toRadians(55);Vec3 hammerX=d.scale(Math.sin(ready)).subtract(s.scale(Math.cos(ready)));
                    MODELS.appendOriented(solids,reduced?"hammer_lod":"hammer",head,UP,hammerX,1.45,RIVET_MODEL,1);
                    g.plane(head.add(d.scale(.025)),s,UP,.28,.13,YIKE_HD_VEIL,AMBER,.35+.35*lift);
                    // Short piston compression lines hug the head, rather than covering the full melee arc.
                    for(int i=0;i<3;i++){
                        Vec3 c=head.add(s.scale((i-1)*.16)).add(d.scale(.10));
                        g.line(c.add(0,-.12-.13*(1-lift),0),c.add(0,-.08,0),d,.025,CYAN,.4+.45*lift);
                    }
                    g.line(at.add(0,.06,0).add(d.scale(.45)),end.add(0,.06,0),UP,.025,AMBER,.38);
                }else{
                    double length=at.distanceTo(end);
                    double progress=smooth(p.angle());
                    for(int i=0;i<(reduced?3:5);i++){
                        Vec3 c=at.add(d.scale(.7+i*(length-.7)/5)).add(0,.055,0);double alpha=.25+.6*Math.clamp(progress*5-i,0,1);
                        g.line(c.subtract(s.scale(.42)),c.add(d.scale(.22)),UP,.045,AMBER,alpha);
                        g.line(c.add(s.scale(.42)),c.add(d.scale(.22)),UP,.045,AMBER,alpha);
                    }
                    brackets(g,at.add(d.scale(.55)).add(0,.72,0),s,UP,d,.48-.08*progress,.17,AMBER,.45+.4*progress);
                    g.plane(at.add(d.scale(.58)).add(0,.72,0),s,UP,.38,.25,YIKE_HD_VEIL,STEEL,.24+.2*progress);
                    if(!reduced)g.sparks(at.add(s.scale(.5)).add(0,.15,0),.28,AMBER);
                }
            }
            case ARC->{
                double sweep=smooth(t/.62),half=Math.toRadians(55),angle=-half+2*half*sweep;
                Vec3 f=d.scale(Math.cos(angle)).add(s.scale(Math.sin(angle))),head=at.add(f.scale(2.35)).add(0,1.28-.16*sweep,0);
                Vec3 hammerX=d.scale(Math.sin(angle)).subtract(s.scale(Math.cos(angle)));
                MODELS.appendOriented(solids,reduced?"hammer_lod":"hammer",head,UP,hammerX,1.5,RIVET_MODEL,(float)(1-.32*smooth((t-.65)/.35)));
                // A short, moving three-band wake follows the actual head; it never fills the whole cone.
                int steps=reduced?4:6;double tail=Math.max(-half,angle-.70);
                for(int i=0;i<steps;i++){
                    double a=tail+(angle-tail)*i/steps,b=tail+(angle-tail)*(i+1)/steps;
                    Vec3 fa=d.scale(Math.cos(a)).add(s.scale(Math.sin(a))),fb=d.scale(Math.cos(b)).add(s.scale(Math.sin(b)));
                    Vec3 ca=at.add(fa.scale(2.35)).add(0,.95,0),cb=at.add(fb.scale(2.35)).add(0,.95,0);
                    double opacity=(.2+.7*(i+1.0)/steps)*(1-.7*t);
                    g.quad(ca.add(0,-.05,0),cb.add(0,-.05,0),cb.add(0,.14,0),ca.add(0,.14,0),DARK,opacity,0);
                    g.quad(ca,cb,cb.add(0,.07,0),ca.add(0,.07,0),AMBER,opacity,RIVET_HD_FRICTION);
                    g.line(ca.add(0,.07,0),cb.add(0,.07,0),UP,.025,STEEL,opacity);
                }
                if(age>1&&age<5)g.sparks(head.add(0,-.1,0),.35,AMBER);
            }
            case BEAM->{
                boolean released=p.angle()>0;double retract=released?smooth(t):0;
                Vec3 start=at.add(d.scale(released?.12:0)),stop=end.lerp(at,retract*.92);
                double alpha=released?1-t:.95;
                g.rod(start,stop,.021,STEEL,DARK,DARK,alpha);
                int steps=reduced?4:8;
                for(int i=0;i<steps;i++){
                    Vec3 a=start.lerp(stop,i/(double)steps).add(s.scale((i%2==0?1:-1)*.024));
                    Vec3 b=start.lerp(stop,(i+1.0)/steps).add(s.scale((i%2==0?-1:1)*.024));
                    g.line(a,b,v,.016,AMBER,.75*alpha);
                }
                if(!released){
                    double phase=((p.started()+age)*.11)%1;Vec3 pulse=start.lerp(stop,1-phase);
                    g.plane(pulse,s,v,.065,.065,KEHENG_HD_SPARK,CYAN,.8);
                    brackets(g,end.subtract(d.scale(.025)),s,v,d,.15,.06,CYAN,.65);
                }else{
                    g.plane(stop,s,v,.13+.09*t,.13+.09*t,RIVET_HD_COMPRESSION,AMBER,.65*(1-t));
                    if(!reduced&&age<3)g.sparks(end,.25,AMBER);
                }
            }
            case SHIELD->{
                double radius=p.radius();
                boolean station=p.variant()==0,protocol=p.variant()==6;int panels=station?3:4;
                double deployment=smooth(age/3),opacity=1-smooth((t-.65)/.35);
                for(int i=0;i<(reduced?3:panels);i++){
                    double a=i*Math.PI*2/panels;Vec3 f=new Vec3(Math.cos(a),0,Math.sin(a)),right=side(f);
                    Vec3 c=at.add(f.scale(radius*(1.18-.18*deployment))).add(0,station?.52:.8,0);
                    box(g,c,right,UP,f,station?.18:.26,station?.25:.42,.025,STEEL,.34*opacity);
                    // Mechanical braces stay separate from the translucent shield pane.
                    g.line(c.add(right.scale(-.23)).add(0,-.28,0),c.add(right.scale(.23)).add(0,.28,0),f,.032,AMBER,.8*opacity);
                    g.plane(c.add(f.scale(.03)),right,UP,.28,station?.29:.46,YIKE_HD_SHIELD,protocol?AMBER:CYAN,.47*opacity);
                }
                g.ring(at.add(0,.05,0),radius,.035,protocol?CYAN:AMBER,.55*opacity);
            }
            case SEAL->{
                // Anchor-relative spherical safety boundary, exactly matching the four-block region.
                double elapsed=p.angle()+age,opacity=Math.min(1,Math.max(0,(120-elapsed)/12));
                double radius=p.radius(),deploy=smooth(elapsed/8);g.ring(at,radius,.035,AMBER,.68*opacity);
                for(Vec3 axis:new Vec3[]{X,Z})for(int segment=0;segment<(reduced?8:12);segment++){
                    int n=reduced?8:12;double a=segment*Math.PI*2/n,b=(segment+1)*Math.PI*2/n;
                    Vec3 x=at.add(axis.scale(radius*Math.cos(a))).add(UP.scale(radius*Math.sin(a)));
                    Vec3 y=at.add(axis.scale(radius*Math.cos(b))).add(UP.scale(radius*Math.sin(b)));
                    g.line(x,x.lerp(y,.7),axis.cross(UP),.024,CYAN,.38*opacity*deploy);
                }
                for(int i=0;i<4;i++){
                    double a=i*Math.PI/2;Vec3 f=new Vec3(Math.cos(a),0,Math.sin(a)),right=side(f),node=at.add(f.scale(radius-.1));
                    box(g,node.add(0,.10+.10*deploy,0),right,UP,f,.12,.18,.05,AMBER,.8*opacity);
                    g.plane(node.add(0,.35,0),right,UP,.045,.10,KEHENG_HD_SPARK,CYAN,.8*opacity);
                    g.line(node.add(right.scale(-.27)),node.add(right.scale(.27)),UP,.035,STEEL,.7*opacity);
                }
                // A single expanding commissioning wave; refreshes continue the original field age.
                if(elapsed<10)g.ring(at.add(0,.015,0),radius*deploy,.045,CYAN,.48*(1-elapsed/10));
            }
            case TRAIL->{
                // Two floor-skimming skid rails leave the centre and crosshair clear.
                for(int sign:new int[]{-1,1}){
                    Vec3 offset=s.scale(sign*.38);
                    g.trail(at.add(offset),end.add(offset),.065,RIVET_HD_FRICTION,AMBER,.75*(1-t));
                    g.line(at.add(offset).add(0,.02,0),end.add(offset).add(0,.02,0),UP,.028,STEEL,.65*(1-t));
                }
                if(!reduced)g.sparks(end,.23,AMBER);
            }
            case BURST->{
                int stage=(int)p.angle();double fade=1-t;
                if(stage==ANCHOR_SET||stage==ANCHOR_END){
                    double r=stage==ANCHOR_SET?.7-.33*smooth(t):.35+.25*t;
                    brackets(g,at.add(d.scale(.41)),s,v,d,r,.15,stage==ANCHOR_SET?CYAN:AMBER,.85*fade);
                    if(age<4)g.plane(at.add(d.scale(.43)),s,v,.20+.2*t,.20+.2*t,RIVET_HD_COMPRESSION,STEEL,.65*fade);
                }else if(stage==ANCHOR_HIT){
                    // Compression first, four jaws close next, then a separate cyan lock flash.
                    double close=smooth((age-1)/4),r=.92-.45*close;
                    g.plane(at.subtract(d.scale(.025)),s,v,.38+.35*t,.38+.35*t,SHUO_IMPACT,DARK,.8*fade);
                    if(age<4)g.plane(at.subtract(d.scale(.045)),s,v,.32+.18*t,.32+.18*t,RIVET_HD_COMPRESSION,AMBER,.95*(1-age/4));
                    brackets(g,at.subtract(d.scale(.065)),s,v,d,r,.22,CYAN,.95*fade);
                    if(age>2){
                        g.plane(at.subtract(d.scale(.085)),s,v,.28,.28,YIKE_HD_RING,CYAN,.85*fade);
                        for(int i=0;i<4;i++){
                            double a=i*Math.PI/2;Vec3 f=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                            g.line(at.add(f.scale(.12)).subtract(d.scale(.10)),at.add(f.scale(.35)).subtract(d.scale(.10)),d,.032,STEEL,.8*fade);
                        }
                    }
                    if(age<7)g.sparks(at.subtract(d.scale(.12)),.65,AMBER);
                }else{
                    boolean charge=stage==CHARGE_HIT,rhythm=stage==RHYTHM_HIT;
                    double spread=(charge?.50:.30)+(charge?.75:.50)*smooth(t);
                    // Dark contact dent, sharp HD compression, then hard-edged radial splinters.
                    g.plane(at.subtract(d.scale(.025)),s,v,spread*.85,spread*.85,SHUO_IMPACT,DARK,.9*fade);
                    if(age<5)g.plane(at.subtract(d.scale(.045)),s,v,spread,spread,RIVET_HD_COMPRESSION,rhythm?CYAN:AMBER,.95*fade);
                    int n=reduced?5:8;
                    for(int i=0;i<n;i++){
                        double a=i*Math.PI*2/n+.16;Vec3 f=s.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                        Vec3 start=at.add(f.scale(spread*.40)).subtract(d.scale(.06+.10*t));
                        Vec3 stop=at.add(f.scale(spread*(1.1+.3*t))).subtract(d.scale(.06+.22*t));
                        g.line(start,stop,d,i%2==0?.04:.025,i%2==0?STEEL:AMBER,.88*fade);
                    }
                    if(charge)brackets(g,at.subtract(d.scale(.08)),s,v,d,.50+.30*t,.19,AMBER,.85*fade);
                    if(!reduced&&age<6)g.sparks(at.subtract(d.scale(.1)),p.radius()*.65,AMBER);
                }
            }
            default->{}
        }
        solids.addAll(g.result());return solids.stream().map(RivetVfx::brighten).toList();
    }
}
