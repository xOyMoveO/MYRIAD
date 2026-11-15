package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.character.skills.rivet.*;
import com.myriad.combat.*;
import com.myriad.network.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Actual selection, point spending, casts, damage, movement, shields and production packets. */
final class RivetChecks {
    private static final int CASES=71;
    private static final class Fixture {
        ServerLevel level;ServerPlayer p,ally,other;BlockPos anchorBlock;LivingEntity target,rear;Vec3 origin;
        int index;double initialZ,maxZ;float before,shieldAbsorbed,npcAbsorbed,firstBasicLoss,plainBasicLoss;int maxRoot,previousRoot,continuousRoot,maxContinuousRoot,continuousControl,maxContinuousControl;boolean sawRoot,sawBonus;final List<Integer> roots=new ArrayList<>();
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final List<CustomPacketPayload> packets=new ArrayList<>();
        final Map<UUID,List<CustomPacketPayload>> mailboxes=new HashMap<>();
    }
    private RivetChecks() {}
    static void run(GameTestHelper h) {
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled(),nc=TrainingMode.isNoCooldown(),nm=TrainingMode.isNoMana();
        var previous=GameManager.get();var fixtures=new ArrayList<Fixture>();boolean[] done={false};
        @SuppressWarnings("unchecked") var fountains=(Map<Faction,BlockPos>)field(LaneSystem.class,null,"FOUNTAIN_POINTS");
        var oldFountains=new EnumMap<Faction,BlockPos>(Faction.class);oldFountains.putAll(fountains);
        Runnable cleanup=()->{fixtures.forEach(RivetChecks::cleanup);fountains.clear();fountains.putAll(oldFountains);set(GameManager.class,null,"instance",previous);
            ModState.setEnabled(enabled);TrainingMode.setEnabled(training);TrainingMode.setNoCooldown(nc);TrainingMode.setNoMana(nm);};
        try {
            ModState.setEnabled(true);TrainingMode.setEnabled(false);TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            RivetVfxChecks.verify(h);
            GameManager.create();var manager=GameManager.get();manager.setServer(h.getLevel().getServer());
            for(int col=0;col<8;col++)CombatChunks.prepare(h.getLevel(),h.absolutePos(new BlockPos(-80+col*20,12,-30)));
            CombatChunks.prepare(h.getLevel(),h.absolutePos(new BlockPos(1024,12,-30)));
            var prep=fixture(h,80);fixtures.add(prep);
            var noWall=fixture(h,81);fixtures.add(noWall);
            @SuppressWarnings("unchecked") var factions=(Map<UUID,Faction>)field(GameManager.class,manager,"factions");
            factions.put(prep.p.getUUID(),Faction.XUANXIAO);factions.put(noWall.p.getUUID(),Faction.YOUMING);
            fountains.put(Faction.XUANXIAO,BlockPos.containing(prep.origin).below());
            fountains.put(Faction.YOUMING,BlockPos.containing(noWall.origin).below());
            @SuppressWarnings("unchecked") var list=(List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,h.getLevel().getServer().getPlayerList(),"players");list.add(prep.p);list.add(noWall.p);
            PlayerDataProvider.resetForHostSession(prep.p);PlayerDataProvider.resetForHostSession(noWall.p);
            PlayerDataProvider.get(prep.p).setFaction(Faction.XUANXIAO);PlayerDataProvider.get(noWall.p).setFaction(Faction.YOUMING);
            set(GameManager.class,manager,"phase",GameManager.Phase.PICKING);set(GameManager.class,manager,"pickIndex",0);
            @SuppressWarnings("unchecked") var order=(List<UUID>)field(GameManager.class,manager,"pickOrder");order.add(noWall.p.getUUID());order.add(prep.p.getUUID());
            prep.p.setPos(prep.origin.add(-4,0,0));noWall.p.setPos(noWall.origin.add(-4,0,0));
            NetworkSetup.handleChooseCharacter(noWall.p,new NetworkSetup.ChooseCharacterPayload("shuo"));
            manager.onPlayerQuit(noWall.p);list.remove(noWall.p);h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(noWall.p.getUUID());h.getLevel().players().remove(noWall.p);
            NetworkSetup.handleChooseCharacter(prep.p,new NetworkSetup.ChooseCharacterPayload("rivet"));
            h.assertTrue(prep.p.position().distanceTo(prep.origin)<.01&&noWall.p.position().distanceTo(noWall.origin)>3,"The online final pick immediately arrives while an offline teammate is queued for its own fountain");
            h.assertTrue(prep.packets.stream().filter(NetworkSetup.PickStatePayload.class::isInstance).map(NetworkSetup.PickStatePayload.class::cast).anyMatch(p->p.state()==1),"Final pick immediately sends the actual close-screen packet");
            h.assertTrue(manager.getPhase()==GameManager.Phase.PICKING&&manager.preparationFor(prep.p),"Fountain preparation reuses PICKING and only qualifies the selected Rivet");
            var pd=PlayerDataProvider.get(prep.p);h.assertTrue(pd.getSkillPoints()==1&&pd.skillLevel(node(3).getId())==0&&pd.getModMaxHealth()==700,"Free base W never consumes the opening point");
            PlayerDataProvider.upgradeSkill(prep.p,node(2).getId());
            h.assertTrue(pd.skillLevel(node(2).getId())==1&&pd.getSkillPoints()==0,"The opening point can learn Q while base W remains free");
            wall(prep,4,3);SkillSystem.cast(prep.p,1);float prepMana=pd.getMana();
            h.assertTrue(TacticalAnchor.anchor(prep.p)!=null&&prepMana==700,"Preparation base W places a real side-wall anchor without mana");
            SkillSystem.cast(prep.p,0);SkillSystem.cast(prep.p,2);SkillSystem.cast(prep.p,3);
            SkillSystem.cast(noWall.p,1);
            h.assertTrue(pd.getMana()==prepMana&&pd.getCooldown(node(2).getId())==0&&TacticalAnchor.anchor(noWall.p)==null,"Preparation allows only Rivet W and does not require anyone to find a wall");
            long anchorUntil=TacticalAnchor.anchor(prep.p).until();long deadline=(Long)field(GameManager.class,manager,"pickCountdownEndTick");
            h.assertTrue(deadline-h.getLevel().getGameTime()==200,"The existing preparation countdown is exactly ten seconds, once");
            for(int n=1;n<=205;n++){final int tick=n;h.runAfterDelay(n,()->{
                if(done[0])return;
                try {
                    if(tick%20==0){for(int col=0;col<8;col++)CombatChunks.hold(h.getLevel(),h.absolutePos(new BlockPos(-80+col*20,12,-30)));CombatChunks.hold(h.getLevel(),h.absolutePos(new BlockPos(1024,12,-30)));}
                    AbilityRuntime.tick(prep.p);manager.tick(h.getLevel().getServer(),h.getLevel().getGameTime());
                    if(tick==100){list.add(noWall.p);h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(noWall.p.getUUID(),noWall.p);h.getLevel().players().add(noWall.p);manager.onPlayerJoin(noWall.p);h.assertTrue(noWall.p.position().distanceTo(noWall.origin)<.01&&(Long)field(GameManager.class,manager,"pickCountdownEndTick")==deadline&&noWall.packets.stream().filter(NetworkSetup.PickStatePayload.class::isInstance).map(NetworkSetup.PickStatePayload.class::cast).anyMatch(packet->packet.state()==1),"A teammate reconnecting during preparation receives its missed fountain transfer and closed draft without restarting the timer");}
                    if(tick==100)h.assertTrue(!TacticalAnchor.anchorReady(prep.p)&&manager.getPhase()==GameManager.Phase.PICKING&&TacticalAnchor.anchor(prep.p).until()==anchorUntil,"Preparation does not restart the countdown or anchor lifetime");
                    if(tick==202){
                        h.assertTrue(manager.getPhase()==GameManager.Phase.PLAYING&&TacticalAnchor.anchorReady(prep.p)&&TacticalAnchor.anchor(prep.p)!=null&&TacticalAnchor.anchor(prep.p).until()==anchorUntil,"Formal start retains the existing anchor and its remaining lifetime");
                        pd.setCooldown(node(3).getId(),0);SkillSystem.cast(prep.p,1);
                        h.assertTrue(pd.getMana()<=prepMana-30&&pd.getMana()>=prepMana-31,"Formal base W now pays its normal 30 mana");
                        list.remove(prep.p);list.remove(noWall.p);cleanup(prep);cleanup(noWall);fixtures.clear();
                        for(int i=0;i<CASES;i++){var f=fixture(h,i);fixtures.add(f);setup(h,f,manager);}

                    }
                }catch(Throwable failure){failure.printStackTrace();done[0]=true;cleanup.run();throw new IllegalStateException("Rivet test: "+failure,failure);}
            });}
            runScenes(h,fixtures,done,cleanup);
        }catch(Throwable failure){failure.printStackTrace();done[0]=true;cleanup.run();throw new IllegalStateException("Rivet test: "+failure,failure);}
    }
    private static void setup(GameTestHelper h,Fixture f,GameManager manager) {
        int i=f.index;var d=PlayerDataProvider.get(f.p);
        @SuppressWarnings("unchecked") var factions=(Map<UUID,Faction>)field(GameManager.class,manager,"factions");factions.put(f.p.getUUID(),Faction.XUANXIAO);d.setFaction(Faction.XUANXIAO);
        NetworkSetup.handleChooseCharacter(f.p,new NetworkSetup.ChooseCharacterPayload("rivet"));
        // Ordinary formal selection is exercised above; per-scene initialization uses the same authoritative provider.
        PlayerDataProvider.chooseCharacter(f.p,CharacterType.byId("rivet"));d.setLevel(20);d.refundAllSkillPointsForTraining();
        int passive=i<8?(i&1):i==18||i==38||i==39||i==47||i==52||i==53||i==65||i==68||i==70?1:0;
        int major=i<8?4+((i>>1)&1):Set.of(24,31,32,33,34,48,49,67,68,69).contains(i)?5:4;
        int ultimate=i<8?6+((i>>2)&1):Set.of(20,35,36,37,38,39,50,51,53,55).contains(i)?7:6;
        for(int nodeIndex:new int[]{passive,2,major,ultimate})PlayerDataProvider.upgradeSkill(f.p,node(nodeIndex).getId());
        h.assertTrue(d.skillLevel(node(passive).getId())==1&&d.skillLevel(node(major).getId())==1&&d.skillLevel(node(ultimate).getId())==1,"Normal point spending installs the selected branch combination "+i);
        int points=d.getSkillPoints();PlayerDataProvider.upgradeSkill(f.p,node(1-passive).getId());PlayerDataProvider.upgradeSkill(f.p,node(major==4?5:4).getId());PlayerDataProvider.upgradeSkill(f.p,node(ultimate==6?7:6).getId());
        h.assertTrue(points==d.getSkillPoints(),"All three opposite branches are rejected without spending points: "+i);
        CharacterType.applyInitialAttributes(f.p,CharacterType.byId("rivet"));FirstGroupGrowth.initialize(f.p,d);PlayerHealthSystem.fill(f.p);d.setMana(700);
        h.assertTrue(d.getModMaxHealth()==2790&&f.p.getAttributeValue(ModAttributes.MANA_REGEN)==4,"Level twenty reaches 2790 health and retains four mana per second: "+i);
        f.p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);f.p.setOnGround(true);f.target.setPos(f.origin.add(0,0,2.35));
        if(!Set.of(8,9,10,14,16,26,27,31,32,33,34,35,37,48,49,50,51,55).contains(i))wall(f,4,3);
        if(i==11){clearWall(f,4);block(f,0,0,4,Blocks.STONE);block(f,0,1,4,Blocks.STONE);}
        if(i==12){clearWall(f,4);for(int y=0;y<3;y++){block(f,0,y,4,Blocks.STONE);block(f,1,y,3,Blocks.STONE);}}
        if(i==13){for(int z=0;z<7;z++)block(f,0,2,z,Blocks.STONE);}
        if(i==9||i==10)f.target.setPos(f.origin.add(0,0,2.1));
        if(i==9)block(f,0,0,3,Blocks.STONE_SLAB);
        if(i==10){block(f,0,0,3,Blocks.STONE_STAIRS);f.level.setBlockAndUpdate(BlockPos.containing(f.origin).offset(0,0,3),Blocks.STONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,net.minecraft.core.Direction.SOUTH));}
        if(i==9||i==10)h.assertTrue(f.level.noCollision(f.target,f.target.getBoundingBox()),"Step fixture starts outside every collision shape: "+i);
        if(i==14)f.target.setPos(f.origin.add(1.25,0,1.8));
        if(i==15||i==36){f.rear=dummy(f,f.origin.add(0,0,i==36?4.8:2.9));if(i==36){clearWall(f,4);f.target.setPos(f.origin.add(0,0,2));}}
        if(i==17)ModShieldSystem.grant(f.target,"rivet_test_shield",1000,700);
        if(i==18)f.target.setInvulnerable(true);
        if(i==19){rank(f,2,5);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==20){rank(f,7,3);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==21){rank(f,0,5);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==22){rank(f,4,7);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==23){rank(f,6,3);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==24)rank(f,5,7);
        if(i==25)rank(f,3,5);
        if(i==70){rank(f,1,5);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i>=56&&i<=61)rank(f,3,i-56);
        if(i==63){rank(f,0,5);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);}
        if(i==65){clearWall(f,4);wall(f,5,3);}
        if(Set.of(0,1,2,3,4,5,6,7,17,19,20,21,22,23,24,25,28,29,30,33,34,38,39,40,41,42,43,44,45,46,47,48,49,52,53,54,56,57,58,59,60,61,62,63,65,67,68,69,70).contains(i)){
            if(i==33||i==34||i==48||i==49||i==67||i==68){clearWall(f,4);wall(f,7,3);}
            SkillSystem.cast(f.p,1);h.assertTrue(TacticalAnchor.anchor(f.p)!=null,"Normal W attaches to the aimed side face: "+i);f.anchorBlock=TacticalAnchor.anchor(f.p).block();
        }
        if(i==64||i==66)clearWall(f,4);
        if(i>=64&&i<=66)ActionTime.apply(f.target.getUUID(),f.p.getUUID(),"rivet_push_slow",.25,f.level.getGameTime()+200);
        if(i==67)block(f,0,0,3,Blocks.STONE);
        if(i==68){clearWall(f,4);f.target.setPos(f.origin.add(1.8,0,3.5));for(int z=1;z<=4;z++)for(int y=0;y<3;y++)block(f,3,y,z,Blocks.STONE);}
        if(i==26){f.p.setXRot(-90);SkillSystem.cast(f.p,1);h.assertTrue(TacticalAnchor.anchor(f.p)==null&&d.getMana()==700,"W rejects top/bottom and missing side faces without payment");f.p.setXRot(0);}
        if(i==27){wall(f,9,3);SkillSystem.cast(f.p,1);h.assertTrue(TacticalAnchor.anchor(f.p)==null&&d.getMana()==700,"W rejects a side wall beyond eight blocks without payment");}
        if(i==31||i==32){if(i==32){wall(f,7,3);SkillSystem.cast(f.p,1);wall(f,3,3);}float before=d.getMana();SkillSystem.cast(f.p,2);h.assertTrue(d.getMana()==before&&d.getCooldown(node(5).getId())==0,"Tow without a visible valid path leaves mana and cooldown unchanged: "+i);}
        if(i>=40&&i<=46||i==62||i==63)f.target.setPos(f.origin.add(6,0,0));
        if(Set.of(22,23,44,45,46,54).contains(i)){f.ally=player(h,f,f.origin.add(1,0,0));var ad=PlayerDataProvider.get(f.ally);ad.setCharacterId("shuo");ad.setFaction(Faction.XUANXIAO);factions.put(f.ally.getUUID(),Faction.XUANXIAO);f.level.players().remove(f.ally);f.level.addNewPlayer(f.ally);}
        if(i==0){f.ally=player(h,f,f.origin.add(1,0,-1));f.other=player(h,f,f.origin.add(-1,0,-1));for(var observer:new ServerPlayer[]{f.ally,f.other}){PlayerDataProvider.get(observer).setCharacterId("shuo");var side=observer==f.ally?Faction.XUANXIAO:Faction.YOUMING;PlayerDataProvider.get(observer).setFaction(side);factions.put(observer.getUUID(),side);}}
        if(i==45){f.other=player(h,f,f.origin.add(.5,0,0));PlayerDataProvider.chooseCharacter(f.other,CharacterType.byId("rivet"));var od=PlayerDataProvider.get(f.other);od.setFaction(Faction.XUANXIAO);od.setLevel(20);od.refundAllSkillPointsForTraining();for(int rank=0;rank<2;rank++)PlayerDataProvider.upgradeSkill(f.other,node(4).getId());factions.put(f.other.getUUID(),Faction.XUANXIAO);}
        if(i==39){f.other=player(h,f,f.origin);PlayerDataProvider.chooseCharacter(f.other,CharacterType.byId("rivet"));var od=PlayerDataProvider.get(f.other);od.setFaction(Faction.XUANXIAO);od.setLevel(20);od.refundAllSkillPointsForTraining();PlayerDataProvider.upgradeSkill(f.other,node(2).getId());factions.put(f.other.getUUID(),Faction.XUANXIAO);}
        if(i==52||i==53){f.target.discard();var pos=BlockPos.containing(f.origin.add(0,0,2));MonsterManager.spawn(f.level,pos,MonsterType.OBSIDIAN_TORTOISE_WARDEN);f.target=f.level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(2),MonsterManager::isMonster).getFirst();((Mob)f.target).setNoAi(true);f.target.setNoGravity(true);f.target.setPos(f.origin.add(0,0,2));h.assertTrue(com.myriad.character.skills.keheng.KehengTimeFields.managedNpcRate(f.target,f.level.getGameTime())==1,"A real managed large boss remains immune to local slow");}
        f.initialZ=f.target.getZ();f.maxZ=f.initialZ;f.before=hp(f.target);
    }
    private static void runScenes(GameTestHelper h,List<Fixture> fixtures,boolean[] done,Runnable cleanup) {
        var combatFailures=new ArrayList<String>();
        for(int n=1;n<=500;n++){final int tick=n;h.runAfterDelay(n+209,()->{
            if(done[0])return;
            try {
                for(var f:fixtures){var p=f.p;var d=PlayerDataProvider.get(p);int i=f.index;p.setOnGround(true);if(tick%20==0&&i!=69)CombatChunks.hold(f.level,BlockPos.containing(f.origin));
                    // Keep mock player physics faithful to the actual server motion, without teleporting a dash.
                    if(AbilityRuntime.moving(p))p.move(MoverType.SELF,p.getDeltaMovement());
                    if(tick==1){
                        if(i==69)p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(-120,13,-30))));
                        if(i==65)h.assertTrue(TacticalAnchor.anchor(p)!=null,"The late-impact test begins with a valid live anchor");
                        if(i>=8&&i<=19||i==29||i==38||i==39||i==47||i==52||i==70||i>=64&&i<=66)SkillSystem.cast(p,0);
                        if(Set.of(20,35,36,37,50,51,53,55).contains(i))SkillSystem.cast(p,3);
                        if(Set.of(22,24,33,34,44,45,46,48,49,54,68).contains(i))SkillSystem.cast(p,2);
                        if(i==23)SkillSystem.cast(p,3);
                    }
                    if(tick==5&&i==0){AbilityRuntime.tick(p);PlayerDataProvider.syncToClient(p);var hud=f.mailboxes.get(p.getUUID()).stream().filter(NetworkSetup.PlayerDataPayload.class::isInstance).map(NetworkSetup.PlayerDataPayload.class::cast).reduce((a,b)->b).orElseThrow();h.assertTrue(hud.characterId().equals("rivet")&&!hud.training()&&hud.skillLevels().getOrDefault(node(3).getId(),0)==0&&hud.cdW()==160&&hud.buffs().getOrDefault("g1_rivet_anchor_active",0)==1,"Actual owner HUD reports free unranked W cooldown and the live anchor");var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{NetworkSetup.PlayerDataPayload.STREAM_CODEC.encode(wire,hud);h.assertTrue(NetworkSetup.PlayerDataPayload.STREAM_CODEC.decode(wire).equals(hud),"Rivet owner state survives the production wire codec");}finally{wire.release();}for(var observer:new ServerPlayer[]{f.ally,f.other})h.assertTrue(f.mailboxes.get(observer.getUUID()).stream().filter(AbilityVfxPayload.class::isInstance).map(AbilityVfxPayload.class::cast).anyMatch(packet->packet.theme()==13&&packet.kind()==AbilityVfxGeometry.WALL),"A nearby ally and visible enemy both receive the actual anchor-state packet");}
                    if(tick==2&&i==67){float mana=d.getMana();SkillSystem.cast(p,2);h.assertTrue(d.getMana()==mana&&d.getCooldown(node(5).getId())==0,"Tow rejects a low body obstacle even when its eye ray is clear");}
                    if(tick==2&&i==28){for(int y=0;y<3;y++)block(f,0,y,4,Blocks.AIR);}
                    if(tick==2&&i==35)f.target.setPos(f.origin.add(3,0,2.35));
                    if(tick==2&&i==37)wall(f,1,3);
                    if(tick==3&&i==33)wall(f,2,3);
                    if(tick==3&&i==34)SkillSystem.cast(p,2);
                    if(tick==3&&i==48){p.setHealth(0);AbilityRuntime.tick(p);h.assertTrue(TacticalAnchor.anchor(p)==null&&p.getDeltaMovement().horizontalDistanceSqr()==0,"Death during a pull clears anchor, shield and owned velocity");}
                    if(tick==3&&i==49){p.setServerLevel(h.getLevel().getServer().getLevel(Level.NETHER));AbilityRuntime.cleanup();h.assertTrue(TacticalAnchor.anchor(p)==null,"Dimension switch during a pull clears the old anchor");}
                    if(tick==3&&i==50)d.setBuff("g1_stun",30);
                    if(tick==3&&i==51)SilenceSystem.apply(p,30);
                    if(tick==3&&i==55)ActionTime.apply(p.getUUID(),p.getUUID(),"rivet_charge_slow",.5,p.level().getGameTime()+100);
                    if(tick==12&&i==38){SkillSystem.cast(p,3);}
                    if(tick==12&&i==39){f.other.setYRot(0);SkillSystem.cast(f.other,0);}
                    if(tick==15&&i==66)ActionTime.apply(f.target.getUUID(),p.getUUID(),"rivet_push_slow",1,f.level.getGameTime()+200);
                    if(tick==14&&i==29){int recovery=TacticalAnchor.recovery(p);h.assertTrue(recovery>0,"Q wall anchor impact starts owner recovery");d.setCooldown(node(3).getId(),0);SkillSystem.cast(p,1);h.assertTrue(TacticalAnchor.recovery(p)>=recovery-1,"Replacing an anchor cannot reset recovery");}
                    if(tick==18&&(i==47||i==52||i==70)){p.setPos(f.target.position().add(0,0,-1.2));p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(100);d.setCooldown(node(4).getId(),100);f.before=hp(f.target);p.getRandom().setSeed(28);basic(p,f.target);f.firstBasicLoss=f.before-hp(f.target);}
                    if(tick==19&&(i==47||i==52||i==70)){h.assertTrue(f.before-hp(f.target)>0&&d.getCooldown(node(4).getId())==60,"A real Q wall-followup basic hit consumes P2 and refunds exactly two seconds of E: "+i+" hpLoss="+(f.before-hp(f.target))+" cd="+d.getCooldown(node(4).getId())+" rhythm="+d.getBuff("g1_rivet_rhythm"));}
                    if(tick==100&&(i==47||i==70)){float health=hp(f.target);p.getRandom().setSeed(28);basic(p,f.target);f.plainBasicLoss=health-hp(f.target);near(h,f.firstBasicLoss-f.plainBasicLoss,i==47?30:112,"Matched real critical basics isolate the uncritical P2 first/rank-five S230 bonus: "+i);}
                    if(tick==20&&i==19)h.assertTrue(d.getMana()==615&&d.getCooldown(node(2).getId())==140,"Grown Q commits its unchanged 55 mana and seven-second cooldown");
                    if(tick==20&&i==20)h.assertTrue(d.getMana()==520&&d.getCooldown(node(7).getId())==1000,"Grown R2 commits its unchanged 150 mana and fifty-second cooldown");
                    if(tick==20&&i==22)h.assertTrue(d.getMana()==595&&d.getCooldown(node(4).getId())==260&&d.getCooldown(node(3).getId())==120,"Grown E1 commits 75 mana/thirteen seconds and refunds exactly two seconds of W");
                    if(tick==20&&i==23)h.assertTrue(d.getMana()==530&&d.getCooldown(node(6).getId())==1100,"Grown R1 commits 140 mana and fifty-five seconds");
                    if(tick==20&&i==24)h.assertTrue(d.getMana()==620,"Rank-seven self-pull commits exactly fifty mana");
                    if(tick==20&&i==68){Vec3 aim=f.target.position().subtract(p.position());p.setYRot((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));SkillSystem.cast(p,0);}
                    if(tick==20&&i==39){AbilityRuntime.clear(p);int root=RivetPush.rootRemaining(f.target);h.assertTrue(root>0&&root<=5,"The second owner keeps its independently sourced, shared-DR half root after the first owner leaves; remaining="+root);}
                    if(tick==20&&i==55)h.assertTrue(loss(f)==0&&d.getMana()==700,"Local slow delays unfinished R2 windup without charging early");
                    if(tick==60&&i==55)near(h,loss(f),170,"Slowed windup eventually commits its ordinary physical charge");
                    if(tick==20){if(i>=56&&i<=61)h.assertTrue(d.getCooldown(node(3).getId())==160-8*(i-56)&&d.getMana()==670,"Each paid W rank commits its exact normal cooldown and 30 mana: "+(i-56));if(i==24)h.assertTrue(d.getCooldown(node(5).getId())==140,"Rank-seven tow commits a seven-second cooldown");if(i==25)h.assertTrue(d.getCooldown(node(3).getId())==120,"Five paid W points shorten base cooldown from eight to six seconds");}
                    if(tick==42&&(i>=40&&i<=43||i==62)){near(h,ModShieldSystem.value(p),80,"P1 first grounded two seconds grant 80: "+i);f.shieldAbsorbed=ModShieldSystem.consume(p,45);}
                    if(tick==44&&i==40)p.setPos(f.origin.add(6,0,0));
                    if(tick==63&&i==40)p.setPos(f.origin);
                    if(tick==84&&i==40)near(h,ModShieldSystem.value(p),35,"A short exit preserves remaining armor and never stacks a top-up");
                    if(tick==44&&(i==41||i==43||i==62||i==63))p.setPos(f.origin.add(6,0,0));
                    if(tick==85&&(i==41||i==43||i==62||i==63)){near(h,ModShieldSystem.value(p),0,"A full two-second exit clears only station armor");p.setPos(f.origin);}
                    if(tick==86&&i==62)ModShieldSystem.grant(p,StationArmor.source(p),35,600);
                    if(tick==126&&i==62)near(h,ModShieldSystem.value(p),35,"A pre-existing 35 armor remainder is preserved instead of adding 16");
                    if(tick==126&&i==63)near(h,ModShieldSystem.value(p),46.4,"Return fraction uses current rank-five S230 full armor dynamically");
                    if(tick==126&&i==41)near(h,ModShieldSystem.value(p),16,"Valid exit and grounded return refill to 20 percent, not additive");
                    if(tick==44&&(i==42||i==43))ModShieldSystem.consume(p,10000);
                    if(tick==126&&(i==42||i==43))near(h,ModShieldSystem.value(p),0,"Breaking armor enforces a ten-second lockout even after re-entry");
                    if(tick==245&&i==42)near(h,ModShieldSystem.value(p),80,"Uninterrupted grounded defense restores a full shield after ten real seconds");
                    if(tick==285&&i==43)near(h,ModShieldSystem.value(p),16,"Effective exit after break restores only the return fraction when lockout ends");
                    if(i<8){
                        if(tick==43&&((i&1)==0))near(h,ModShieldSystem.value(p),80,"Combination P1 actually shields its owner: "+i);
                        if(tick==45)SkillSystem.cast(p,2);
                        if(tick==64){p.setPos(f.origin);SkillSystem.cast(p,0);}
                        if(tick==82){h.assertTrue(loss(f)>=125,"Combination Q really hits and collides with the wall: "+i);if((i&1)==1){p.setPos(f.target.position().add(0,0,-1.2));d.setCooldown(node(4+((i>>1)&1)).getId(),100);basic(p,f.target);f.sawBonus=true;}}
                        if(tick==86){p.setPos(f.origin);f.target.setPos(f.origin.add(0,0,2.35));SkillSystem.cast(p,3);}
                        if(tick==130){h.assertTrue(f.sawRoot,"Every branch combination observes an actual wall root: "+i);if(((i>>2)&1)==0)h.assertTrue(ModShieldSystem.value(p)>=160,"Combination R1 actually grants its own shield: "+i);else h.assertTrue(loss(f)>=340,"Combination R2 deals direct and wall damage: "+i);}
                    }
                    if(tick==10&&i==22){near(h,ModShieldSystem.value(f.ally),342,"Rank-seven S230 emergency strut grants 342 actual ally shield");}
                    if(tick==10&&i==23)near(h,ModShieldSystem.sourceValue(p,"rivet_hold_protocol/"+p.getUUID()),380,"Rank-three S230 hold grants 380 own source shield");
                    if(tick==10&&i==44){float value=ModShieldSystem.value(f.ally);near(h,value,110,"E1 grants its full shield to a real ally");ModShieldSystem.grant(f.ally,"unrelated_test_shield",20,600);AbilityRuntime.clear(p);near(h,ModShieldSystem.value(f.ally),20,"Owner cleanup removes only its own ally shield");}
                    if(tick==10&&i==45){ModShieldSystem.consume(f.ally,75);d.setCooldown(node(4).getId(),0);d.setMana(700);SkillSystem.cast(p,2);near(h,ModShieldSystem.value(f.ally),110,"Same support group uses the higher remaining shield rather than additive stacking");}
                    if(tick==12&&i==45)SkillSystem.cast(f.other,2);
                    if(tick==13&&i==45){near(h,ModShieldSystem.value(f.ally),132,"Two owners contribute only the stronger strut group");float beforeShield=ModShieldSystem.value(f.ally);f.ally.invulnerableTime=0;NpcCombat.hit(f.ally,f.target,100,DamageCalc.Kind.TRUE);f.npcAbsorbed=beforeShield-ModShieldSystem.value(f.ally);near(h,f.npcAbsorbed,100,"A shared strut family absorbs one actual NPC hit");}
                    if(tick==14&&i==45){AbilityRuntime.clear(f.other);near(h,ModShieldSystem.value(f.ally),10,"Leaving stronger owner removes only its source and cannot resurrect an unspent weaker reserve");}
                    if(tick==10&&i==46){float hp=f.ally.getHealth();float shield=ModShieldSystem.value(f.ally);f.ally.invulnerableTime=0;NpcCombat.hit(f.ally,f.target,50,DamageCalc.Kind.TRUE);f.shieldAbsorbed=shield-ModShieldSystem.value(f.ally);f.npcAbsorbed=f.shieldAbsorbed;near(h,f.shieldAbsorbed,50,"Support armor absorbs actual incoming damage budget");h.assertTrue(f.ally.getHealth()==hp,"Shield absorption preserves ally health");}
                    if(tick==50&&Set.of(28,48,49).contains(i))h.assertTrue(TacticalAnchor.anchor(p)==null,"Missing/unloaded or cleared anchor cannot remain active: "+i);
                    if(tick==50&&i==48){AbilityRuntime.clear(p);h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"Lifecycle cleanup releases server motion");}

                    if(tick==60&&i==54){SkillSystem.cast(p,3);}
                    if(tick==67&&i==54){for(int y=0;y<3;y++)block(f,0,y,4,Blocks.AIR);h.assertTrue(HoldProtocol.incoming(f.ally)==.85,"Locked original zone still protects its ally after anchor replacement and wall destruction");}
                    if(tick==66&&i==54){d.setCooldown(node(3).getId(),0);p.setYRot(180);wall(f,-4,3);SkillSystem.cast(p,1);p.setYRot(0);h.assertTrue(ModShieldSystem.value(p)>=160,"An already locked hold zone keeps its shield after anchor replacement");}
                    AbilityRuntime.tick(p);if(f.other!=null)AbilityRuntime.tick(f.other);
                    if(f.target.isAlive()){boolean controlled=RivetPush.autonomousMovementBlocked(f.target);f.continuousControl=controlled?f.continuousControl+1:0;f.maxContinuousControl=Math.max(f.maxContinuousControl,f.continuousControl);f.maxZ=Math.max(f.maxZ,f.target.getZ());int root=RivetPush.rootRemaining(f.target);if(root>0){if(root>f.previousRoot)f.roots.add(root);f.continuousRoot++;f.maxContinuousRoot=Math.max(f.maxContinuousRoot,f.continuousRoot);f.sawRoot=true;f.maxRoot=Math.max(f.maxRoot,root);h.assertTrue(f.target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==0,"An active sourced root actually blocks autonomous movement: "+i);}else f.continuousRoot=0;f.previousRoot=root;}
                    if(tick==165)try{verifyCombat(h,f);}catch(RuntimeException failure){combatFailures.add("case "+i+": "+failure.getMessage());System.out.println("[RIVET-CASE-FAIL] "+combatFailures.getLast());}
                    if(tick==330&&i==69){h.assertTrue(!f.level.hasChunkAt(f.anchorBlock)&&TacticalAnchor.remaining(p)>0&&TacticalAnchor.anchor(p)==null,"An actually unloaded support chunk deactivates an unexpired anchor without force-loading it");float mana=d.getMana();SkillSystem.cast(p,2);h.assertTrue(d.getMana()==mana&&d.getCooldown(node(5).getId())==0&&!f.level.hasChunkAt(f.anchorBlock),"An inactive unloaded anchor cannot charge a tow or reload its support chunk");}
                    if(tick==365&&i==30)h.assertTrue(TacticalAnchor.anchor(p)==null,"W expires after eighteen real seconds");
                }
                if(tick==500){
                    h.assertTrue(combatFailures.isEmpty(),String.join(" | ",combatFailures));
                    int collisions=(int)fixtures.stream().filter(f->f.index<8&&f.sawRoot).count();double absorbed=fixtures.stream().mapToDouble(f->f.npcAbsorbed).sum();int longest=fixtures.stream().mapToInt(f->f.maxContinuousRoot).max().orElse(0);int longestControl=fixtures.stream().mapToInt(f->f.maxContinuousControl).max().orElse(0);
                    System.out.println("[MYRIAD-RIVET32] PASS: "+CASES+" real-input scenes + 2 formal draft/preparation players; combinations=8, Q-wall="+collisions+"/8; actual NPC shield absorption="+absorbed+"; tow-then-Q hits=1/1; maximum observed consecutive rooted ticks="+longest+"; maximum consecutive forced-movement-or-root ticks="+longestControl+"; visual/audio/feel acceptance excluded");
                    done[0]=true;cleanup.run();h.succeed();
                }
            }catch(Throwable failure){failure.printStackTrace();done[0]=true;cleanup.run();throw new IllegalStateException("Rivet test: "+failure,failure);}
        });}
    }
    private static void verifyCombat(GameTestHelper h,Fixture f){int i=f.index;
        if(i==8) {near(h,loss(f),90,"Flat ground Q is 90 direct physical damage without wall bonus");h.assertTrue(f.maxZ-f.initialZ>1.8&&!f.sawRoot,"Flat push covers approximately two blocks with no root");}
        if(i==9||i==10){near(h,loss(f),90,"A passable slab/stair is not a wall: "+i);h.assertTrue(!f.sawRoot&&f.maxZ-f.initialZ>1.5,"A passable slab/stair allows forward movement: "+i);}
        if(i==11||i==12||i==13){near(h,loss(f),125,"A real pillar/corner/tunnel side collision grants one wall bonus: "+i);h.assertTrue(f.maxRoot==10,"Ordinary Q wall root remains exactly half a second: "+i);}
        if(i==14)h.assertTrue(f.maxZ==f.initialZ,"Q damages its cone without pushing a target outside the crosshair");
        if(i==15)h.assertTrue(f.maxZ>f.initialZ&&Math.abs(f.rear.getZ()-(f.origin.z+2.9))<.02,"Only the first crosshair target is pushed");
        if(i==17)h.assertTrue(ModShieldSystem.value(f.target)<1000&&f.sawRoot,"Shield absorption counts as an effective hit and still enables collision");
        if(i==18)h.assertTrue(loss(f)==0&&!f.sawRoot&&f.maxZ==f.initialZ,"Invulnerable targets cannot create effective Q hit rewards");
        if(i==19)near(h,loss(f),377,"Rank-five S230 Q and its own wall formula deal 270+107");
        if(i==20)near(h,loss(f),555,"Rank-three S230 R2 and its own wall formula deal 430+125");
        if(i==21)near(h,ModShieldSystem.value(f.p),232,"Rank-five S230 station armor grows to 232");

        if(i==23)h.assertTrue(ModShieldSystem.sourceValue(f.p,"rivet_hold_protocol/"+f.p.getUUID())==0,"Hold shield expires with its real six-second zone");
        if(i==28)h.assertTrue(TacticalAnchor.anchor(f.p)==null,"Breaking the attached block deactivates W");
        if(i==33)h.assertTrue(f.p.getZ()<f.origin.z+2,"A dynamic obstacle halts tow before the wall");
        if(i==34)h.assertTrue(f.p.getZ()<f.origin.z+4,"Early tow release stops short of the full five blocks");
        if(i==35)h.assertTrue(loss(f)==0&&f.target.getZ()==f.initialZ,"A target sidestepping during the visible R2 windup escapes damage and push");
        if(i==36)h.assertTrue(loss(f)>0&&hp(f.rear)==JiarenManager.MAX_HP,"R2 stops on its first enemy and protects the rear target");
        if(i==37)h.assertTrue(loss(f)==0&&f.p.getZ()<f.origin.z+1,"R2 caster wall collision prevents hitting a target beyond the obstruction");
        if(i==38)h.assertTrue(loss(f)>=340&&f.roots.contains(16)&&f.roots.contains(8),"Q into R2 retains actual damage and does not suppress the second push");
        if(i==39)h.assertTrue(loss(f)>=250,"Two Rivets share the target root record while both still deal their wall hits");
        if(i==50||i==51)h.assertTrue(loss(f)==0,"Stun/silence cancels a not-yet-committed R2 charge: "+i);
        if(i==68){near(h,loss(f),125,"A real self-pull followed from its actual endpoint by an aimed Q hits and wall-collides");h.assertTrue(f.p.position().subtract(f.origin).horizontalDistance()>4.8&&f.sawRoot,"Tow-to-Q success includes actual completed movement and target control");}
        if(i==64||i==66){near(h,loss(f),90,"Slowed flat Q retains direct damage without invented wall bonus: "+i);h.assertTrue(f.maxZ-f.initialZ>1.85&&!f.sawRoot,"Slowed or rate-changing push preserves completed work and its two-block budget: "+i);}
        if(i==65){near(h,loss(f),90,"A late slowed wall contact awards no damage outside the eight-real-tick window");h.assertTrue(f.maxZ-f.initialZ>1.7&&!f.sawRoot&&TacticalAnchor.recovery(f.p)==0&&PlayerDataProvider.get(f.p).getBuff("g1_rivet_rhythm")==0,"Late slowed wall contact only stops motion, without anchor, root or P2 rewards");}
        if(i==52||i==53)h.assertTrue(f.maxZ>f.initialZ&&f.sawRoot&&hp(f.target)<f.before,"A real managed large boss accepts push, wall damage and root: "+i);
    }
    private static void basic(ServerPlayer player,LivingEntity target){set(LivingEntity.class,player,"attackStrengthTicker",200);target.invulnerableTime=0;player.fallDistance=0;player.attack(target);}
    private static AbilitySkill node(int i){return FirstGroupSkills.node("rivet",i);}
    private static void rank(Fixture f,int index,int rank){var d=PlayerDataProvider.get(f.p);for(int r=d.skillLevel(node(index).getId());r<rank;r++)PlayerDataProvider.upgradeSkill(f.p,node(index).getId());}
    private static Fixture fixture(GameTestHelper h,int i){var f=new Fixture();f.index=i;f.level=h.getLevel();var at=h.absolutePos(i==69?new BlockPos(1024,12,-30):new BlockPos(-80+(i%8)*20,12+(i/8)*7,-30));
        f.origin=Vec3.atBottomCenterOf(at.above());for(int x=-7;x<=7;x++)for(int z=-4;z<=11;z++)for(int y=-1;y<4;y++)block(f,x,y,z,y==-1?Blocks.STONE:Blocks.AIR);
        f.p=player(h,f,f.origin);f.p.setYRot(0);f.p.setXRot(0);if(i<80)f.target=dummy(f,f.origin.add(0,0,2.35));return f;}
    private static ServerPlayer player(GameTestHelper h,Fixture f,Vec3 at){var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var c=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(f.level.getServer(),c,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom){f.packets.add(custom.payload());f.mailboxes.computeIfAbsent(p.getUUID(),ignored->new ArrayList<>()).add(custom.payload());}}};
        p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);f.level.players().add(p);f.level.getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);return p;}
    private static Villager dummy(Fixture f,Vec3 pos){Vec3 before=f.p.position();f.p.setPos(pos.add(0,0,-3));JiarenManager.create(f.p);f.p.setPos(before);var target=f.level.getEntitiesOfClass(Villager.class,new AABB(pos,pos.add(0,2,0)).inflate(2),v->JiarenManager.isJiaren(v.getUUID())).stream().max(Comparator.comparingInt(Entity::getId)).orElseThrow();target.setPos(pos);target.setNoGravity(true);target.setOnGround(true);return target;}
    private static void wall(Fixture f,int z,int height){for(int x=-2;x<=2;x++)for(int y=0;y<height;y++)block(f,x,y,z,Blocks.STONE);}
    private static void clearWall(Fixture f,int z){for(int x=-2;x<=2;x++)for(int y=0;y<3;y++)block(f,x,y,z,Blocks.AIR);}
    private static void block(Fixture f,int x,int y,int z,Block b){BlockPos pos=BlockPos.containing(f.origin).offset(x,y,z);f.blocks.putIfAbsent(pos,f.level.getBlockState(pos));f.level.setBlockAndUpdate(pos,b.defaultBlockState());}
    private static float hp(LivingEntity target){return JiarenManager.isJiaren(target.getUUID())?JiarenManager.hp(target.getUUID()):MonsterManager.modHealth(target);}
    private static float loss(Fixture f){return f.before-hp(f.target);}
    private static void near(GameTestHelper h,double actual,double expected,String label){h.assertTrue(Math.abs(actual-expected)<.15,label+" (actual="+actual+", expected="+expected+")");}
    private static void cleanup(Fixture f){for(var p:new ServerPlayer[]{f.p,f.ally,f.other})if(p!=null){AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);SilenceSystem.clearFor(p.getUUID());p.level().players().remove(p);@SuppressWarnings("unchecked") var online=(List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,f.level.getServer().getPlayerList(),"players");online.remove(p);f.level.getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());if(p.level() instanceof ServerLevel actual)actual.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);else p.discard();}if(f.target!=null)f.target.discard();if(f.rear!=null)f.rear.discard();f.blocks.forEach(f.level::setBlockAndUpdate);}
    private static Object field(Class<?> type,Object target,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void set(Class<?> type,Object target,String name,Object value){try{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
}
