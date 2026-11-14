package com.myriad.game;
import com.myriad.character.ability.*;
import com.myriad.character.skills.shuo.*;
import com.myriad.client.firstperson.ShuoRig;
import com.myriad.network.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
/** New authored world phases, real outbound packets, and exact horizontal closed-weapon pose. */
final class ShuoWorldVfxChecks {
    record Observer(ServerPlayer player,List<AbilityVfxPayload> packets,io.netty.channel.embedded.EmbeddedChannel channel){}
    static Observer observe(GameTestHelper h,Vec3 at){
        var packets=new ArrayList<AbilityVfxPayload>();var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
            net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c&&c.payload() instanceof AbilityVfxPayload v)packets.add(v);
            }
        };p.connection.markClientLoaded();p.setPos(at);p.setNoGravity(true);p.level().players().add(p);return new Observer(p,packets,channel);
    }
    static void cleanup(Observer o){o.player.level().players().remove(o.player);o.channel.finishAndReleaseAll();o.player.discard();}
    static void form(GameTestHelper h,Observer o,ServerPlayer p,int node,boolean drawn){
        var packets=o.packets.stream().filter(v->v.owner()==p.getId()&&ShuoSkillVfx.owns(v)).toList();
        var phases=new HashSet<Integer>();packets.forEach(v->{h.assertTrue(v.valid()&&!v.personal(),"Third-person phases use valid public packets");phases.add((int)v.angle());});
        int expected=switch(node){case 2->drawn?ShuoSkillVfx.CHASE:ShuoSkillVfx.TRACE;case 3->drawn?ShuoSkillVfx.RIGHT:ShuoSkillVfx.GUARDING;
            case 4->drawn?ShuoSkillVfx.CHASE:ShuoSkillVfx.CHARGING;case 5->drawn?ShuoSkillVfx.LEFT:ShuoSkillVfx.RIFT;case 6->drawn?ShuoSkillVfx.DOWN:ShuoSkillVfx.MARK;
            case 7->ShuoSkillVfx.DOWN;default->throw new IllegalStateException();};
        h.assertTrue(phases.contains(expected),"External observer receives actual form phase, node="+node+" drawn="+drawn+" phases="+phases);
        if(node==5&&drawn)h.assertTrue(phases.contains(ShuoSkillVfx.RIGHT),"Second cut produces a separate reverse sweep at its due tick");
        if(node==2||node==4||node==6){
            h.assertTrue(phases.containsAll(List.of(ShuoDashVfx.DEPART,ShuoDashVfx.ARRIVE)),"Every completed real dash has measured departure and arrival phases");
            var departures=packets.stream().filter(v->v.angle()==ShuoDashVfx.DEPART).toList();
            var arrivals=packets.stream().filter(v->v.angle()==ShuoDashVfx.ARRIVE).toList();
            h.assertTrue(departures.size()==1&&arrivals.size()==1,"Start/end phase emitted once per dash, never every trail sample");
            h.assertTrue(arrivals.getFirst().from().distanceTo(p.position().add(0,.75,0))<.001,"Arrival follows the actual endpoint, not requested range");
        }
        if(node==6&&!drawn)h.assertTrue(phases.containsAll(List.of(ShuoSkillVfx.CHARGING,ShuoSkillVfx.TRACE,ShuoSkillVfx.SHEATHING,ShuoSkillVfx.CONTACT)),"Delayed ultimate has charge, actual path, marks and return-triggered contact");
        for(var v:packets)if(v.angle()==ShuoSkillVfx.TRACE||v.angle()==ShuoSkillVfx.CHASE)
            h.assertTrue(v.from().distanceTo(v.to())>0&&v.from().distanceTo(v.to())<=2.01,"World wake samples real per-tick movement, not the whole future path");
        if(node==6&&!drawn){
            long mark=packets.stream().filter(v->v.angle()==ShuoSkillVfx.MARK).mapToLong(AbilityVfxPayload::started).min().orElseThrow();
            long hit=packets.stream().filter(v->v.angle()==ShuoSkillVfx.CONTACT).mapToLong(AbilityVfxPayload::started).min().orElseThrow();
            h.assertTrue(hit>=mark+8,"Mark never bursts before the return-sheath damage stage");
        }
    }
    static void start(GameTestHelper h){
        var at=Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(45,100,5)));
        var p=ShuoChecks.player(h,at,new ArrayList<>());var observer=observe(h,at.add(8,0,0));
        var d=com.myriad.player.PlayerDataProvider.get(p);var w=com.myriad.character.rework.FirstGroupSkills.node("shuo",3);
        d.setSkillLevel(w.getId(),1);d.setSkillLevel(com.myriad.character.rework.FirstGroupSkills.node("shuo",1).getId(),1);
        var enemy=ShuoChecks.target(h,at.add(0,0,2));
        // Let relocated mock targets enter ticking entity sections before the immediate counter query.
        h.runAfterDelay(2,()->{
            h.assertTrue(p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().inflate(3)).contains(enemy),"Prepared parry target is spatially indexed before casting");
            com.myriad.character.SkillSystem.cast(p,1);
            h.assertTrue(ShuoCombat.parry(p,p.damageSources().mobAttack(enemy),com.myriad.combat.DamageCalc.Kind.PHYSICAL),"Real frontal parry triggers its own visible pulse");
            com.myriad.character.SkillSystem.cast(p,1);
            var signals=observer.packets.stream().filter(v->v.owner()==p.getId()).map(v->(int)v.angle()).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(signals.containsAll(List.of(ShuoSkillVfx.GUARDING,ShuoSkillVfx.PARRY,ShuoSkillVfx.EMPOWER,ShuoSkillVfx.LEFT,ShuoSkillVfx.CONTACT)),"External parry, empowered draw, counter-cut and effective hit all delivered: "+signals+" target HP="+enemy.getHealth());
        });
        var charged=ShuoChecks.player(h,at.add(0,8,0),new ArrayList<>());var cd=com.myriad.player.PlayerDataProvider.get(charged);
        var cut=com.myriad.character.rework.FirstGroupSkills.node("shuo",5);cd.setSkillLevel(cut.getId(),1);com.myriad.character.SkillSystem.cast(charged,2);
        cd.setBuff("g1_stun",1);AbilityRuntime.tick(charged);
        h.assertTrue(observer.packets.stream().anyMatch(v->v.owner()==charged.getId()&&v.kind()==AbilityVfxGeometry.CANCEL&&v.variant()==5&&v.angle()==1),"Interrupted windup explicitly cancels only that skill's world phase");
        var ready=ShuoChecks.player(h,at.add(0,16,0),new ArrayList<>());
        com.myriad.player.PlayerDataProvider.get(ready).setSkillLevel(com.myriad.character.rework.FirstGroupSkills.node("shuo",0).getId(),1);
        for(int n=1;n<=52;n++){int age=n;h.runAfterDelay(n,()->{
            AbilityRuntime.tick(ready);
            if(age==52){
                h.assertTrue(observer.packets.stream().filter(v->v.owner()==ready.getId()&&v.angle()==ShuoSkillVfx.READY).count()==1,"Full-gauge glint fires once at readiness, not every tick");
                cleanup(observer);for(var player:List.of(p,charged,ready)){AbilityRuntime.clear(player);player.discard();}enemy.discard();
                System.out.println("[MYRIAD-SHUO-WORLD-EVENTS] PASS: external parry/counter/passive glints, charge cancellation and single full-gauge edge");
            }
        });}
    }
    static void verify(GameTestHelper h){
        ShuoDashChecks.verify(h);
        ShuoSilentLineChecks.verify(h);
        int samples=0;var materials=new HashSet<Integer>();
        for(int signal=301;signal<=316;signal++)for(int index=0;index<8;index++)for(var forward:List.of(new Vec3(0,0,3),new Vec3(2,.4,2),new Vec3(0,1,0),Vec3.ZERO)){
            var at=new Vec3(10,20,-15);var p=ShuoVisuals.recipe(1,-1,index,signal,at,at.add(forward),5,12,0);
            h.assertTrue(ShuoSkillVfx.owns(p)&&!ShuoSkillVfx.ownerHidden(p),"Caster sees the same authored world phase in first and third person");
            for(int stage=0;stage<24;stage++)for(boolean low:new boolean[]{false,true}){
                float age=(stage+.5f)*p.life()/24;var mesh=AbilityVfxGeometry.build(p,age,low);
                h.assertTrue(mesh.size()<=(low?96:ShuoSkillVfx.MAX_QUADS),"Bounded per-effect high/low detail mesh");
                for(var q:mesh){
                    materials.add(q.material());h.assertTrue((q.material()==0||q.material()>=75&&q.material()<=78)&&q.alpha()>0&&q.alpha()<=1,"HD Shuo art and its narrow filament primitive have valid alpha");
                    h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startU()<q.endU()&&q.startV()>=0&&q.endV()<=1,"No atlas UV spill");
                    for(var v:List.of(q.a(),q.b(),q.c(),q.d())){
                        h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&ShuoSkillVfx.bounds(p).contains(v),"All vertices finite and inside the culling bounds");
                        if((signal==305||signal==306||signal==314)&&forward.length()>.02){
                            double along=v.subtract(at).dot(forward.normalize());
                            h.assertTrue(along>=-1E-6&&along<=forward.length()+1E-6,"Wake cannot extend beyond the measured segment");
                        }
                    }
                }samples++;
            }
            for(float age:new float[]{-1,p.life(),p.life()+10,Float.NaN,Float.POSITIVE_INFINITY})h.assertTrue(ShuoSkillVfx.build(p,age,false).isEmpty(),"No lingering mesh outside finite lifetime");
        }
        h.assertTrue(materials.equals(Set.of(0,75,76,77,78)),"Slash, guard, stream and impact art plus narrow filaments all used");
        var states=new HashSet<Integer>();var p=ShuoVisuals.recipe(1,-1,5,ShuoSkillVfx.LEFT,Vec3.ZERO,new Vec3(0,0,4),4.5f,7,0);
        for(int i=0;i<16;i++)states.add(ShuoSkillVfx.build(p,(i+.5f)*7/16,false).hashCode());
        h.assertTrue(states.size()==16,"Continuous unfolding, crossing and erasing, not a static pasted sprite");
        for(int action:new int[]{ShuoViewPayload.GUARD,ShuoViewPayload.COUNTER})for(boolean mirror:new boolean[]{false,true}){
            var f=ShuoRig.frame(action,.5,false);var blade=ShuoRig.blade(f,mirror);var sheath=ShuoRig.sheath(f,mirror);
            h.assertTrue(Math.abs(f.sheathAxis().x)>.99&&Math.abs(f.sheathAxis().y)<.05&&Math.abs(f.mouth().y)<.15,"Closed guard lies horizontally at the center, not low-left idle");
            var throat=blade.transformPosition(new Vector3f(0,ShuoRig.MODEL_GRIP_TO_MOUTH,0));
            h.assertTrue(throat.distance(sheath.transformPosition(new Vector3f()))<1E-6&&ShuoRig.showMainHand(f,false),"Horizontal guard keeps blade seated and gripping hand attached");
        }
        for(String name:List.of("shuo_guard_hd","shuo_stream_hd","shuo_impact_hd"))try(var in=ShuoWorldVfxChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/"+name+".png")){
            var image=ImageIO.read(in);h.assertTrue(image.getColorModel().hasAlpha()&&image.getWidth()>=1536&&image.getHeight()>=1024,"Native-resolution alpha masters, not enlarged low-resolution sprites");
            long lit=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int argb=image.getRGB(x,y),a=argb>>>24;
                if(x<120||y<120||x>=image.getWidth()-120||y>=image.getHeight()-120)h.assertTrue(a==0,"Transparent padding prevents filtering seams");
                if(a>200&&(argb>>16&255)>200)lit++;
            }h.assertTrue(lit>1000,"Readable bright core survives compositing");
        }catch(Exception e){throw new IllegalStateException(name,e);}
        System.out.println("[MYRIAD-SHUO-WORLD-VFX] PASS: "+samples+" authored phase/variant/direction/LOD samples, finite lifetime/UVs/bounds, no path overshoot, native HD alpha and horizontal closed guard");
    }
}
