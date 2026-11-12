package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yingxiao.rework.*;
import com.myriad.network.AbilityMotionPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real skill release, authoritative motion packets and collision-stepped mock players, not network latency acceptance. */
final class NightDashRangeChecks {
    private record Fixture(ServerPlayer player,AbilityRuntime.Cast cast,Vec3 origin,Vec3 endpoint,boolean wall,
            LivingEntity target,List<AbilityMotionPayload> motions,Map<BlockPos,BlockState> blocks){}
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();
        try{
            for(int i=0;i<4;i++){
                var f=fixture(h,i);fixtures.add(f);var p=f.player;var c=f.cast;
                h.assertTrue(c.skill.getManaCost()==85&&c.skill.getCooldownTicks(1)==(i%2==0?200:240),"Dash costs and cooldowns remain unchanged");
                if(i==1){
                    h.assertTrue(c.skill.pointRange(c)==7,"Return preview and server confirmation share the new 7-block range");
                    var ground=c.point(c.skill.pointRange(c));
                    h.assertTrue(ground!=null&&ground.distanceTo(f.origin)>6.9&&AbilityTargeting.inRange(f.origin,ground,7),"Real ground projection reaches the new maximum, rather than the old 4 blocks");
                    h.assertTrue(!AbilityTargeting.inRange(f.origin,f.origin.add(0,0,7.1),7),"Return does not allow out-of-range points");
                }
                c.skill.release(c);
                h.assertTrue(p.position().equals(f.origin),"Neither outward skill teleports at release");
                double expected=i%2==0?9.0/5:7.0/4;
                h.assertTrue(Math.abs(p.getDeltaMovement().length()-expected)<1E-6,"Actual initial velocity uses the new range and duration");
                h.assertTrue(!f.motions.isEmpty()&&new Vec3(f.motions.getFirst().tx(),f.motions.getFirst().ty(),f.motions.getFirst().tz()).distanceTo(f.endpoint)<1E-6,
                        "Client receives the real 9/7-block endpoint, including when looking down");
            }
        }catch(Throwable failure){fixtures.forEach(NightDashRangeChecks::cleanup);throw failure;}
        boolean[] done={false};
        for(int tick=1;tick<=8;tick++){
            final int elapsed=tick;
            h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    for(var f:fixtures){
                        var p=f.player;
                        if(AbilityRuntime.moving(p))p.move(MoverType.SELF,AbilityTargeting.clampPrediction(p.position(),f.endpoint,p.getDeltaMovement()));
                        AbilityRuntime.tick(p);
                        h.assertTrue(p.getZ()<=f.endpoint.z+1E-5,"Bounded prediction never overshoots the new endpoint");
                        if(f.wall)h.assertTrue(p.getZ()<f.origin.z+3.2&&f.target.getHealth()==1000,"Fast body sweep stops before the wall and cannot hit the enemy behind it");
                    }
                    if(elapsed==5)h.assertTrue(fixtures.get(0).player.position().distanceTo(fixtures.get(0).endpoint)<1E-5&&!AbilityRuntime.moving(fixtures.get(0).player),
                            "Actual Assault reaches 9 blocks and completes after five guided movement ticks");
                    if(elapsed==4)h.assertTrue(fixtures.get(1).player.position().distanceTo(fixtures.get(1).endpoint)<1E-5&&!AbilityRuntime.moving(fixtures.get(1).player),
                            "Actual Return reaches 7 blocks in the accelerated four-tick outward movement");
                    if(elapsed==8){
                        for(int i=0;i<2;i++){
                            var f=fixtures.get(i);double expected=i==0?150:120;
                            h.assertTrue(Math.abs(f.target.getHealth()-(1000-expected))<.05,"Targets beyond the old range are hit exactly once with the unchanged damage formula");
                        }
                        var returning=fixtures.get(1);
                        h.assertTrue(returning.cast.skill.recastAvailable(returning.cast)&&returning.cast.memory().until>returning.cast.now(),"Original 3-second return window remains active");
                        returning.cast.skill.recast(returning.cast);
                        h.assertTrue(returning.player.position().distanceTo(returning.origin)<1E-6&&!returning.cast.skill.recastAvailable(returning.cast),"Second cast returns to the original anchor and consumes the window");
                        h.assertTrue(Math.abs(returning.target.getHealth()-880)<.05,"Return teleport adds no second damage hit");
                        done[0]=true;fixtures.forEach(NightDashRangeChecks::cleanup);
                        System.out.println("[MYRIAD-NIGHT-DASH] PASS: real 9-block / 5-tick Assault, 7-block Return preview and motion, no outward teleport/overshoot, swept walls, far single hits and 3-second return anchor");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(NightDashRangeChecks::cleanup);throw failure;}
            });
        }
    }
    private static Fixture fixture(GameTestHelper h,int index){
        var blocks=new HashMap<BlockPos,BlockState>();var level=h.getLevel();var floor=h.absolutePos(new BlockPos(30,190+index*8,7));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=11;z++)for(int y=0;y<=3;y++){
            var pos=floor.offset(x,y,z);blocks.put(pos,level.getBlockState(pos));level.setBlockAndUpdate(pos,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        boolean wall=index>=2;
        if(wall)for(int y=1;y<=2;y++)level.setBlockAndUpdate(floor.offset(0,y,4),Blocks.STONE.defaultBlockState());
        var motions=new ArrayList<AbilityMotionPayload>();var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AbilityMotionPayload motion)motions.add(motion);
            }
        };
        p.connection.markClientLoaded();p.setNoGravity(true);Vec3 origin=Vec3.atBottomCenterOf(floor.above());p.setPos(origin);p.setYRot(0);p.setXRot(index%2==0?89:0);
        var data=PlayerDataProvider.get(p);data.setCharacterId("yingxiao");p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);
        var skill=FirstGroupSkills.node("yingxiao",index%2==0?4:5);data.setSkillLevel(skill.getId(),1);
        var c=AbilityRuntime.context(p,skill);Vec3 endpoint=origin.add(0,0,index%2==0?ShadowAssaultSkill.DASH_DISTANCE:ShadowReturnSkill.DASH_DISTANCE);c.point=endpoint;
        var target=h.spawn(EntityTypes.PIG,new BlockPos(30,191+index*8,7));target.setPos(origin.add(0,0,index%2==0?7.5:6.1));
        target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        return new Fixture(p,c,origin,endpoint,wall,target,motions,blocks);
    }
    private static void cleanup(Fixture f){AbilityRuntime.clear(f.player);f.target.discard();f.blocks.forEach((pos,state)->f.player.level().setBlockAndUpdate(pos,state));f.player.discard();}
}
