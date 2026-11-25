package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.combat.DamageCalc;
import com.myriad.network.NetworkSetup;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A-group production play receivers. Fixture setup supplies level/health, never learned ranks or cast results. */
final class RosterAChecks {
    private static final List<String> ROLES=List.of("keheng","shuo","tianxing","fenjue","xuanchen","qingluo","chixiao");
    private record Actor(ServerPlayer player,EmbeddedChannel channel,List<CustomPacketPayload> packets){}
    private static final class Fixture {
        AbilitySkill skill;Actor actor,enemy,ally;Vec3 origin;int recasts;float minEnemy=10000,maxHeal=4000,maxShield;double allyTravel;
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
    }
    static void run(GameTestHelper h) {
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled();
        var previous=GameManager.get();var fixtures=new ArrayList<Fixture>();boolean[] done={false};
        ModState.setEnabled(true);TrainingMode.setEnabled(true);
        GameManager.create();
        Runnable cleanup=()->{
            for(var f:fixtures){for(var a:List.of(f.actor,f.enemy,f.ally)){
                AbilityRuntime.clear(a.player);ModShieldSystem.clearFor(a.player);
                h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(a.player.getUUID());
                h.getLevel().removePlayerImmediately(a.player,Entity.RemovalReason.DISCARDED);a.channel.finishAndReleaseAll();
            }f.blocks.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));}
            try{var field=GameManager.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,previous);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            TrainingMode.setEnabled(training);ModState.setEnabled(enabled);
        };
        try {
            GameManager.get().setServer(h.getLevel().getServer());
            for(String role:ROLES)for(int index=2;index<8;index++){
                var f=fixture(h,fixtures.size(),FirstGroupSkills.node(role,index));fixtures.add(f);
                var p=f.actor.player;receive(h,p,new NetworkSetup.ChooseCharacterPayload(role));
                var d=PlayerDataProvider.get(p);
                h.assertTrue(d.getCharacterId().equals(role)&&d.getSkillPoints()==1
                        &&p.getInventory().getItem(0).is(ModItems.initialWeaponItem(role)),"Formal A-group choice and weapon: "+role);
                d.setLevel(20);d.setSkillPoints(20);
                // Learn both passive alternatives separately, through the same registered receiver.
                if(index==2)for(int passive=0;passive<2;passive++){
                    var node=FirstGroupSkills.node(role,passive);receive(h,p,new NetworkSetup.UpgradeSkillPayload(node.getId()));
                    h.assertTrue(d.skillLevel(node.getId())==1,"Registered passive learning: "+node.getId());
                    String opposite=FirstGroupSkills.node(role,1-passive).getId();int points=d.getSkillPoints();
                    receive(h,p,new NetworkSetup.UpgradeSkillPayload(opposite));
                    h.assertTrue(d.skillLevel(opposite)==0&&d.getSkillPoints()==points,"Mutually exclusive passive request spends nothing");
                    d.refundAllSkillPointsForTraining();
                }
                receive(h,p,new NetworkSetup.UpgradeSkillPayload(f.skill.getId()));
                h.assertTrue(d.skillLevel(f.skill.getId())==1&&d.getSkillPoints()==19,"One paid rank via production receiver: "+f.skill.getId());
                int opposite=FirstGroupSkills.opposite(index);
                if(opposite>=0){receive(h,p,new NetworkSetup.UpgradeSkillPayload(FirstGroupSkills.node(role,opposite).getId()));
                    h.assertTrue(d.getSkillPoints()==19&&d.skillLevel(FirstGroupSkills.node(role,opposite).getId())==0,"Opposite active branch cannot spend: "+f.skill.getId());}
                prepare(p);d.setFaction(Faction.GUARDIAN);p.setPos(f.origin);aim(p,f.skill.needsPoint()||role.equals("tianxing")&&index==2);
            }
            // The manager phase is fixture preparation. All tested cast/confirm requests below run with training off.
            var phase=GameManager.class.getDeclaredField("phase");phase.setAccessible(true);phase.set(GameManager.get(),GameManager.Phase.PLAYING);
            var factionField=GameManager.class.getDeclaredField("factions");factionField.setAccessible(true);
            @SuppressWarnings("unchecked") var factions=(Map<UUID,Faction>)factionField.get(GameManager.get());
            for(var f:fixtures){factions.put(f.actor.player.getUUID(),Faction.GUARDIAN);factions.put(f.ally.player.getUUID(),Faction.GUARDIAN);factions.put(f.enemy.player.getUUID(),Faction.DESTRUCTION);}
            TrainingMode.setEnabled(false);
            for(var f:fixtures) {
                var p=f.actor.player;var d=PlayerDataProvider.get(p);
                receive(h,p,new NetworkSetup.CastSkillPayload(f.skill.slot(),0,0));
                if(f.skill.needsPoint()){
                    h.assertTrue(d.getMana()==700&&d.getCooldown(f.skill.getId())==0&&d.getPlacementType()!=0,"Preview remains unpaid: "+f.skill.getId());
                    receive(h,p,new NetworkSetup.CancelPlacementPayload());
                    h.assertTrue(d.getMana()==700&&d.getPlacementType()==0,"Formal preview cancellation is free: "+f.skill.getId());
                    receive(h,p,new NetworkSetup.CastSkillPayload(f.skill.slot(),0,0));
                    receive(h,p,new NetworkSetup.ConfirmPlacementPayload());
                }
                h.assertTrue(d.getMana()==700-f.skill.getManaCost()&&d.getCooldown(f.skill.getId())==f.skill.getCooldownTicks(1),
                        "Non-training production input pays exact mana/CD: "+f.skill.getId()+" mana="+d.getMana()+" cd="+d.getCooldown(f.skill.getId()));
                p.setXRot(0);
                if(f.skill.getId().equals("shuo_qiaofan")){
                    float before=p.getHealth();DamageCalc.dealDamage(f.enemy.player,p,100,DamageCalc.Kind.PHYSICAL);
                    h.assertTrue(p.getHealth()==before&&d.getBuff("shuo_qiaofan_recast")>0,"Actual frontal incoming hit opens Shuo's paid guard counter");
                    receive(h,p,new NetworkSetup.CastSkillPayload(f.skill.slot(),0,0));f.recasts++;
                }
                if(f.skill.getId().equals("g2_chixiao_ma")){
                    float before=p.getHealth();DamageCalc.dealDamage(f.enemy.player,p,100,DamageCalc.Kind.PHYSICAL);
                    h.assertTrue(Math.abs(before-p.getHealth()-65)<.02,"Actual incoming physical hit is reduced to 65 by Crimson Parry");
                }
                sample(f);
            }
            for(int tick=1;tick<=165;tick++){int age=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    if(age>145){passiveTick(h,fixtures,age-145);if(age==165){done[0]=true;cleanup.run();
                        System.out.println("[MYRIAD-ROSTER-A] PASS: seven formal choices/weapons, 56 paid candidate learning paths, 42 non-training production codec/receiver casts, exact mana/CD, point cancel/confirm, heal/shield/damage/motion/portal/parry/recast, nine additional passive transactions, Fabric save/load and lifecycle");h.succeed();}return;}
                    for(var f:fixtures){
                        f.enemy.player.damageCooldownTime=0;
                        if(AbilityRuntime.moving(f.actor.player))f.actor.player.move(MoverType.SELF,f.actor.player.getDeltaMovement());
                        AbilityRuntime.tick(f.actor.player);sample(f);
                        var c=AbilityRuntime.context(f.actor.player,f.skill);
                        if(f.skill.getId().equals("shuo_canyue")&&f.recasts<2&&!ShuoCombat.blocked(f.actor.player)&&f.skill.recastAvailable(c)){
                            receive(h,f.actor.player,new NetworkSetup.CastSkillPayload(f.skill.slot(),0,0));f.recasts++;
                        }
                        if(f.skill.getId().equals("g2_chixiao_n2")&&age==12){
                            h.assertTrue(f.actor.player.position().distanceTo(f.origin)>6,"Soul Pursuit first leg actually moves seven blocks");
                            receive(h,f.actor.player,new NetworkSetup.CastSkillPayload(f.skill.slot(),0,0));f.recasts++;
                        }
                        if(f.skill.getId().equals("g1_xuanchen_ma")&&age==2){f.ally.player.setPos(f.origin);f.ally.player.setShiftKeyDown(true);}
                        if(f.skill.getId().equals("g1_xuanchen_ma")&&age==10)f.ally.player.setShiftKeyDown(false);
                    }
                    if(age==145){
                        for(var f:fixtures){verify(h,f);verifySaved(h,f);AbilityRuntime.clear(f.actor.player);
                            h.assertTrue(!AbilityRuntime.moving(f.actor.player)&&!AbilityRuntime.channeling(f.actor.player)
                                    &&PlayerDataProvider.get(f.actor.player).getPlacementType()==0,"Lifecycle clears motion/channel/placement: "+f.skill.getId());}
                        preparePassives(h,fixtures);
                    }
                }catch(Throwable failure){done[0]=true;cleanup.run();throw failure;}
            });}
        }catch(Throwable failure){done[0]=true;cleanup.run();throw new IllegalStateException(failure);}
    }
    private static void sample(Fixture f){f.minEnemy=Math.min(f.minEnemy,f.enemy.player.getHealth());f.allyTravel=Math.max(f.allyTravel,f.ally.player.position().distanceTo(f.origin));
        f.maxHeal=Math.max(f.maxHeal,Math.max(f.actor.player.getHealth(),f.ally.player.getHealth()));
        f.maxShield=Math.max(f.maxShield,Math.max(ModShieldSystem.value(f.actor.player),ModShieldSystem.value(f.ally.player)));}
    private static void verify(GameTestHelper h,Fixture f){
        String id=f.skill.getId();
        switch(id){
            case "keheng_cuopaibu" -> h.assertTrue(f.maxShield>=39.49&&f.actor.player.position().distanceTo(f.origin)>4.4,"Offbeat Step shield and actual dash");
            case "shuo_nadao" -> h.assertTrue(f.actor.player.position().distanceTo(f.origin)>2.9,"Sheathed NaDao performs the real retreat");
            case "g1_tianxing_ma" -> h.assertTrue(f.maxShield>0||PlayerDataProvider.get(f.actor.player).getBuff("g1_guard15")>0,"Thunder Guard creates its supported protection");
            case "g1_fenjue_ub","g1_xuanchen_n2","g1_xuanchen_mb","g2_qingluo_ma","g2_chixiao_ub" -> h.assertTrue(f.maxShield>0,"Actual supported shield: "+id);
            case "g1_xuanchen_ma" -> h.assertTrue(f.allyTravel>3,"Crouching ally uses paired portal");
            case "g2_qingluo_n1","g2_qingluo_mb","g2_qingluo_ua" -> h.assertTrue(f.maxHeal>4000,"Actual supported healing: "+id);
            case "g2_qingluo_ub" -> h.assertTrue(f.maxHeal>4000&&f.maxShield>0,"Jade Canopy heals and shields legal allies");
            default -> h.assertTrue(f.minEnemy<10000,"Actual enemy damage: "+id+" enemy="+f.minEnemy+" actor="+f.actor.player.position().subtract(f.origin));
        }
        // Exact baseline S=30/rank-one expectations for the A-group roles without a prior dedicated suite.
        var expected=Map.ofEntries(Map.entry("g1_xuanchen_n1",61.5),Map.entry("g1_xuanchen_ua",216.0),Map.entry("g1_xuanchen_ub",203.0),
                Map.entry("g2_qingluo_n2",63.5),Map.entry("g2_chixiao_n1",93.0),Map.entry("g2_chixiao_n2",62.0),
                Map.entry("g2_chixiao_ma",132.5),Map.entry("g2_chixiao_mb",101.5),Map.entry("g2_chixiao_ua",195.5));
        if(expected.containsKey(id))h.assertTrue(Math.abs(10000-f.minEnemy-expected.get(id))<.02,"Exact baseline damage: "+id);
        var shields=Map.of("g1_xuanchen_n2",108.0,"g1_xuanchen_mb",70.5,"g2_qingluo_ma",90.5,"g2_qingluo_mb",171.0,"g2_qingluo_ub",219.5,"g2_chixiao_ub",251.0);
        if(shields.containsKey(id))h.assertTrue(Math.abs(f.maxShield-shields.get(id))<.02,"Exact baseline shield: "+id);
        var heals=Map.of("g2_qingluo_n1",85.0,"g2_qingluo_n2",42.5,"g2_qingluo_mb",98.0,"g2_qingluo_ua",309.6,"g2_qingluo_ub",112.0);
        if(heals.containsKey(id))h.assertTrue(Math.abs(f.maxHeal-4000-heals.get(id))<.02,"Exact baseline healing including real-time pulses: "+id);
        if(id.equals("shuo_canyue"))h.assertTrue(f.recasts==2,"Both paid-free CanYue follow-ups use the registered input receiver");
        if(id.equals("g2_chixiao_n2"))h.assertTrue(f.recasts==1&&f.actor.player.position().distanceTo(f.origin)<.1,"Soul Pursuit recast returns by motion to its saved origin");
        var d=PlayerDataProvider.get(f.actor.player);h.assertTrue(d.getMana()==700-f.skill.getManaCost(),"Follow-ups never double-pay: "+id);
        h.assertTrue(!f.actor.packets.isEmpty(),"Actual owner packets survive production S2C codec: "+id);
        System.out.println("[MYRIAD-ROSTER-A-CAST] "+id+" damage="+(10000-f.minEnemy)+" heal="+(f.maxHeal-4000)+" shield="+f.maxShield+" delta="+f.actor.player.position().subtract(f.origin)+" recasts="+f.recasts);
    }
    /** These branches previously had graph checks but no isolated behavioral assertions. */
    private static void preparePassives(GameTestHelper h,List<Fixture> fixtures){
        for(var f:fixtures)if(Set.of("xuanchen","qingluo","chixiao").contains(f.skill.role())&&f.skill.index()<4
                ||f.skill.getId().equals("shuo_qiaofan")||f.skill.getId().equals("g1_tianxing_n2")||f.skill.getId().equals("g1_fenjue_n2")){
            var p=f.actor.player;var d=PlayerDataProvider.get(p);AbilityRuntime.clear(p);d.refundAllSkillPointsForTraining();
            p.setPos(f.origin);p.setYRot(0);p.setXRot(0);p.setHealth(1000);p.damageCooldownTime=0;f.ally.player.setHealth(1000);f.enemy.player.setHealth(10000);
            ModShieldSystem.clearFor(p);ModShieldSystem.clearFor(f.ally.player);f.enemy.player.damageCooldownTime=0;
            var passive=FirstGroupSkills.node(f.skill.role(),f.skill.index()-2);receive(h,p,new NetworkSetup.UpgradeSkillPayload(passive.getId()));
            h.assertTrue(d.skillLevel(passive.getId())==1,"Passive transaction uses formally learned rank: "+passive.getId());
            var first=AbilityRuntime.context(p,FirstGroupSkills.node(f.skill.role(),2));
            switch(passive.getId()){
                case "shuo_jianqie" -> {
                    var guard=FirstGroupSkills.node("shuo",3);receive(h,p,new NetworkSetup.UpgradeSkillPayload(guard.getId()));
                    d.setMana(700);d.setCooldown(guard.getId(),0);f.enemy.player.setPos(f.origin.add(0,0,2));
                    receive(h,p,new NetworkSetup.CastSkillPayload(guard.slot(),0,0));DamageCalc.dealDamage(f.enemy.player,p,100,DamageCalc.Kind.PHYSICAL);
                    h.assertTrue(d.getBuff("shuo_qiaofan_recast")>0,"Fresh actual frontal damage opens the passive's riposte window");
                    float hp=f.enemy.player.getHealth();basic(h,f);
                    h.assertTrue(hp-f.enemy.player.getHealth()>131.7&&hp-f.enemy.player.getHealth()<138.3,"JianQie actual basic attack consumes exactly one 70-point post-parry draw bonus plus full base attack; damage="+(hp-f.enemy.player.getHealth())+" drawn="+ShuoCombat.drawn(p)+" blocked="+ShuoCombat.blocked(p)+" guard="+d.getBuff("shuo_qiaofan_recast")+" attack="+p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK));
                }
                case "g1_tianxing_pb" -> {
                    boolean hit=first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);h.assertTrue(d.getBuff(passive.getId())>0,"Skill hit on a legal hero readies Thunder Resonance; hit="+hit+" hp="+f.enemy.player.getHealth()+" rank="+d.skillLevel(passive.getId())+" until="+AbilityRuntime.context(p,passive).memory().until+" cooldown="+AbilityRuntime.context(p,passive).memory().cooldown);
                    f.enemy.player.setPos(f.origin.add(0,0,2));float hp=f.enemy.player.getHealth();f.enemy.player.damageCooldownTime=0;basic(h,f);
                    h.assertTrue(hp-f.enemy.player.getHealth()>105.7&&hp-f.enemy.player.getHealth()<112.3&&d.getBuff(passive.getId())==0,"Actual basic attack consumes Resonance for exactly 44 bonus magic damage plus full base attack");
                }
                case "g1_fenjue_pb" -> {
                    first.damage(f.enemy.player,100,DamageCalc.Kind.MAGIC);h.assertTrue(Math.abs(10000-f.enemy.player.getHealth()-110)<.02,"Burning Last Stand boosts an actual low-health skill transaction by ten percent");
                    f.enemy.player.damageCooldownTime=0;AbilityRuntime.context(p,FirstGroupSkills.node("fenjue",7)).damage(f.enemy.player,100,DamageCalc.Kind.MAGIC);
                    h.assertTrue(Math.abs(10000-f.enemy.player.getHealth()-210)<.02,"A distinct cast cannot bypass Last Stand internal cooldown");
                }
                case "g1_xuanchen_pa" -> {
                    receive(h,p,new NetworkSetup.UpgradeSkillPayload(first.skill.getId()));d.setCooldown(first.skill.getId(),0);d.setMana(700);
                    receive(h,p,new NetworkSetup.CastSkillPayload(first.skill.slot(),0,0));
                    h.assertTrue(Math.abs(ModShieldSystem.value(f.ally.player)-47.5)<.02,"Array Guardian shields the lowest-health legal ally on an actual cast");
                }
                case "g1_xuanchen_pb" -> {
                    first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);f.enemy.player.damageCooldownTime=0;
                    AbilityRuntime.context(p,FirstGroupSkills.node("xuanchen",7)).damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);
                    h.assertTrue(Math.abs(10000-f.enemy.player.getHealth()-64)<.02,"Seal Resonance adds exactly 44 for a second distinct skill transaction");
                }
                case "g2_qingluo_pa" -> {
                    first.heal(f.ally.player,10);h.assertTrue(Math.abs(f.ally.player.getHealth()-1035.4)<.02,"Kind Herbalist adds 25.4 to actual low-health allied healing");
                    first.heal(f.ally.player,10);h.assertTrue(Math.abs(f.ally.player.getHealth()-1045.4)<.02,"Kind Herbalist internal cooldown prevents a second bonus");
                }
                case "g2_qingluo_pb" -> {
                    first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);first.heal(f.ally.player,10);
                    h.assertTrue(Math.abs(f.ally.player.getHealth()-1025)<.02&&d.getBuff(passive.getId()+"_ready")==0,"Herbal Essence consumes one effective-hit stack for exactly 15 bonus healing");
                }
                case "g2_chixiao_pa" -> first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);
                case "g2_chixiao_pb" -> {
                    first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);
                    h.assertTrue(Math.abs(ModShieldSystem.value(p)-92)<.02,"Unbroken Will creates its exact low-health shield after an actual effective hit");
                    ModShieldSystem.consume(p,92);f.enemy.player.damageCooldownTime=0;first.damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);
                    h.assertTrue(ModShieldSystem.value(p)==0,"Unbroken Will internal cooldown blocks a second shield");
                }
                default -> throw new IllegalStateException(passive.getId());
            }
        }
    }
    private static void basic(GameTestHelper h,Fixture f){try{
        var ticker=LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);ticker.setInt(f.actor.player,1000);
        f.actor.player.getRandom().setSeed(28);
        var buf=Unpooled.buffer();try{
            var packet=new net.minecraft.network.protocol.game.ServerboundAttackPacket(f.enemy.player.getId());
            net.minecraft.network.protocol.game.ServerboundAttackPacket.STREAM_CODEC.encode(buf,packet);
            f.actor.player.connection.handleAttack(net.minecraft.network.protocol.game.ServerboundAttackPacket.STREAM_CODEC.decode(buf));
            h.assertTrue(!buf.isReadable(),"Vanilla 26.3 attack packet codec is fully consumed");
        }finally{buf.release();}
    }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static void passiveTick(GameTestHelper h,List<Fixture> fixtures,int age){
        for(var f:fixtures)if(f.skill.getId().equals("g2_chixiao_n1")&&(age==5||age==10||age==15)){
            f.enemy.player.damageCooldownTime=0;
            AbilityRuntime.context(f.actor.player,FirstGroupSkills.node("chixiao",age==5?3:age==10?5:6)).damage(f.enemy.player,10,DamageCalc.Kind.MAGIC);
            if(age==10)h.assertTrue(PlayerDataProvider.get(f.actor.player).getBuff("g2_chixiao_pa_ready")>0,"Three spaced effective hero hits ready Battle Intent");
            if(age==15)h.assertTrue(Math.abs(10000-f.enemy.player.getHealth()-72.5)<.02
                    &&PlayerDataProvider.get(f.actor.player).getBuff("g2_chixiao_pa_ready")==0,"Fourth distinct transaction consumes three stacks for exactly 32.5 true damage");
        }
    }
    private static void verifySaved(GameTestHelper h,Fixture f){
        var problems=new net.minecraft.util.ProblemReporter.Collector();
        var out=net.minecraft.world.level.storage.TagValueOutput.createWithContext(problems,h.getLevel().registryAccess());f.actor.player.saveWithoutId(out);
        var restored=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(problems,h.getLevel().registryAccess(),out.buildResult()));
        var data=PlayerDataProvider.get(restored);
        h.assertTrue(problems.isEmpty()&&data.getCharacterId().equals(f.skill.role())&&data.skillLevel(f.skill.getId())==1&&data.getSkillPoints()==19,
                "Native Fabric entity attachment save/load keeps paid branch: "+f.skill.getId());restored.discard();
    }
    private static Fixture fixture(GameTestHelper h,int index,AbilitySkill skill){
        var f=new Fixture();f.skill=skill;var floor=h.absolutePos(new BlockPos(-180+(index%7)*30,100,(index/7)*32));
        for(int x=-5;x<=5;x++)for(int z=-5;z<=16;z++)for(int y=0;y<=5;y++){
            var at=floor.offset(x,y,z);f.blocks.put(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());}
        f.origin=Vec3.atBottomCenterOf(floor.above());f.actor=actor(h,f.origin,false);f.enemy=actor(h,f.origin.add(0,0,3),true);f.ally=actor(h,f.origin.add(.8,0,2.5),true);
        PlayerDataProvider.get(f.enemy.player).setFaction(Faction.DESTRUCTION);PlayerDataProvider.get(f.ally.player).setFaction(Faction.GUARDIAN);
        f.enemy.player.setHealth(10000);
        f.ally.player.setHealth(4000);return f;
    }
    private static Actor actor(GameTestHelper h,Vec3 at,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var packets=new ArrayList<CustomPacketPayload>();
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom){
                var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{
                    ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(buf,custom);packets.add(ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(buf).payload());
                    h.assertTrue(!buf.isReadable(),"Production S2C payload fully consumed");}finally{buf.release();}}}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());p.setPos(at);p.setNoGravity(true);p.setYRot(0);p.setXRot(0);prepare(p);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);if(add)h.getLevel().addNewPlayer(p);
        return new Actor(p,channel,packets);
    }
    private static void prepare(ServerPlayer p){var d=PlayerDataProvider.get(p);d.setMana(700);d.setModMaxHealth(10000);d.setModHealthInitialized(true);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);p.setHealth(4000);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(700);p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30);
        p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);p.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);}
    private static void aim(ServerPlayer p,boolean ground){p.setYRot(0);p.setXRot(ground?(float)Math.toDegrees(Math.atan2(p.getEyeHeight(),4)):0);}
    private static void receive(GameTestHelper h,ServerPlayer p,CustomPacketPayload payload){
        ShuoPilotChecks.receive(h,p,payload);
    }
}
