package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.WarSpiritHandCues;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.firstperson.WarSpiritHandMotion;
import com.myriad.client.firstperson.WarSpiritHandRig;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.MagicBullet;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.myriad.character.ability.WarSpiritHandCues.*;

final class WarSpiritHandChecks {
    private record Actor(ServerPlayer player,String role,List<AbilityVfxPayload> cues) {
        long count(int slot,int kind) {return cues.stream().filter(p->p.variant()==slot&&p.angle()==kind).count();}
    }

    static void run(GameTestHelper h) {
        verifyPoses(h);
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);
        var war=actor(h,"chixiao",24);
        var bell=actor(h,"yunsheng",42);
        var cancelled=actor(h,"yunsheng",58);
        var emptyParry=actor(h,"chixiao",72);
        var jumper=actor(h,"chixiao",86);
        var courier=actor(h,"yunsheng",100);
        var actors=List.of(war,bell,cancelled,emptyParry,jumper,courier);
        var landing=BlockPos.containing(jumper.player.position().add(0,-1,5));
        var oldLanding=h.getLevel().getBlockState(landing);
        h.getLevel().setBlockAndUpdate(landing,Blocks.STONE.defaultBlockState());
        var dummy=h.spawn(EntityTypes.PIG,new BlockPos(7,24,12));
        dummy.setNoAi(true);dummy.setNoGravity(true);
        var tether=h.spawn(EntityTypes.PIG,new BlockPos(7,42,13));
        tether.setNoAi(true);tether.setNoGravity(true);
        Runnable clean=()->{
            dummy.discard();
            tether.discard();
            h.getLevel().setBlockAndUpdate(landing,oldLanding);
            for(var a:actors){AbilityRuntime.clear(a.player);MagicBullet.clearFor(a.player.getUUID());BasicAttackSystem.clearFor(a.player.getUUID());a.player.discard();}
            ModState.setEnabled(enabled);TrainingMode.setEnabled(training);
        };
        try {
            var timer=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            timer.setAccessible(true);
            dummy.setPos(war.player.position().add(0,0,1.1));
            timer.setInt(war.player,0);
            h.assertTrue(PlayerDataProvider.onPlayerAttack(war.player,dummy),"Melee callback rejects an uncharged swing");
            h.assertTrue(war.count(0,BASIC)==0,"Rejected near swing must not advance the blade combo");
            timer.setInt(war.player,200);
            war.player.attack(dummy);
            h.assertTrue(war.count(0,BASIC)==1,"One accepted melee swing advances the blade combo once");
            MagicBullet.tryFire(bell.player,bell.player.getMainHandItem());
            MagicBullet.tryFire(bell.player,bell.player.getMainHandItem());
            h.assertTrue(bell.count(0,BASIC)==1,"One spawned spirit bullet, no cue on cooldown rejection");
            tether.setPos(bell.player.position().add(0,.85,2));
            var aim=FirstGroupSkills.node(bell.role,4);
            var data=PlayerDataProvider.get(bell.player);data.setSkillLevel(aim.getId(),1);data.setMana(700);
            AbilityRuntime.request(bell.player,data,aim);AbilityRuntime.cancel(bell.player);
            h.assertTrue(bell.count(4,AIM)==1&&bell.count(4,STOP)==1&&bell.count(4,CAST)==0&&data.getMana()==700,
                    "Cancelled beacon preview has no release or mana cost");
            cast(war,2);cast(war,3);
            var parry=cast(war,4);parry.skill.incomingMultiplier(parry);
            cast(war,5);cast(war,7);cast(emptyParry,4);
            cast(bell,2);cast(bell,3);cast(bell,4);cast(courier,5);cast(bell,6);cast(bell,7);
            var abort=cast(cancelled,6);abort.cancelCast();
            cast(jumper,6);
            h.assertTrue(cancelled.count(6,HOLD)==1&&cancelled.count(6,STOP)==1&&cancelled.count(6,CAST)==0,"Cancelled chorus only recovers");
        } catch (Throwable e) {clean.run();throw new IllegalStateException(e);}

        boolean[] done={false};
        for(int t=1;t<=43;t++){
            int tick=t;
            h.runAfterDelay(t,()->{
                if(done[0])return;
                try {
                    if(jumper.count(6,FINISH)==0)jumper.player.setPos(jumper.player.position().add(jumper.player.getDeltaMovement()));
                    for(var a:actors)AbilityRuntime.tick(a.player);
                    if(tick!=43)return;
                    h.assertTrue(war.count(2,CAST)==1&&war.count(5,CAST)==1&&war.count(7,CAST)==1,"Distinct war release cues");
                    h.assertTrue(war.count(3,MOVE)>0&&war.count(3,FINISH)==1,"Pursuit starts and actually stops once");
                    h.assertTrue(war.count(4,FOLLOW)==1&&emptyParry.count(4,FOLLOW)==0,"Only a hit during brace produces a counter cue");
                    h.assertTrue(jumper.count(6,MOVE)>1&&jumper.count(6,FINISH)==1,"Leap lands with exactly one impact cue after flight");
                    h.assertTrue(bell.count(2,CAST)==1&&bell.count(3,CAST)==1&&bell.count(4,CAST)==1&&bell.count(7,CAST)==1,
                            "Crane, tether, beacon and shelter each release once");
                    h.assertTrue(courier.count(5,MOVE)>0&&courier.count(5,FINISH)==1,"Cloud escort applies the travel and arrival poses once");
                    h.assertTrue(bell.count(6,HOLD)>1&&bell.count(6,CAST)==1,"Actual chorus windup progress and one release");
                    for(var a:actors)for(var p:a.cues)h.assertTrue(p.valid()&&AbilityVfxGeometry.build(p,.5f,false).isEmpty(),"No presentation cue becomes a world mesh");
                    done[0]=true;clean.run();
                    System.out.println("[MYRIAD-WAR-SPIRIT-HANDS] PASS: 18 actions, shared grip, private cues, valid attacks, movement and cancellation");
                    h.succeed();
                }catch(Throwable e){done[0]=true;clean.run();throw e;}
            });
        }
    }

    private static AbilityRuntime.Cast cast(Actor actor,int index) {
        var skill=FirstGroupSkills.node(actor.role,index);
        var c=AbilityRuntime.context(actor.player,skill);
        c.point=actor.player.position().add(0,0,5);
        skill.release(c);skill.present(c);return c;
    }

    private static Actor actor(GameTestHelper h,String role,int y) {
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        List<AbilityVfxPayload> cues=new ArrayList<>();
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){
                if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AbilityVfxPayload cue
                        &&cue.owner()==p.getId()&&WarSpiritHandCues.owns(cue))cues.add(cue);
            }
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        PlayerDataProvider.get(p).setCharacterId(role);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,y,11))));
        p.setNoGravity(true);p.setYRot(0);p.setXRot(0);
        var weapon=new ItemStack(role.equals("chixiao")?ModItems.CHIXIAO_BLADE.get():ModItems.YUNSHENG_BELL.get());
        weapon.set(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        p.setItemSlot(EquipmentSlot.MAINHAND,weapon);
        return new Actor(p,role,cues);
    }

    private static void verifyPoses(GameTestHelper h) {
        var crown=bellPartCenter("jade_loop_joint_2");
        var shell=bellPartCenter("cap_plate");
        for(boolean war:new boolean[]{true,false}) {
            var motion=new WarSpiritHandMotion(war);
            var hits=new HashSet<Vec3>();
            Vec3 opening=null;
            for(int i=0;i<3;i++){
                motion.cue(0,BASIC,12*i,9,13);hits.add(motion.pose(12*i+3.1).grip());
                if(i==0)opening=motion.pose(3.1).grip();
                verifyGrip(h,motion,war,12*i,crown,shell);
            }
            h.assertTrue(hits.size()==3,"Three separate attacks per role");
            motion.cue(0,BASIC,85,9,13);
            h.assertTrue(motion.pose(88.1).grip().distanceToSqr(opening)<1e-12,"Expired combo reopens with the first attack");
            var skills=new HashSet<Vec3>();
            for(int i=2;i<=7;i++){
                motion.reset();double begin=0;
                if(war&&i==3||!war&&i==5){motion.cue(i,MOVE,0,7,0);begin=2;motion.cue(i,FINISH,begin,7,0);}
                else if(war&&i==4){motion.cue(i,HOLD,0,20,0);begin=2;motion.cue(i,FOLLOW,begin,8,0);}
                else if(war&&i==6){motion.cue(i,MOVE,0,7,.8);begin=2;motion.cue(i,FINISH,begin,7,0);}
                else motion.cue(i,CAST,0,10,0);
                verifyGrip(h,motion,war,begin,crown,shell);
                var grip=motion.pose(begin+2).grip();skills.add(grip);
                h.assertTrue(grip.y<-.3&&grip.z<-.65,"Cast stays low and in front of the camera");
                motion.cancel(i,begin+3);
                var idle=new WarSpiritHandMotion(war).pose(0);
                h.assertTrue(motion.pose(begin+9).equals(idle),"Cancellation restores the idle grip");
            }
            h.assertTrue(skills.size()==6,"All six casts have their own primary movement");
        }
    }

    private static void verifyGrip(GameTestHelper h,WarSpiritHandMotion motion,boolean war,double start,Vector3f crown,Vector3f shell){
        for(boolean left:new boolean[]{true,false})for(boolean slim:new boolean[]{true,false})for(int frame=0;frame<32;frame++){
            double now=start+frame*.25;
            var pose=motion.pose(now);
            var item=WarSpiritHandRig.item(motion,now,war,left,.15f);
            var arm=WarSpiritHandRig.arm(pose,item,war,left,false,slim,.15f);
            var palm=arm.transformPosition(new Vector3f((left?1:-1)*(slim?.5f:1f)/16,8f/16,0));
            var grip=item.transformPosition(war?new Vector3f():new Vector3f(crown));
            h.assertTrue(item.determinant3x3()>.2f&&grip.distance(palm)<1e-5,
                    "Both hands and sleeve widths keep the native grip in the palm");
            if(!war)h.assertTrue(item.transformPosition(new Vector3f(shell)).y<grip.y-.20f,
                    "The shell hangs beneath the top grip throughout every pose");
        }
    }

    private static Vector3f bellPartCenter(String name){
        try(var file=WarSpiritHandChecks.class.getResourceAsStream("/assets/myriad/models/item/yunsheng_bell.json")){
            var elements=JsonParser.parseReader(new InputStreamReader(file,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("elements");
            for(var element:elements){
                var e=element.getAsJsonObject();if(!name.equals(e.get("name").getAsString()))continue;
                var min=e.getAsJsonArray("from");var max=e.getAsJsonArray("to");
                return new Vector3f((min.get(0).getAsFloat()+max.get(0).getAsFloat()-16)/32,
                        (min.get(1).getAsFloat()+max.get(1).getAsFloat()-16)/32,
                        (min.get(2).getAsFloat()+max.get(2).getAsFloat()-16)/32);
            }
        }catch(IOException e){throw new IllegalStateException(e);}
        throw new IllegalStateException("Missing bell model part: "+name);
    }
}
