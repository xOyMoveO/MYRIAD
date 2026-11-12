package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.network.NightSkillViewPayload;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

final class NightSkillReleaseChecks {
    static void verify(GameTestHelper h){
        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var cues=new ArrayList<NightSkillViewPayload>();
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof NightSkillViewPayload cue)cues.add(cue);
            }
        };p.connection.markClientLoaded();p.setNoGravity(true);
        var floor=h.absolutePos(new BlockPos(74,65,40));var saved=new HashMap<BlockPos,BlockState>();
        try{
            for(int x=-2;x<=2;x++)for(int z=-1;z<=9;z++)for(int y=0;y<=3;y++){
                var at=floor.offset(x,y,z);saved.put(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
            }
            var d=PlayerDataProvider.get(p);d.setCharacterId("yingxiao");d.setLevel(20);
            CharacterType.applyInitialAttributes(p,CharacterType.YINGXIAO);FirstGroupGrowth.initialize(p,d);
            int[] nodes={2,3,4,7};int[] actions={0,1,2,4};
            for(int i=0;i<nodes.length;i++){
                reset(p,cues);var skill=FirstGroupSkills.node("yingxiao",nodes[i]);d.setSkillLevel(skill.getId(),1);d.setCooldown(skill.getId(),0);d.setMana(700);
                p.setPos(Vec3.atBottomCenterOf(floor.above()));p.setYRot(0);p.setXRot(0);float hp=p.getHealth();
                SkillSystem.cast(p,skill.slot());
                h.assertTrue(cues.size()==1&&cues.getFirst().action()==actions[i],"Real selected skill release sends its distinct owner pose once");
                h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())>0,"Cue does not alter or duplicate normal mana/CD payment");
                h.assertTrue(p.getHealth()==hp,"Cosmetic shield tap and all gestures do not self-damage");
                cues.clear();SkillSystem.cast(p,skill.slot());h.assertTrue(cues.isEmpty(),"Rejected repeat cast does not restart the hand animation");
            }
            reset(p,cues);var returning=FirstGroupSkills.node("yingxiao",5);d.setSkillLevel(returning.getId(),1);d.setCooldown(returning.getId(),0);d.setMana(700);
            p.setPos(Vec3.atBottomCenterOf(floor.above()));p.setYRot(0);p.setXRot(45);
            SkillSystem.cast(p,CharacterType.SLOT_E);
            h.assertTrue(cues.isEmpty()&&d.getMana()==700&&d.getPlacementType()!=0,"Entering the point preview does not play a dash gesture");
            AbilityRuntime.cancel(p);h.assertTrue(cues.isEmpty()&&d.getMana()==700,"Cancelled point preview has no gesture/payment");
            SkillSystem.cast(p,CharacterType.SLOT_E);AbilityRuntime.confirm(p);
            h.assertTrue(cues.size()==1&&cues.getFirst().action()==NightSkillViewPayload.RETURN&&d.getMana()==615,"Only confirmed Return landing starts the forward knife pose");
            reset(p,cues);var dagger=FirstGroupSkills.node("yingxiao",2);d.setSkillLevel(dagger.getId(),1);d.setCooldown(dagger.getId(),0);d.setMana(0);
            SkillSystem.cast(p,CharacterType.SLOT_Q);h.assertTrue(cues.isEmpty(),"No-mana failure has no throwing gesture");
        }finally{
            AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);p.discard();saved.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));
            TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);
        }
        System.out.println("[MYRIAD-NIGHT-SKILL-RELEASE] PASS: actual Q/W/E/R release cues, normal payment, no self-damage, no cue for preview/cancel/reject/no mana");
    }
    private static void reset(ServerPlayer p,List<NightSkillViewPayload> cues){
        AbilityRuntime.clear(p);var d=PlayerDataProvider.get(p);for(String id:d.getSkillLevels().keySet())d.setSkillLevel(id,0);
        cues.clear();
    }
}
