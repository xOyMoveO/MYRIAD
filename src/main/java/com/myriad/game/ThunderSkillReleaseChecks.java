package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.ThunderSkillViewPayload;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Actual selected skill release, not direct calls that bypass costs/charge/target validation. */
final class ThunderSkillReleaseChecks {
    private record Fixture(ServerPlayer caster,List<LivingEntity> hits,List<LivingEntity> excluded,List<ServerPlayer> players,
                           List<ThunderSkillViewPayload> cues,Map<BlockPos,BlockState> blocks,int index,float damage){}
    static void verify(GameTestHelper h){
        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        var cues=new ArrayList<ThunderSkillViewPayload>();var players=new ArrayList<ServerPlayer>();
        var p=player(h,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(110,65,40))),Faction.XUANXIAO,players,cues,false);
        var floor=BlockPos.containing(p.position()).below();var saved=new HashMap<BlockPos,BlockState>();
        try{
            for(int x=-2;x<=2;x++)for(int z=-1;z<=12;z++)for(int y=0;y<4;y++)set(h,saved,floor.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
            var d=PlayerDataProvider.get(p);d.setCharacterId("tianxing");d.setLevel(20);
            for(int index:new int[]{2,3,4,5,7}){
                reset(p,cues);var skill=FirstGroupSkills.node("tianxing",index);d.setSkillLevel(skill.getId(),1);d.setCooldown(skill.getId(),0);d.setMana(700);
                p.setPos(Vec3.atBottomCenterOf(floor.above()));p.setYRot(0);p.setXRot(index==2?45:0);float hp=p.getHealth();
                SkillSystem.cast(p,skill.slot());
                int action=switch(index){case 2->GROUND;case 3->ARC_READY;case 4->GUARD;case 5->SLASH_READY;default->JUDGMENT_READY;};
                h.assertTrue(cues.size()==1&&cues.getFirst().action()==action,"Each real skill starts its own confirmed gesture: "+index);
                h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())==skill.getCooldownTicks(1),"One normal mana/CD payment: "+index);
                h.assertTrue(p.getHealth()==hp,"Presentation never damages its owner");
                cues.clear();SkillSystem.cast(p,skill.slot());h.assertTrue(cues.isEmpty(),"Rejected repeat has no new gesture");
            }
            reset(p,cues);var nine=FirstGroupSkills.node("tianxing",6);d.setSkillLevel(nine.getId(),1);d.setCooldown(nine.getId(),0);d.setMana(700);
            p.setPos(Vec3.atBottomCenterOf(floor.above()));p.setYRot(0);p.setXRot(45);
            SkillSystem.cast(p,nine.slot());
            h.assertTrue(d.getPlacementType()==106&&cues.isEmpty()&&d.getMana()==700,"Nine Thunder aim uses synced placement only, with no cast/payment");
            AbilityRuntime.cancel(p);h.assertTrue(d.getPlacementType()==0&&cues.isEmpty()&&d.getMana()==700,"Cancel removes aiming without payment");
            SkillSystem.cast(p,nine.slot());AbilityRuntime.confirm(p);
            h.assertTrue(cues.size()==1&&cues.getFirst().action()==NINE_FIRE&&d.getMana()==700-nine.getManaCost(),"Only valid confirmation triggers the left-hand release");
            reset(p,cues);var arc=FirstGroupSkills.node("tianxing",3);d.setSkillLevel(arc.getId(),1);d.setCooldown(arc.getId(),0);d.setMana(0);
            SkillSystem.cast(p,arc.slot());h.assertTrue(cues.isEmpty(),"No mana means no fake gesture");
            d.setMana(700);d.setBuff("g1_stun",20);SkillSystem.cast(p,arc.slot());h.assertTrue(cues.isEmpty(),"Stun prevents cast gesture");
        }finally{AbilityRuntime.clear(p);players.forEach(Entity::discard);saved.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        System.out.println("[MYRIAD-THUNDER-SKILL-RELEASE] PASS: six actual skill cues, preserved fees/CD, preview/confirm/cancel, rejected casts and no self-damage");
    }
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int i=0;i<9;i++){
                var f=fixture(h,i);fixtures.add(f);var d=PlayerDataProvider.get(f.caster);
                var skill=FirstGroupSkills.node("tianxing",slash(i)?5:7);SkillSystem.cast(f.caster,skill.slot());
                h.assertTrue(SkillChargeSystem.active(f.caster)&&f.cues.size()==1&&f.cues.getFirst().action()==(slash(i)?SLASH_READY:JUDGMENT_READY),"Real charge begins before damage");
                h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())>0,"Async cast retains original fee/CD");
            }
        }catch(Throwable failure){fixtures.forEach(f->cleanup(h,f));throw failure;}
        finally{TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        boolean[] done={false};
        for(int t=1;t<=30;t++){int elapsed=t;h.runAfterDelay(t,()->{
            if(done[0])return;
            try{
                if(elapsed==3)for(int i:new int[]{3,6})PlayerDataProvider.get(fixtures.get(i).caster).setBuff("g1_stun",40);
                // The final heading, not the initial heading, defines Judgment.
                if(elapsed==10)fixtures.get(5).caster.setYRot(-90);
                for(var f:fixtures)AbilityRuntime.tick(f.caster);
                for(var f:fixtures){
                    int windup=slash(f.index)?10:12;
                    if(elapsed<windup)for(var target:f.hits)h.assertTrue(target.getHealth()==1000,"No hit before windup completes");
                    if(elapsed==12&&(f.index==4||f.index==5))for(var target:f.hits)
                        h.assertTrue(PlayerDataProvider.get((ServerPlayer)target).getBuff("g1_stun")==16,"All fan targets receive the full original 16-tick stun: case="+f.index+" target="+target.position().subtract(f.caster.position())+" hp="+target.getHealth()+" stun="+PlayerDataProvider.get((ServerPlayer)target).getBuff("g1_stun"));
                    if(elapsed==11&&(f.index==0||f.index==1)){
                        h.assertTrue(PlayerDataProvider.get((ServerPlayer)f.hits.getFirst()).getBuff("g1_root")==12,"First pierced hero retains the 12-tick root");
                        h.assertTrue(PlayerDataProvider.get((ServerPlayer)f.hits.get(1)).getBuff("g1_root")==0,"Root does not propagate to later heroes");
                    }
                    if(f.index==0||f.index==1){
                        var far=f.hits.getLast();
                        if(elapsed<15)h.assertTrue(far.getHealth()==1000,"80 blocks/s: far target is not hit before five travelling ticks");
                        if(elapsed==15)h.assertTrue(Math.abs(far.getHealth()-(1000-f.damage))<.03,"20-block Slash reaches the 19.5-block target on travelling tick five");
                    }
                }
                if(elapsed==30){
                    for(var f:fixtures){
                        boolean success=f.index==0||f.index==1||f.index==4||f.index==5;
                        for(var target:f.hits)h.assertTrue(Math.abs(target.getHealth()-(1000-(success?f.damage:0)))<.03,"Exactly one rank/S hit, travel/pierce/wall/interruption: case="+f.index+" hp="+target.getHealth());
                        for(var target:f.excluded)h.assertTrue(Math.abs(target.getHealth()-1000)<.03,"Friends/buildings/range/back/angle excluded: case="+f.index+" hp="+target.getHealth());
                        h.assertTrue(f.cues.stream().anyMatch(c->c.action()==(f.index==3||f.index==6?CANCEL:slash(f.index)?SLASH_FIRE:JUDGMENT_FIRE)),"Completion or cancellation is server confirmed");
                        if(f.index==3||f.index==6)h.assertTrue(f.cues.stream().noneMatch(c->c.action()==SLASH_FIRE||c.action()==JUDGMENT_FIRE),"Interrupted charge never sends a release cue");
                    }
                    done[0]=true;fixtures.forEach(f->cleanup(h,f));
                    System.out.println("[MYRIAD-THUNDER-SKILL-COMBAT] PASS: actual 10/12-tick windups, moving piercing slash once per target, ranks/S, 120-degree 8-block final-heading fan/all-target stun, allies/towers/LOS/obstacle/range, cancellation cue");
                }
            }catch(Throwable failure){done[0]=true;fixtures.forEach(f->cleanup(h,f));throw failure;}
        });}
    }
    private static Fixture fixture(GameTestHelper h,int index){
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(140,160+index*10,40)));
        var players=new ArrayList<ServerPlayer>();var cues=new ArrayList<ThunderSkillViewPayload>();var saved=new HashMap<BlockPos,BlockState>();
        var caster=player(h,origin,Faction.XUANXIAO,players,cues,false);var d=PlayerDataProvider.get(caster);d.setCharacterId("tianxing");d.setLevel(20);d.setMana(700);
        int rank=index==1?7:index==5?3:1;float strength=index==1||index==5?100:0;
        var skill=FirstGroupSkills.node("tianxing",slash(index)?5:7);d.setSkillLevel(skill.getId(),rank);
        caster.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(strength);caster.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);caster.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
        var hits=new ArrayList<LivingEntity>();var excluded=new ArrayList<LivingEntity>();
        List<Vec3> hitPositions=slash(index)?List.of(new Vec3(0,0,4),new Vec3(0,0,8),new Vec3(.8,0,11),new Vec3(0,0,19.5)):
                List.of(new Vec3(0,0,4),new Vec3(-6*Math.sin(Math.toRadians(55)),0,6*Math.cos(Math.toRadians(55))),new Vec3(8*Math.sin(Math.PI/3),0,4));
        for(var delta:hitPositions)hits.add(player(h,origin.add(rotate(delta,index==5)),Faction.YOUMING,players,cues,true));
        List<Vec3> excludedPositions=slash(index)?List.of(new Vec3(2.5,0,6),new Vec3(0,0,22)):
                List.of(new Vec3(0,0,-3),new Vec3(0,0,8.2),new Vec3(5*Math.sin(Math.toRadians(61)),0,5*Math.cos(Math.toRadians(61))));
        for(var delta:excludedPositions)excluded.add(player(h,origin.add(rotate(delta,index==5)),Faction.YOUMING,players,cues,true));
        excluded.add(player(h,origin.add(rotate(new Vec3(0,0,6),index==5)),Faction.XUANXIAO,players,cues,true));
        var tower=h.spawn(ModEntities.DEFENSE_TOWER.get(),new BlockPos(2,6,2));tower.setPos(origin.add(rotate(new Vec3(.5,0,5.5),index==5)));tower.setHealth(1000);excluded.add(tower);
        if(index==2||index==7)for(int x=-9;x<=9;x++)for(int y=0;y<4;y++)set(h,saved,BlockPos.containing(origin.add(x,y,2)),Blocks.STONE.defaultBlockState());
        if(index==8){var wall=player(h,origin.add(0,0,2),Faction.YOUMING,players,cues,false);var wd=PlayerDataProvider.get(wall);wd.setCharacterId("sulan");com.myriad.character.skills.sulan.FengQiangSkill.createWall(wall,wd,80,false);}
        float damage=slash(index)?95+24*(rank-1)+.95f*strength:200+80*(rank-1)+1.5f*strength;
        return new Fixture(caster,hits,excluded,players,cues,saved,index,damage);
    }
    private static Vec3 rotate(Vec3 d,boolean turn){return turn?new Vec3(d.z,d.y,-d.x):d;}
    private static boolean slash(int index){return index<4||index==8;}
    private static ServerPlayer player(GameTestHelper h,Vec3 at,Faction faction,List<ServerPlayer> list,List<ThunderSkillViewPayload> cues,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp&&cp.payload() instanceof ThunderSkillViewPayload cue)cues.add(cue);}
        };p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setFaction(faction);d.setModMaxHealth(1000);d.setModHealthInitialized(true);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);p.getAttribute(ModAttributes.TENACITY).setBaseValue(0);
        if(add)h.getLevel().addNewPlayer(p);list.add(p);return p;
    }
    private static void reset(ServerPlayer p,List<ThunderSkillViewPayload> cues){AbilityRuntime.clear(p);var d=PlayerDataProvider.get(p);for(String id:new ArrayList<>(d.getSkillLevels().keySet()))d.setSkillLevel(id,0);cues.clear();}
    private static void set(GameTestHelper h,Map<BlockPos,BlockState> saved,BlockPos at,BlockState state){saved.putIfAbsent(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,state);}
    private static void cleanup(GameTestHelper h,Fixture f){AbilityRuntime.clear(f.caster);for(var p:f.players){AbilityRuntime.clear(p);com.myriad.character.skills.sulan.FengQiangSkill.clearFor(p.getUUID());h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}f.excluded.stream().filter(t->!(t instanceof ServerPlayer)).forEach(Entity::discard);f.blocks.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));}
}
