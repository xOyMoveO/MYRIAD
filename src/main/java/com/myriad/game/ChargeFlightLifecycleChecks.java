package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Real flight completion packets plus pure client lifetime/late-frame isolation. */
final class ChargeFlightLifecycleChecks {
    private record Fixture(ServerPlayer player,List<AbilityVfxPayload> packets,List<net.minecraft.world.entity.Entity> targets,
                           Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks,int expectedEnds){}
    static void start(GameTestHelper h){
        lifetime(h);
        var fixtures=new ArrayList<Fixture>();
        try{
            for(int i=0;i<7;i++){
                var fixture=fixture(h,i);fixtures.add(fixture);
                var p=fixture.player;var c=AbilityRuntime.context(p,FirstGroupSkills.node("lingyuan",4));
                switch(i){
                    case 0->c.projectile(3,1.5,.3,false,false,(t,b)->{});
                    case 1->c.projectile(10,2,.7,false,false,(t,b)->{});
                    case 2->c.projectile(10,1.5,.3,false,false,(t,b)->{});
                    case 3->c.projectile(3,1,.3,true,true,(t,b)->{});
                    case 4->{c.skill.release(c);c.skill.present(c);} // Include the actual launch gate, not just flight packets.
                    case 5->{
                        PlayerDataProvider.get(p).setCharacterId("yunsheng");
                        var crane=FirstGroupSkills.candidates("yunsheng").stream().filter(s->s instanceof com.myriad.character.skills.yunsheng.rework.PaperCraneSkill).findFirst().orElseThrow();
                        var cast=AbilityRuntime.context(p,crane);crane.release(cast);
                    }
                    case 6->c.projectile(100,.1,.3,true,false,(t,b)->{});
                }
            }
        }catch(Throwable failure){fixtures.forEach(ChargeFlightLifecycleChecks::cleanup);throw failure;}
        boolean[] done={false};
        for(int tick=1;tick<=36;tick++){
            int elapsed=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    if(elapsed==3){
                        var reset=fixtures.get(6);int old=reset.packets.stream().filter(p->p.kind()==FLIGHT).findFirst().orElseThrow().instance();
                        AbilityRuntime.clear(reset.player);
                        int fresh=AbilityRuntime.context(reset.player,FirstGroupSkills.node("lingyuan",4)).newVisualInstance();
                        h.assertTrue(fresh!=old,"Reset must not reuse an ID still present in an old completion packet/tombstone");
                    }
                    fixtures.forEach(f->AbilityRuntime.tick(f.player));
                    if(elapsed==36){
                        for(int i=0;i<fixtures.size();i++){
                            var fixture=fixtures.get(i);var starts=new HashSet<Integer>();var finishes=new HashMap<Integer,Integer>();
                            var ledger=new AbilityVisualLifetime();
                            for(var packet:fixture.packets){
                                if(packet.kind()==FLIGHT){h.assertTrue(packet.instance()>0,"Every moving flight frame has its own identity");starts.add(packet.instance());}
                                if(packet.kind()==CANCEL&&packet.angle()==2){finishes.merge(packet.instance(),1,Integer::sum);ledger.end(packet,packet.started());}
                            }
                            h.assertTrue(finishes.size()==fixture.expectedEnds,"Every natural/hit/wall/return/volley/crane finish is sent: case="+i+", ends="+finishes);
                            h.assertTrue(finishes.values().stream().allMatch(count->count==1),"A projectile finishes exactly once");
                            if(i<6){
                                h.assertTrue(starts.equals(finishes.keySet()),"No moving model is left without an end packet: case="+i);
                                for(var packet:fixture.packets)if(packet.kind()==FLIGHT)
                                    h.assertTrue(ledger.finished(packet,fixture.player.level().getGameTime())||AbilityVisualLifetime.expired(packet,fixture.player.level().getGameTime()),"An end signal or expiry invalidates even already queued old model frames");
                            }else h.assertTrue(fixture.packets.stream().anyMatch(p->p.kind()==CANCEL&&p.angle()==0),"Reset still sends full owner cleanup");
                        }
                        h.assertTrue(fixtures.get(4).packets.stream().filter(p->p.kind()==FLIGHT).map(AbilityVfxPayload::instance).distinct().count()==3,
                                "Three simultaneous swords keep separate identities, not one per skill");
                        verifyVolleyPresentation(h,fixtures.get(4).packets);
                        done[0]=true;fixtures.forEach(ChargeFlightLifecycleChecks::cleanup);
                        System.out.println("[MYRIAD-FLIGHT-LIFECYCLE] PASS: real range/hit/wall/return/three-shot volley/paper-crane/reset, independent identities, one end each, queued-frame suppression, TTL fallback and wire");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(ChargeFlightLifecycleChecks::cleanup);throw failure;}
            });
        }
    }
    private static Fixture fixture(GameTestHelper h,int index){
        var packets=new ArrayList<AbilityVfxPayload>();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        // This observer is in level.players(): entity pairing queries NeoForge channel attributes.
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AbilityVfxPayload visual&&visual.owner()==p.getId())packets.add(visual);
            }
        };
        p.connection.markClientLoaded();p.setNoGravity(true);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,80+index*12,7))));p.setYRot(0);p.setXRot(0);
        PlayerDataProvider.get(p).setCharacterId("lingyuan");
        // Test-only packet observer: no player entity ticks or game-loop double stepping.
        p.level().players().add(p);
        var targets=new ArrayList<net.minecraft.world.entity.Entity>();
        var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        if(index==1){
            var pig=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new BlockPos(2,80,2));pig.setPos(p.position().add(0,.5,3));pig.setNoGravity(true);pig.setNoAi(true);targets.add(pig);
        }
        if(index==2){
            var at=BlockPos.containing(p.getEyePosition().add(0,0,3));blocks.put(at,p.level().getBlockState(at));
            p.level().setBlockAndUpdate(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        return new Fixture(p,packets,targets,blocks,index==4?3:index==6?0:1);
    }
    private static void cleanup(Fixture f){
        AbilityRuntime.clear(f.player);f.player.level().players().remove(f.player);f.targets.forEach(net.minecraft.world.entity.Entity::discard);
        f.blocks.forEach((pos,state)->f.player.level().setBlockAndUpdate(pos,state));f.player.discard();
    }
    private static AbilityVfxPayload packet(int owner,int instance,int kind,long time){
        return new AbilityVfxPayload(owner,-1,5,4,kind,Vec3.ZERO,new Vec3(0,0,1),.2f,kind==CANCEL?2:0,3,time,false,instance);
    }
    private static void verifyVolleyPresentation(GameTestHelper h,List<AbilityVfxPayload> packets){
        int flights=0,gates=0;
        for(var p:packets){
            if(p.theme()!=5||p.variant()!=4||p.kind()==CANCEL)continue;
            for(boolean reduced:new boolean[]{false,true}){
                for(float age:new float[]{0,.25f,.75f,1,1.5f,2.9f}){
                    var mesh=AbilityVfxGeometry.build(p,age,reduced);
                    var body=mesh.stream().filter(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL).toList();
                    if(p.kind()==FLIGHT&&age<1){
                        h.assertTrue(!body.isEmpty(),"A fresh volley frame retains the complete near/far sword");
                        Vec3 direction=p.to().subtract(p.from()).normalize();
                        Vec3 tip=p.from().lerp(p.to(),age);
                        double leading=body.stream().flatMap(q->List.of(q.a(),q.b(),q.c(),q.d()).stream())
                                .mapToDouble(v->v.subtract(tip).dot(direction)).max().orElseThrow();
                        h.assertTrue(Math.abs(leading)<1E-6,"Real sword tip interpolates along this tick, rather than parking ahead");
                    }else h.assertTrue(body.isEmpty(),"Launch/contact light and stale flight frames never recreate stationary volley swords");
                }
            }
            if(p.kind()==FLIGHT)flights++;
            if(p.kind()==SEAL){gates++;h.assertTrue(p.life()==9,"Launch light ends with the third launch, not a lingering sword array");}
        }
        h.assertTrue(flights>3&&gates==1,"The real three-shot release and its actual presentation are covered");
        // This fix must not delete the persistent passive orbit, defensive shields, or sword arrays.
        for(int variant:new int[]{0,3,5}){
            int kind=variant==5?SEAL:SHIELD;
            var p=new AbilityVfxPayload(1,kind==SHIELD?1:-1,5,variant,kind,Vec3.ZERO,new Vec3(0,0,1),1,360,14,100,false);
            h.assertTrue(AbilityVfxGeometry.build(p,5,false).stream().anyMatch(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL),
                    "Intended orbit/shield/array swords retain their real lifetime");
        }
        System.out.println("[MYRIAD-VOLLEY-PRESENTATION] PASS: real launch cue has no duplicate swords; fresh frames move, stale bodies vanish within one tick; orbit/shield/array preserved");
    }
    private static void lifetime(GameTestHelper h){
        var ledger=new AbilityVisualLifetime();
        var a=packet(1,1,FLIGHT,100);var b=packet(1,2,FLIGHT,100);var other=packet(2,1,FLIGHT,100);
        ledger.end(packet(1,1,CANCEL,101),101);
        h.assertTrue(ledger.finished(a,101)&&!ledger.finished(b,101)&&!ledger.finished(other,101),"Ending one sword never removes a sibling or another owner");
        h.assertTrue(!ledger.finished(packet(1,1,BURST,101),101),"Contact/disappearance sprites keep their short lifetime");
        h.assertTrue(!AbilityVisualLifetime.expired(a,102)&&AbilityVisualLifetime.expired(a,103),"World time expires a frozen final model even if particle ticking is delayed");
        ledger.prune(134);h.assertTrue(ledger.retained()==0,"Ended-flight identities have bounded lifetime");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{AbilityVfxPayload.CODEC.encode(buffer,b);h.assertTrue(AbilityVfxPayload.CODEC.decode(buffer).equals(b),"Moving instance identity survives the actual codec");}finally{buffer.release();}
        h.assertTrue(!packet(1,-1,FLIGHT,100).valid()&&!packet(1,0,CANCEL,100).valid(),"Invalid flight IDs cannot cancel an untracked effect");
        for(int kind:new int[]{BEAM,PILLAR}){
            var p=new AbilityVfxPayload(1,-1,5,6,kind,Vec3.ZERO,new Vec3(0,0,5),.4f,0,12,100,false);
            var mesh=AbilityVfxGeometry.build(p,11,false);
            h.assertTrue(mesh.stream().noneMatch(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL),"Completed decorative flight/falling sword keeps only dissipating light, not a grounded sword model");
        }
        var craneContact=new AbilityVfxPayload(1,-1,9,2,BURST,Vec3.ZERO,new Vec3(0,0,1),.8f,360,9,100,false);
        h.assertTrue(AbilityVfxGeometry.build(craneContact,1,false).stream().noneMatch(q->q.material()==AbilityVfxMaterials.SPIRIT_CRANE),
                "Paper-crane contact no longer recreates a stationary crane model after flight ends");
    }
}
