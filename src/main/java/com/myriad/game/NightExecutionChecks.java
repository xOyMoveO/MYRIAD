package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.character.skills.yingxiao.rework.*;
import com.myriad.network.NightSkillViewPayload;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.network.NightSkillViewPayload.*;

/** Actual selected ultimate, server tick timing, teleport, target state, collision and private cues. */
final class NightExecutionChecks {
    private record Fixture(ServerPlayer player,ServerPlayer target,Vec3 origin,BlockPos floor,
                           List<NightSkillViewPayload> cues,List<NightSkillViewPayload> targetCues,Map<BlockPos,BlockState> saved){}
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();
        try{
            for(int i=0;i<6;i++){
                var f=fixture(h,i);fixtures.add(f);
                if(i==5)blockRear(f);
                var d=PlayerDataProvider.get(f.player);SkillSystem.cast(f.player,CharacterType.SLOT_R);
                if(i==5){
                    h.assertTrue(d.getMana()==700&&d.getCooldown("g1_yingxiao_ua")==0&&f.cues.isEmpty(),"No safe rear landing rejects the initial cast without payment or pose cue");
                }else{
                    h.assertTrue(d.getMana()==540&&d.getCooldown("g1_yingxiao_ua")==1700,"Accepted blink keeps original mana and cooldown");
                    h.assertTrue(SkillChargeSystem.active(f.player)&&f.cues.stream().anyMatch(c->c.action()==READY&&c.ticks()==16),"Real cast locks movement and confirms 0.8-second grip-turn cue");
                }
            }
        }catch(Throwable failure){fixtures.forEach(f->cleanup(h,f));throw failure;}
        boolean[] done={false};
        for(int tick=1;tick<=22;tick++){
            final int elapsed=tick;
            h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    if(elapsed==5)PlayerDataProvider.get(fixtures.get(1).player).setBuff("g1_stun",40);
                    if(elapsed==8){
                        fixtures.get(0).target.setYRot(90); // Recompute the rear from current target facing.
                        fixtures.get(2).target.setHealth(0);
                        blockRear(fixtures.get(3));
                        fixtures.get(4).target.setPos(fixtures.get(4).origin.add(0,0,12));
                    }
                    for(var f:fixtures)AbilityRuntime.tick(f.player);
                    if(elapsed<16)for(var f:fixtures)h.assertTrue(f.player.position().distanceTo(f.origin)<1E-6,"No premature teleport before all 16 windup ticks");
                    if(elapsed==16){
                        h.assertTrue(fixtures.stream().allMatch(f->f.targetCues.isEmpty()),"Other players never receive the caster's first-person hand cue");
                        var f=fixtures.getFirst();
                        h.assertTrue(f.player.position().distanceTo(f.origin)>3&&!AbilityRuntime.moving(f.player),"At 0.8 seconds the hero instantly relocates, not an eight-tick dash");
                        h.assertTrue(AbilityTargeting.canStand(f.player,f.player.position()),"Actual arrival is supported and collision-free");
                        var rear=f.player.position().subtract(f.target.position()).multiply(1,0,1).normalize();
                        var facing=f.target.getLookAngle().multiply(1,0,1).normalize();
                        h.assertTrue(rear.dot(facing)<-.8,"Blink uses the victim's latest facing and really lands behind");
                        var aim=f.target.getBoundingBox().getCenter().subtract(f.player.getEyePosition()).normalize();
                        h.assertTrue(f.player.getLookAngle().dot(aim)>.999,"Arrival faces the victim for the forward stab");
                        h.assertTrue(Math.abs(f.target.getHealth()-820)<.05,"Same 180 physical damage is applied once, not a dash plus a blink hit");
                        h.assertTrue(f.cues.stream().filter(c->c.action()==STRIKE).count()==1&&!SkillChargeSystem.active(f.player),"Arrival pose is confirmed once after releasing the real charge lock");
                        for(int i=1;i<5;i++){
                            var invalid=fixtures.get(i);
                            h.assertTrue(invalid.player.position().equals(invalid.origin)&&invalid.cues.stream().noneMatch(c->c.action()==STRIKE),"Stun/dead/out-of-range/no-landing never relocates or plays an arrival stab");
                            if(i!=2)h.assertTrue(invalid.target.getHealth()==1000,"Invalid blink applies no damage");
                            h.assertTrue(invalid.cues.stream().anyMatch(c->c.action()==CANCEL),"Interrupted/failed lock sends a token-specific stop cue");
                        }
                    }
                    if(elapsed==22){
                        h.assertTrue(Math.abs(fixtures.getFirst().target.getHealth()-820)<.05,"No lingering follow-up damage after the blink");
                        done[0]=true;fixtures.forEach(f->cleanup(h,f));
                        System.out.println("[MYRIAD-NIGHT-BLINK] PASS: actual selected R, 16 ticks, live rear-facing safe teleport + one hit/arrival, original payment; stun/dead/range/blocked/rejected paths and private cue cancellation");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(f->cleanup(h,f));throw failure;}
            });
        }
    }
    private static Fixture fixture(GameTestHelper h,int i){
        var level=h.getLevel();var floor=h.absolutePos(new BlockPos(60,70+i*7,20));var saved=new HashMap<BlockPos,BlockState>();
        for(int x=-3;x<=3;x++)for(int z=-2;z<=9;z++)for(int y=0;y<=3;y++){
            var at=floor.offset(x,y,z);saved.put(at,level.getBlockState(at));level.setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        var cues=new ArrayList<NightSkillViewPayload>();var targetCues=new ArrayList<NightSkillViewPayload>();var p=mock(h,cues);var target=mock(h,targetCues);
        Vec3 origin=Vec3.atBottomCenterOf(floor.above());p.setPos(origin);p.setYRot(0);p.setXRot(0);p.setNoGravity(true);
        target.setPos(origin.add(0,0,5));target.setYRot(180);target.setXRot(0);target.setNoGravity(true);
        var td=PlayerDataProvider.get(target);td.setFaction(Faction.YOUMING);td.setModMaxHealth(1000);td.setModHealthInitialized(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);level.addNewPlayer(target);
        var d=PlayerDataProvider.get(p);d.setFaction(Faction.XUANXIAO);d.setCharacterId("yingxiao");d.setLevel(20);d.setSkillLevel("g1_yingxiao_ua",1);d.setMana(700);
        p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(0);p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(ModItems.YINGCI.get()));
        return new Fixture(p,target,origin,floor,cues,targetCues,saved);
    }
    private static ServerPlayer mock(GameTestHelper h,List<NightSkillViewPayload> cues){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection); // Target players participate in entity pairing.
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof NightSkillViewPayload cue)cues.add(cue);
            }
        };p.connection.markClientLoaded();return p;
    }
    private static void blockRear(Fixture f){
        for(int x=-2;x<=2;x++)for(int z=6;z<=8;z++)for(int y=1;y<=2;y++)f.player.level().setBlockAndUpdate(f.floor.offset(x,y,z),Blocks.STONE.defaultBlockState());
    }
    private static void cleanup(GameTestHelper h,Fixture f){
        AbilityRuntime.clear(f.player);AbilityRuntime.clear(f.target);h.getLevel().removePlayerImmediately(f.target,Entity.RemovalReason.DISCARDED);
        f.saved.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));f.player.discard();
    }
}
