package com.myriad.game;

import com.myriad.character.SkillPresentation;
import com.myriad.character.ability.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.combat.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.*;
import com.myriad.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class FlameHitChecks {
    private record Viewer(ServerPlayer p,List<AbilityVfxPayload> hits){}
    static void verify(GameTestHelper h){
        try(var in=FlameHitChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/fenjue_hit.png")){
            var im=ImageIO.read(in);h.assertTrue(im.getWidth()==1024&&im.getHeight()==512&&im.getColorModel().hasAlpha(),"Eight-frame impact retains the fixed source motion-reference cell metadata");
            var hashes=new HashSet<Integer>();int peak=0,tail=0;
            for(int frame=0;frame<8;frame++){int hash=1,visible=0;
                for(int y=0;y<256;y++)for(int x=0;x<256;x++){
                    int p=im.getRGB(frame%4*256+x,frame/4*256+y),alpha=p>>>24;hash=31*hash+p;
                    if(x<24||y<24||x>=232||y>=232)h.assertTrue(alpha==0,"No impact-frame bleed");
                    if(alpha>25)visible++;
                }
                h.assertTrue(visible>100&&hashes.add(hash),"Eight distinct nonempty authored phases");if(frame==3)peak=visible;if(frame==7)tail=visible;
            }
            h.assertTrue(tail<peak*.15,"Last phase is dying fragments, not another bright explosion or a loop");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        int cases=0;
        for(var f:List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(-.3,.5,.7).normalize()))
        for(boolean crit:new boolean[]{false,true})for(boolean periodic:new boolean[]{false,true})for(boolean low:new boolean[]{false,true}){
            var p=FlameHitVisuals.recipe(1,new Vec3(3,1,2),f,crit,periodic,100);h.assertTrue(p.valid()&&FlameHitVfx.owns(p)&&!FlameMajorVfx.owns(p),"Separate contact marker never becomes a cage/area explosion");
            for(float age:new float[]{0,.5f,1,2.5f,4,6,7.9f}){
                var mesh=AbilityVfxGeometry.build(p,age,low);h.assertTrue(!mesh.isEmpty()&&mesh.size()<=(low?8:20),"Compact full/reduced layered contact budget");
                h.assertTrue(mesh.stream().anyMatch(q->q.material()==FENJUE_HIT),"New authored impact is the subject");
                for(var q:mesh){h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.endU()>q.startU()&&q.endV()>q.startV(),"Finite opacity and real frame UVs");
                    h.assertTrue(Set.of(FENJUE_HIT,FENJUE_PIXEL_EMBERS,FENJUE_PIXEL_STREAM).contains(q.material()),"No ground disc, generic cube or unregistered sprite");
                    for(var point:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                            &&point.distanceTo(p.from())<2&&FlameVfxGeometry.bounds(p).contains(point),"All axes including vertical have finite tight contact-local geometry");}
            }
            h.assertTrue(AbilityVfxGeometry.build(p,8,false).isEmpty(),"Impact expires at 0.4 seconds with no parked model");cases++;
        }
        var cadence=new FlameHitVisuals.Cadence();UUID targetId=UUID.randomUUID();
        h.assertTrue(cadence.allow(targetId,100,false)&&!cadence.allow(targetId,100,false)&&!cadence.allow(targetId,102,false)&&cadence.allow(targetId,103,false),"Repeated shield/HP/nested same-target events are one contact, direct visual cadence three ticks");
        h.assertTrue(!cadence.allow(targetId,108,true)&&cadence.allow(targetId,109,true),"Periodic burn uses six ticks, not one big flash per damage tick");
        cadence=new FlameHitVisuals.Cadence();for(int i=0;i<5;i++)h.assertTrue(cadence.allow(UUID.randomUUID(),200,false)==(i<4),"Large AoE caps four contact bursts per three ticks");
        for(int i=0;i<300;i++)cadence.allow(UUID.randomUUID(),300+i*3L,false);h.assertTrue(cadence.tracked()<=16,"Cosmetic target history stays bounded");
        h.assertTrue(cadence.allow(UUID.randomUUID(),1,false),"World time rollback cannot permanently lock feedback");

        boolean mod=ModState.isEnabled();var viewers=new ArrayList<Viewer>();
        var target=h.spawn(EntityTypes.PIG,new BlockPos(35,210,40));target.setNoAi(true);target.setNoGravity(true);
        var tower=new DefenseTowerEntity(ModEntities.DEFENSE_TOWER.get(),h.getLevel());
        try{
            ModState.setEnabled(true);var origin=target.position().add(0,0,-2);var actor=viewer(h,viewers,origin,false);var near=viewer(h,viewers,origin.add(3,0,0),true);var far=viewer(h,viewers,origin.add(90,0,0),true);
            var p=actor.p;PlayerDataProvider.get(p).setCharacterId("fenjue");
            h.assertTrue(!FlameHitVisuals.eligible(p,tower,10)&&!FlameHitVisuals.eligible(p,p,10),"No self/building contact effect");
            CombatImpactFeedback.confirmed(p,target,0,false,false,0,false);CombatImpactFeedback.confirmed(p,target,0,false,false,Float.NaN,false);
            h.assertTrue(actor.hits.isEmpty(),"No incoming-roll, zero or invalid damage flash");
            float hp=target.getHealth();ModShieldSystem.grant(target,"fenjue_hit_test",500,100);target.invulnerableTime=0;
            DamageCalc.dealUncriticalSkillDamage(p,target,12,DamageCalc.Kind.MAGIC,0);
            h.assertTrue(target.getHealth()==hp&&ModShieldSystem.value(target)<500,"Actual skill damage consumes real mod shield before feedback");
            h.assertTrue(actor.hits.size()==1&&near.hits.size()==1&&far.hits.isEmpty(),"Exactly one confirmed impact reaches caster and visible nearby viewer, not distant viewer");
            var packet=actor.hits.getFirst();h.assertTrue(packet.from().distanceTo(FlameHitVisuals.contact(p,target))<1E-6&&packet.started()==p.level().getGameTime(),"Uses actual struck body surface and confirmation tick");
            CombatImpactFeedback.confirmed(p,target,0,false,false,8,false);SkillPresentation.hit(p,target,false);
            h.assertTrue(actor.hits.size()==1,"Same-tick HP portion and legacy hit cannot duplicate the shield contact");
            FlameHitVisuals.clear(p);actor.hits.clear();target.invulnerableTime=0;
            DamageCalc.dealBasicDamage(p,target,10,DamageCalc.Kind.PHYSICAL);
            h.assertTrue(actor.hits.size()==1,"Actual basic attack shield hit also uses the custom contact, independent of audio throttle");
            FlameHitVisuals.clear(p);actor.hits.clear();target.setInvulnerable(true);target.invulnerableTime=0;
            DamageCalc.dealUncriticalSkillDamage(p,target,12,DamageCalc.Kind.MAGIC,0);h.assertTrue(actor.hits.isEmpty(),"Invulnerable no-loss attacks produce no contact");target.setInvulnerable(false);
            ModState.setEnabled(false);CombatImpactFeedback.confirmed(p,target,0,false,false,10,false);h.assertTrue(actor.hits.isEmpty(),"Mod off is vanilla");
            ModState.setEnabled(true);PlayerDataProvider.get(p).setCharacterId("tianxing");CombatImpactFeedback.confirmed(p,target,0,false,false,10,false);h.assertTrue(actor.hits.isEmpty(),"Other roles cannot obtain Fenjue visuals");
        }finally{
            for(var v:viewers){AbilityRuntime.clear(v.p);if(h.getLevel().players().contains(v.p))h.getLevel().removePlayerImmediately(v.p,Entity.RemovalReason.DISCARDED);else v.p.discard();}
            ModShieldSystem.clearFor(target.getUUID());target.discard();tower.discard();ModState.setEnabled(mod);
        }
        System.out.println("[MYRIAD-FLAME-HIT] PASS: native alpha eight-phase contact; "+cases+" 3D/crit/periodic/LOD cases, budgets/TTL; bounded target/AoE cadence; actual skill/basic shield-loss chain, invulnerable/mod-off/other-role exclusions, visible nearby audience and no duplicate generic hit");
    }
    private static Viewer viewer(GameTestHelper h,List<Viewer> viewers,Vec3 pos,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var hits=new ArrayList<AbilityVfxPayload>();
        var con=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(con);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),con,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp&&cp.payload() instanceof AbilityVfxPayload hit&&FlameHitVfx.owns(hit))hits.add(hit);}
        };p.connection.markClientLoaded();p.setPos(pos);p.setNoGravity(true);PlayerDataProvider.get(p).setFaction(Faction.XUANXIAO);if(add)h.getLevel().addNewPlayer(p);
        var v=new Viewer(p,hits);viewers.add(v);return v;
    }
}
