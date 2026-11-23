package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.sulan.rework.WindCastAnimation;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

final class WindCastAnimationChecks {
    private record Actor(ServerPlayer player,List<AbilityVfxPayload> packets){
        List<AbilityVfxPayload> cues(int index,int signal){
            return packets.stream().filter(p->cue(p)&&p.variant()==index&&p.angle()==signal).toList();
        }
        void cast(int index){
            var skill=FirstGroupSkills.node("sulan",index);
            skill.release(AbilityRuntime.context(player,skill));
        }
        void tick(){
            if(AbilityRuntime.moving(player))player.setPos(player.position().add(player.getDeltaMovement()));
            AbilityRuntime.tick(player);
        }
        void clear(){
            AbilityRuntime.clear(player);player.level().players().remove(player);player.discard();
        }
    }
    private WindCastAnimationChecks(){}

    static void start(GameTestHelper h){
        var throwsAndSpells=actor(h,148);
        var thrust=actor(h,163);
        var run=actor(h,178);
        var cancelled=actor(h,193);
        var blocked=actor(h,208);
        var actors=List.of(throwsAndSpells,thrust,run,cancelled,blocked);
        var pig=h.spawn(EntityTypes.PIG,new BlockPos(8,148,8));
        pig.setPos(throwsAndSpells.player.position().add(0,.7,4));
        pig.setNoGravity(true);pig.setNoAi(true);
        BlockPos obstacle=BlockPos.containing(blocked.player.position().add(0,.5,1));
        var old=h.getLevel().getBlockState(obstacle);
        h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());
        Runnable clear=()->{
            actors.forEach(Actor::clear);pig.discard();h.getLevel().setBlockAndUpdate(obstacle,old);
        };
        try{
            throwsAndSpells.cast(3);throwsAndSpells.cast(4);throwsAndSpells.cast(5);throwsAndSpells.cast(7);
            thrust.cast(2);run.cast(6);cancelled.cast(6);blocked.cast(2);
        }catch(Throwable error){clear.run();throw error;}
        boolean[] done={false};
        for(int i=1;i<=30;i++){
            int elapsed=i;
            h.runAfterDelay(i,()->{
                if(done[0])return;
                try{
                    actors.forEach(Actor::tick);
                    if(elapsed==4)AbilityRuntime.cancel(cancelled.player);
                    if(elapsed==30){
                        for(int index:new int[]{3,4,5,7})
                            h.assertTrue(throwsAndSpells.cues(index,CAST).size()==1,"One release cue for branch "+index);
                        var returnFrames=throwsAndSpells.packets.stream().filter(p->p.variant()==5
                                &&p.kind()==AbilityVfxGeometry.FLIGHT&&p.angle()==180).count();
                        h.assertTrue(returnFrames>0,"Return flight exists and does not cast again");
                        h.assertTrue(actors.stream().flatMap(actor->actor.packets.stream()).filter(WindCastAnimation::cue)
                                        .allMatch(p->p.personal()&&p.valid()&&AbilityVfxGeometry.build(p,1,false).isEmpty()),
                                "Only the caster receives hand poses, while flight and impact remain world effects");
                        verifyMotion(h,thrust,2);
                        verifyMotion(h,run,6);
                        h.assertTrue(run.cues(6,DASH_START).getFirst().started()-run.cues(6,WINDUP).getFirst().started()==10,
                                "Ten actual channel ticks precede the long dash");
                        h.assertTrue(cancelled.cues(6,CANCEL).size()==1&&cancelled.cues(6,DASH_START).isEmpty(),
                                "Interrupted windup releases the pose without a launch");
                        h.assertTrue(blocked.cues(2,CANCEL).size()==1&&blocked.cues(2,DASH_START).isEmpty()
                                        &&blocked.cues(2,DASH_STOP).isEmpty(),"Blocked first step has no fake launch or finish pose");
                        done[0]=true;clear.run();
                        System.out.println("[MYRIAD-WIND-CAST] PASS: personal hand cues, world launch/turn/impact, interrupted and blocked dash");
                    }
                }catch(Throwable error){done[0]=true;clear.run();throw error;}
            });
        }
    }

    private static void verifyMotion(GameTestHelper h,Actor actor,int index){
        var starts=actor.cues(index,DASH_START);
        var stops=actor.cues(index,DASH_STOP);
        h.assertTrue(starts.size()==1&&stops.size()==1&&!actor.cues(index,DASH_HOLD).isEmpty(),
                "One start and one stop accompany the actual dash");
        h.assertTrue(stops.getFirst().started()>starts.getFirst().started()
                        &&stops.getFirst().from().distanceTo(starts.getFirst().from())>3,
                "Finish is sent after real movement, from its end position");
        h.assertTrue(actor.packets.stream().anyMatch(p->p.variant()==index&&p.kind()==AbilityVfxGeometry.BURST
                        &&p.started()==stops.getFirst().started()),"Finish pose and impact share the same tick");
    }

    private static Actor actor(GameTestHelper h,int y){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var packets=new ArrayList<AbilityVfxPayload>();
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){
                if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AbilityVfxPayload v
                        &&v.owner()==p.getId())packets.add(v);
            }
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.setNoGravity(true);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,y,8))));
        p.setYRot(0);p.setXRot(0);
        PlayerDataProvider.get(p).setCharacterId("sulan");
        p.level().players().add(p);
        return new Actor(p,packets);
    }
}
