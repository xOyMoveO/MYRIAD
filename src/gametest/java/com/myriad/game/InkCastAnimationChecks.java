package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.xuanchen.rework.InkCastAnimation;
import com.myriad.combat.MagicBullet;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.skills.xuanchen.rework.InkCastAnimation.*;

final class InkCastAnimationChecks {
    private record Actor(ServerPlayer player,List<AbilityVfxPayload> visuals) {
        List<AbilityVfxPayload> cues(int index,int signal){
            return visuals.stream().filter(p->InkCastAnimation.cue(p)&&p.variant()==index&&p.angle()==signal).toList();
        }
    }
    private InkCastAnimationChecks(){}

    static void start(GameTestHelper h){
        com.myriad.client.firstperson.InkHandChecks.verify(h);

        var regular=actor(h,112);
        var interrupted=actor(h,127);
        try {
            var p=regular.player;
            var d=PlayerDataProvider.get(p);
            var flag=new net.minecraft.world.item.ItemStack(com.myriad.registry.ModItems.ZHENQI.get());
            flag.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,flag);
            MagicBullet.tryFire(p,flag);
            MagicBullet.tryFire(p,flag);
            h.assertTrue(regular.visuals.stream().filter(InkCastAnimation::firstPersonCue)
                            .filter(event->event.angle()==BASIC&&event.variant()==0&&event.personal()).count()==1,
                    "A real launch emits exactly one private hand cue, even when right-click repeats during cooldown");
            var gate=FirstGroupSkills.node("xuanchen",4);
            d.setSkillLevel(gate.getId(),1);
            d.setMana(700);
            AbilityRuntime.request(p,d,gate);
            h.assertTrue(regular.cues(4,AIM).size()==1&&d.getPlacementType()==104,"Real selection enters the portal aim pose");
            AbilityRuntime.cancel(p);
            h.assertTrue(regular.cues(4,STOP).size()==1&&d.getPlacementType()==0&&d.getMana()==700,
                    "Cancelling aim removes its pose without spending mana");
            for(int index=2;index<=7;index++){
                var skill=FirstGroupSkills.node("xuanchen",index);
                var c=AbilityRuntime.context(p,skill);
                c.point=p.position().add(0,0,6);
                skill.release(c);
            }
            var heaven=FirstGroupSkills.node("xuanchen",6);
            heaven.release(AbilityRuntime.context(interrupted.player,heaven));
        }catch(Throwable e){cleanup(regular,interrupted);throw e;}

        boolean[] done={false};
        for(int tick=1;tick<=18;tick++){
            int elapsed=tick;
            h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    AbilityRuntime.tick(regular.player);AbilityRuntime.tick(interrupted.player);
                    if(elapsed==5)AbilityRuntime.cancel(interrupted.player);
                    if(elapsed==18){
                        for(int index=2;index<=7;index++)
                            h.assertTrue(regular.cues(index,index==3?SELF:RELEASE).size()==1,"One cast release cue for variant "+index);
                        var channel=regular.cues(6,WINDUP);
                        var release=regular.cues(6,RELEASE).getFirst();
                        h.assertTrue(!channel.isEmpty()&&release.started()-channel.getFirst().started()==16
                                        &&channel.getLast().radius()>channel.getFirst().radius(),
                                "Real local progress drives the 16-tick channel");
                        h.assertTrue(regular.visuals.stream().anyMatch(p->p.variant()==6&&p.kind()==AbilityVfxGeometry.SEAL
                                        &&p.angle()==360&&p.started()==release.started()),
                                "The cast pose opens on the same tick as the authoritative field");
                        h.assertTrue(interrupted.cues(6,STOP).size()==1&&interrupted.cues(6,RELEASE).isEmpty(),"Interrupted channel never opens");
                        for(int index:new int[]{5,7}){
                            long placed=regular.cues(index,RELEASE).getFirst().started();
                            final int variant=index;
                            var activation=regular.visuals.stream().filter(InkCastAnimation::firstPersonCue)
                                    .filter(p->p.variant()==variant&&p.angle()==ACTIVATE).toList();
                            h.assertTrue(activation.size()==1&&activation.getFirst().started()-placed==(index==5?12:14),
                                    "Personal flash lands when the delayed world skill activates");
                        }
                        h.assertTrue(regular.visuals.stream().filter(InkCastAnimation::cue)
                                        .allMatch(p->p.personal()&&p.valid()&&AbilityVfxGeometry.build(p,.5f,false).isEmpty()),
                                "Hand pose events are personal and don't produce world particles");
                        h.assertTrue(regular.visuals.stream().filter(InkCastAnimation::firstPersonCue)
                                        .allMatch(p->p.valid()&&AbilityVfxGeometry.build(p,.5f,false).isEmpty()),
                                "Personal hand events don't produce a second world particle");
                        done[0]=true;cleanup(regular,interrupted);
                        System.out.println("[MYRIAD-INK-CAST] PASS: six gestures, self/ally variants, real selection/reset and channel release/interrupt cues");
                    }
                }catch(Throwable e){done[0]=true;cleanup(regular,interrupted);throw e;}
            });
        }
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
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,y,11))));
        p.setYRot(0);p.setXRot(0);
        PlayerDataProvider.get(p).setCharacterId("xuanchen");
        p.level().players().add(p);
        return new Actor(p,packets);
    }
    private static void cleanup(Actor... actors){
        for(var actor:actors){
            AbilityRuntime.clear(actor.player);
            MagicBullet.clearFor(actor.player.getUUID());
            actor.player.level().players().remove(actor.player);actor.player.discard();
        }
    }
}
