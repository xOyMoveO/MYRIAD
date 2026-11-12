package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
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

/** Real selected W: delayed swept flight, one primary, full radial fan-out and exclusions. */
final class ThunderChainChecks {
    private record Fixture(ServerPlayer caster,LivingEntity primary,List<ServerPlayer> chained,
                           List<LivingEntity> excluded,List<ServerPlayer> players,
                           Map<BlockPos,BlockState> blocks,float damage){}
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();
        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int i=0;i<5;i++){
                var f=fixture(h,i);fixtures.add(f);var d=PlayerDataProvider.get(f.caster);
                SkillSystem.cast(f.caster,CharacterType.SLOT_W);
                h.assertTrue(d.getMana()==625&&d.getCooldown("g1_tianxing_n2")==200,"Thunder chain keeps 75 mana and 10s CD: mana="+d.getMana()+" cd="+d.getCooldown("g1_tianxing_n2"));
                h.assertTrue(SkillChargeSystem.active(f.caster),"Real W starts the original six-tick charge");
            }
        }catch(Throwable failure){System.err.println("[MYRIAD-THUNDER-CHAIN] fixture setup failed: "+failure);fixtures.forEach(f->cleanup(h,f));throw failure;}
        finally{TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        boolean[] done={false};
        for(int tick=1;tick<=25;tick++){
            int elapsed=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    if(elapsed==3)PlayerDataProvider.get(fixtures.get(4).caster).setBuff("g1_stun",40);
                    for(var f:fixtures)AbilityRuntime.tick(f.caster);
                    if(elapsed<6)for(int i=0;i<fixtures.size();i++)h.assertTrue(fixtures.get(i).primary.getHealth()==(i==2?10:1000),"No hit before charge completes");
                    if(elapsed==7)for(int i=0;i<2;i++)h.assertTrue(fixtures.get(i).primary.getHealth()==1000,"50 blocks/s: first moving tick has not reached the 5-block target");
                    if(elapsed==8)for(int i=0;i<2;i++)h.assertTrue(Math.abs(fixtures.get(i).primary.getHealth()-(1000-fixtures.get(i).damage))<.02,"50 blocks/s reaches the 5-block target on moving tick two");
                    if(elapsed==25){
                        for(int i=0;i<fixtures.size();i++){
                            var f=fixtures.get(i);boolean hit=i<3;
                            float primaryBefore=i==2?10:1000;
                            h.assertTrue(Math.abs(f.primary.getHealth()-(hit?Math.max(0,primaryBefore-f.damage):primaryBefore))<.02,"Main target hit once, rank/S formula, blocked and interrupted casts: case="+i+" hp="+f.primary.getHealth());
                            for(var t:f.chained)h.assertTrue(Math.abs(t.getHealth()-(1000-(hit?f.damage*.35f:0)))<.02,"Every eligible side/vertical/boundary target receives exactly 35% once: case="+i+" hp="+t.getHealth());
                            for(var t:f.excluded)h.assertTrue(Math.abs(t.getHealth()-1000)<.02,"Friends, towers, outside radius, second-hop and blocked targets are untouched: case="+i+" hp="+t.getHealth());
                        }
                        var skill=FirstGroupSkills.node("tianxing",3);
                        h.assertTrue(skill.displayName().equals("雷电劫")&&AbilityFormulaText.evaluate(skill.description(),5,100).contains("69"),"Current UI resolves both main and 35% chain formulas");
                        done[0]=true;fixtures.forEach(f->cleanup(h,f));
                        System.out.println("[MYRIAD-THUNDER-CHAIN] PASS: actual W, 6-tick windup, ranks/S, all 4-block secondaries incl. vertical/boundary, lethal primary, no piercing/recursion, allies/buildings/LOS/range exclusions, wall and stun");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(f->cleanup(h,f));throw failure;}
            });
        }
    }
    private static Fixture fixture(GameTestHelper h,int index){
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(95,100+index*12,40)));
        var players=new ArrayList<ServerPlayer>();var caster=player(h,origin,Faction.XUANXIAO,players,false);
        var d=PlayerDataProvider.get(caster);d.setCharacterId("tianxing");d.setLevel(20);d.setMana(700);
        int rank=index==1?5:1;float strength=index==1?100:0;
        d.setSkillLevel("g1_tianxing_n2",rank);caster.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(strength);
        caster.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);caster.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
        Vec3 contact=origin.add(0,0,5);LivingEntity primary;
        if(index==2){
            var pig=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new BlockPos(2,6,2));
            pig.setPos(contact);pig.setNoAi(true);pig.setNoGravity(true);pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);primary=pig;
        }else primary=player(h,contact,Faction.YOUMING,players,true);
        var chained=new ArrayList<ServerPlayer>();
        // More than two targets, including an out-of-plane and exact four-block target.
        for(Vec3 delta:List.of(new Vec3(2,0,0),new Vec3(2,0,2),new Vec3(0,3,0),new Vec3(0,0,4)))
            chained.add(player(h,contact.add(delta),Faction.YOUMING,players,true));
        var excluded=new ArrayList<LivingEntity>();
        excluded.add(player(h,contact.add(-2,0,0),Faction.XUANXIAO,players,true));
        excluded.add(player(h,contact.add(4.1,0,0),Faction.YOUMING,players,true));
        excluded.add(player(h,contact.add(5.5,0,0),Faction.YOUMING,players,true)); // Near a secondary, not the primary.
        excluded.add(player(h,contact.add(-3,0,-2),Faction.YOUMING,players,true));
        var tower=h.spawn(ModEntities.DEFENSE_TOWER.get(),new BlockPos(2,6,2));tower.setPos(contact.add(3,0,1));tower.setHealth(1000);excluded.add(tower);
        var blocks=new HashMap<BlockPos,BlockState>();
        // Between the primary and the last enemy, while that enemy remains visible from the caster.
        for(int y=0;y<3;y++)block(h,blocks,BlockPos.containing(contact.add(-2,y,-1)));
        if(index==3)for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)block(h,blocks,BlockPos.containing(origin.add(x,y,2)));
        if(index==2)primary.setHealth(10);
        return new Fixture(caster,primary,chained,excluded,players,blocks,60+18*(rank-1)+.65f*strength);
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 at,Faction faction,List<ServerPlayer> list,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection); // Entity spawn hooks query channel attributes.
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setFaction(faction);d.setModMaxHealth(1000);d.setModHealthInitialized(true);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
        if(add)h.getLevel().addNewPlayer(p);list.add(p);return p;
    }
    private static void block(GameTestHelper h,Map<BlockPos,BlockState> saved,BlockPos at){saved.putIfAbsent(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());}
    private static void cleanup(GameTestHelper h,Fixture f){
        AbilityRuntime.clear(f.caster);for(var p:f.players){AbilityRuntime.clear(p);h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
        f.excluded.stream().filter(t->!(t instanceof ServerPlayer)).forEach(Entity::discard);
        if(!(f.primary instanceof ServerPlayer))f.primary.discard();
        f.blocks.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));
    }
}
