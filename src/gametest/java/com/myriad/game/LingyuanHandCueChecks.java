package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.lingyuan.rework.LingyuanHandCues;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.*;

final class LingyuanHandCueChecks {
    private record Actor(ServerPlayer player,List<AbilityVfxPayload> cues) {
        List<AbilityVfxPayload> at(int skill,int phase) {
            return cues.stream().filter(p->p.variant()==skill&&p.angle()==phase).toList();
        }
    }

    static void run(GameTestHelper h) {
        com.myriad.client.firstperson.LingyuanHandChecks.verify(h);
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        var hero=actor(h,24);
        var interrupted=actor(h,46);
        var dasher=actor(h,66);
        var origin=hero.player.position();
        var target=h.spawn(EntityTypes.PIG,new BlockPos(3,24,4));
        target.setNoAi(true);
        target.setNoGravity(true);
        Runnable clean=()->{
            for(var a:List.of(hero,interrupted,dasher)){
                AbilityRuntime.clear(a.player);
                com.myriad.combat.BasicAttackSystem.clearFor(a.player.getUUID());
                a.player.discard();
            }
            target.discard();
            ModState.setEnabled(enabled);
            TrainingMode.setEnabled(training);
        };
        try {
            target.setPos(origin.add(0,0,1.5));
            var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            ticker.setAccessible(true);
            ticker.setInt(hero.player,0);
            hero.player.attack(target);
            h.assertTrue(hero.at(0,BASIC).isEmpty(),"Premature swing does not advance combo");
            ticker.setInt(hero.player,200);
            hero.player.attack(target);
            h.assertTrue(hero.at(0,BASIC).size()==1,"Accepted vanilla melee emits exactly one cue");
            target.discard();
            var aim=FirstGroupSkills.node("lingyuan",5);
            var d=PlayerDataProvider.get(hero.player);
            d.setSkillLevel(aim.getId(),1);
            d.setMana(700);
            AbilityRuntime.request(hero.player,d,aim);
            h.assertTrue(hero.at(5,AIM).size()==1,"Selecting the array enters a private aim pose");
            AbilityRuntime.cancel(hero.player);
            h.assertTrue(hero.at(5,STOP).size()==1&&hero.at(5,CAST).isEmpty()&&d.getMana()==700,
                    "Cancelling a point selection settles with no release or mana cost");
            for(int i=3;i<=7;i++)start(hero,i);
            start(interrupted,7);
            start(dasher,2);
        } catch(Exception e){clean.run();throw new IllegalStateException(e);}
        catch(Error e){clean.run();throw e;}

        boolean[] done={false};
        for(int t=1;t<=33;t++){
            int tick=t;
            h.runAfterDelay(t,()->{
                if(done[0])return;
                try{
                    if(tick==26)AbilityRuntime.cancel(interrupted.player);
                    for(var a:List.of(hero,interrupted,dasher))AbilityRuntime.tick(a.player);
                    if(tick!=33)return;
                    h.assertTrue(hero.at(3,CAST).size()==1&&hero.at(5,CAST).size()==1&&hero.at(6,CAST).size()==1,
                            "Shield and placed fields each drive one release even after pulses");
                    var shots=hero.at(4,CAST);
                    h.assertTrue(shots.size()==3&&shots.stream().map(AbilityVfxPayload::radius).toList().equals(List.of(0f,1f,2f))
                            &&shots.get(2).started()-shots.get(1).started()==4,"Three scheduled flying swords drive three flicks");
                    long charge=hero.at(7,GATHER).getFirst().started(), chop=hero.at(7,CHOP).getFirst().started();
                    h.assertTrue(chop-charge==24 && hero.at(7,IMPACT).size()==1
                            && hero.at(7,IMPACT).getFirst().started()-chop==6,"Long charge, downstroke and impact match server timeline");
                    h.assertTrue(interrupted.at(7,STOP).size()==1&&interrupted.at(7,IMPACT).isEmpty(),"Interrupted downstroke never signals impact");
                    h.assertTrue(!dasher.at(2,DASH).isEmpty()&&dasher.at(2,FINISH).size()==1,"Real dash starts and stops once");
                    for(var a:List.of(hero,interrupted,dasher)) for(var p:a.cues)
                        h.assertTrue(p.valid()&&p.personal()&&AbilityVfxGeometry.build(p,.5f,false).isEmpty(),"No duplicate world VFX");
                    done[0]=true;
                    clean.run();
                    System.out.println("[MYRIAD-LINGYUAN-HANDS] PASS: six casts, real melee readiness, private cues, dash, volley and interrupted two-stage ultimate");
                    h.succeed();
                }catch(Throwable e){done[0]=true;clean.run();throw e;}
            });
        }
    }

    private static void start(Actor a,int index){
        var skill=FirstGroupSkills.node("lingyuan",index);
        var c=AbilityRuntime.context(a.player,skill);
        c.point=a.player.position().add(0,0,8);
        skill.release(c);
    }

    private static Actor actor(GameTestHelper h,int y){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var cues=new ArrayList<AbilityVfxPayload>();
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){
                if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AbilityVfxPayload cue
                        &&cue.owner()==p.getId()&&LingyuanHandCues.owns(cue))cues.add(cue);
            }
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        PlayerDataProvider.get(p).setCharacterId("lingyuan");
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,y,11))));
        p.setYRot(0);p.setXRot(0);p.setNoGravity(true);
        var sword=new ItemStack(ModItems.LINGYUAN_SWORD.get());
        sword.set(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        p.setItemSlot(EquipmentSlot.MAINHAND,sword);
        return new Actor(p,cues);
    }
}
