package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.character.skills.keheng.*;
import com.myriad.combat.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Production input, actual health/shields, local progress, real-time fields and lifecycle in a new world. */
final class KehengChecks {
    private KehengChecks(){}
    private static final class Fixture {ServerPlayer p,t;Vec3 origin;final List<ServerPlayer> players=new ArrayList<>();final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();}
    static void run(GameTestHelper h){
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled(),noMana=TrainingMode.isNoMana(),noCd=TrainingMode.isNoCooldown();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);TrainingMode.setNoMana(false);TrainingMode.setNoCooldown(false);
        var fixtures=new ArrayList<Fixture>();boolean[] done={false};
        Runnable cleanup=()->{for(var f:fixtures){for(var p:f.players){AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}f.blocks.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));}
            ModState.setEnabled(mod);TrainingMode.setEnabled(training);TrainingMode.setNoMana(noMana);TrainingMode.setNoCooldown(noCd);};
        try {
            identity(h);
            for(int i=0;i<30;i++)fixtures.add(fixture(h,i));
            for(int i=0;i<fixtures.size();i++){
                var f=fixtures.get(i);var d=PlayerDataProvider.get(f.p);
                switch(i){
                    case 0->{learn(f,2,1);SkillSystem.cast(f.p,0);h.assertTrue(d.getMana()==660&&d.getCooldown("keheng_chizhen")==140,"Q charges at the normal accepted-cast boundary");}
                    case 1->{learn(f,2,5);f.t.getAttribute(ModAttributes.TENACITY).setBaseValue(60);SkillSystem.cast(f.p,0);}
                    case 2->{learn(f,2,1);SkillSystem.cast(f.p,0);d.setBuff("g1_stun",2);}
                    case 3->{learn(f,3,2);learn(f,1,5);SkillSystem.cast(f.p,1);h.assertTrue(d.getMana()==655&&Math.abs(ModShieldSystem.value(f.p)-193.5)<.05,"W and actual field entry grant independent rank-2 W/rank-5 passive shields");}
                    case 4->{learn(f,4,7);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);d.setBuff("g1_stun",100);}
                    case 5->{learn(f,6,3);point(f,2);SkillSystem.cast(f.p,3);AbilityRuntime.confirm(f.p);}
                    case 6->{learn(f,5,7);SkillSystem.cast(f.p,2);}
                    case 7->{learn(f,5,1);SkillSystem.cast(f.p,2);}
                    case 8->{learn(f,5,1);SkillSystem.cast(f.p,2);}
                    case 9->{learn(f,7,3);SkillSystem.cast(f.p,3);}
                    case 10->{learn(f,7,3);SkillSystem.cast(f.p,3);d.setBuff("g1_stun",5);}
                    case 11->{learn(f,7,3);SkillSystem.cast(f.p,3);f.p.setYRot(90);}
                    case 12->{learn(f,7,3);SkillSystem.cast(f.p,3);f.t.setPos(f.origin.add(2,0,2));}
                    case 13->{wall(f,h,0,1,1);wall(f,h,0,2,1);wall(f,h,0,3,1);learn(f,7,3);SkillSystem.cast(f.p,3);}
                    case 14->{learn(f,6,1);point(f,2);SkillSystem.cast(f.p,3);AbilityRuntime.confirm(f.p);}
                    case 15->{learn(f,0,5);learn(f,2,3);learn(f,4,7);learn(f,7,3);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);SkillSystem.cast(f.p,0);}
                    case 16->{learn(f,4,7);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);}
                    case 17->{learn(f,5,1);SkillSystem.cast(f.p,2);}
                    case 18->{learn(f,4,1);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);}
                    case 19->{learn(f,4,1);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);}
                    case 20->{learn(f,2,1);SkillSystem.cast(f.p,0);PlayerDataProvider.get(f.t).setBuff("g1_control_immune",100);}
                    case 21->{learn(f,3,1);wall(f,h,0,1,1);wall(f,h,0,2,1);SkillSystem.cast(f.p,1);}
                    case 23->{learn(f,0,5);learn(f,2,1);ModShieldSystem.grant(f.t,"test_absorb",500,200);SkillSystem.cast(f.p,0);}
                    case 24->{learn(f,0,5);learn(f,2,1);f.t.setInvulnerable(true);SkillSystem.cast(f.p,0);}
                    case 25->{learn(f,0,5);learn(f,2,1);SkillSystem.cast(f.p,0);}
                    case 26->{learn(f,0,5);learn(f,2,1);learn(f,4,1);learn(f,6,1);SkillSystem.cast(f.p,0);}
                    case 27->{learn(f,1,5);learn(f,4,1);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);}
                    case 28->{
                        var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);ticker.setInt(f.p,200);
                        f.p.getAttribute(ModAttributes.PHYSICAL_ATTACK).setBaseValue(65);f.p.setXRot(0);f.p.setYRot(0);f.p.attack(f.t);
                        hp(h,f,9935,"Hengke left click deals exactly A65 physical damage through vanilla point targeting");
                        f.t.invulnerableTime=0;f.p.attack(f.t);hp(h,f,9935,"Unrecovered second left click cannot cause another hit");}
                    case 29->{learn(f,2,5);SkillSystem.cast(f.p,0);}
                    case 22->{learn(f,4,7);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);f.t.setPos(f.origin.add(0,5,2));}
                }
            }
            long start=h.getLevel().getGameTime();
            java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> listener=new java.util.function.Consumer<>(){
                public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(done[0])return;int age=(int)(h.getLevel().getGameTime()-start);
                    try{
                        if(AbilityRuntime.moving(fixtures.get(3).p))fixtures.get(3).p.move(MoverType.SELF,fixtures.get(3).p.getDeltaMovement());
                        for(var f:fixtures){AbilityRuntime.tick(f.p);KehengTimeFields.refreshActor(f.t);f.t.invulnerableTime=0;}
                        KehengTimeFields.tick(h.getLevel());
                        fixtures.get(29).t.doTick();AbilityRuntime.tick(fixtures.get(29).t);
                        fixtures.get(4).t.doTick();
                        if(age==2){var f=fixtures.get(29);var d=PlayerDataProvider.get(f.t);d.setCharacterId("tianxing");d.setSkillLevel(FirstGroupSkills.node("tianxing",3).getId(),1);f.t.setYRot(180);SkillSystem.cast(f.t,1);}
                        if(age==3)hp(h,fixtures.get(0),10000,"Q does not hit before its four action ticks finish");
                        if(age==8){hp(h,fixtures.get(0),9905,"Real Q swept projectile hits once for 95 at S30");rate(h,fixtures.get(0).t,.85,"Q applies action slow to actor");
                            hp(h,fixtures.get(2),10000,"Stun cancels an already paid Q windup without firing");rate(h,fixtures.get(20).t,1,"Control immune actor is not action slowed");
                            hp(h,fixtures.get(20),9905,"Control immunity does not remove legal skill damage");
                            h.assertTrue(Math.abs(fixtures.get(3).p.getZ()-fixtures.get(3).origin.z-4.5)<.06,"Actual W motion reaches 4.5 blocks without teleporting");}
                        if(age==9){
                            h.assertTrue(PlayerDataProvider.get(fixtures.get(23).p).getBuff("g1_keheng_marks")==1&&fixtures.get(23).t.getHealth()==10000,"Fully shield-absorbed Q gives exactly one effective hero mark");
                            h.assertTrue(PlayerDataProvider.get(fixtures.get(24).p).getBuff("g1_keheng_marks")==0,"Invulnerability gives no mark");
                            h.assertTrue(AbilityRuntime.channeling(fixtures.get(29).t),"A retained Tianxing unfinished windup stays active under real rank-5 needle slow");
                        }
                        if(age==10){var f=fixtures.get(4);hp(h,f,9798.5f,"Rank-7 boundary opens once after its real warning despite caster stun");rate(h,f.t,.5,"Rank-7 fixed field publishes 0.50");
                            f.p.setPos(f.origin.add(0,0,-8));
                            var counter=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");counter.setAccessible(true);counter.setInt(f.t,10);
                            var echo=fixtures.get(26);point(echo,2);SkillSystem.cast(echo.p,2);AbilityRuntime.confirm(echo.p);hp(h,fixtures.get(5),10000,"Dome has no immediate opening hit");
                            var test=fixtures.get(16);test.t.setPos(test.origin.add(0,0,2));
                            double field=AbilityRuntime.projectileRate(test.t,UUID.randomUUID(),test.origin.add(0,1,2),h.getLevel().getGameTime());
                            h.assertTrue(field==.5,"Independent enemy flight samples the actual field");
                            h.assertTrue(AbilityRuntime.projectileRate(test.p,UUID.randomUUID(),test.origin.add(0,1,2),h.getLevel().getGameTime())==1,"The field owner's flight keeps normal progress");
                            AbilityRuntime.cleanseControls(test.t);rate(h,test.t,1,"Cleanse removes same-tick field publication");
                            SkillSystem.cast(fixtures.get(7).p,2); // Active E toggles off at zero further cost.
                            PlayerDataProvider.get(fixtures.get(8).p).setBuff("g1_stun",2);
                            PlayerDataProvider.get(fixtures.get(17).p).setBuff("g1_root",80);
                        }
                        if(age==11){rate(h,fixtures.get(16).t,1,"Cleanse fixed immunity survives the next tick");var f=fixtures.get(18);wall(f,h,0,1,1);wall(f,h,0,2,1);wall(f,h,0,3,1);f.t.setPos(f.origin.add(0,0,0));
                            rate(h,fixtures.get(22).t,1,"Vertical radius excludes target five blocks above the center");}
                        if(age==12){h.assertTrue(!AbilityRuntime.channeling(fixtures.get(29).t),"Completed windup retains earlier progress and eventually releases once");}
                        if(age==14){
                            var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);
                            h.assertTrue(ticker.getInt(fixtures.get(4).t)==12,"Vanilla melee recovery preserves progress at mu0.5; ticker="+ticker.getInt(fixtures.get(4).t)+" rate="+ActionTime.multiplier(fixtures.get(4).t.getUUID(),h.getLevel().getGameTime()));
                            var f=fixtures.get(27);f.p.setPos(f.origin.add(0,0,-8));}
                        if(age==16){fixtures.get(27).p.setPos(fixtures.get(27).origin);hp(h,fixtures.get(9),9429.5f,"Rank-3 penetration completes exactly once for 570.5");
                            hp(h,fixtures.get(10),10000,"Hard control cancels penetration");hp(h,fixtures.get(11),9429.5f,"Penetration keeps its starting direction after camera turn");
                            hp(h,fixtures.get(12),10000,"Walking out of the three-block-wide corridor dodges");hp(h,fixtures.get(13),10000,"Solid wall truncates corridor damage");
                            var f=fixtures.get(15);point(f,2);SkillSystem.cast(f.p,2);AbilityRuntime.confirm(f.p);
                            h.assertTrue(fixtures.get(21).p.getZ()<fixtures.get(21).origin.z+.5,"W respects a wall on its first swept step");}
                        if(age==20){var f=fixtures.get(26);point(f,2);SkillSystem.cast(f.p,3);AbilityRuntime.confirm(f.p);}
                        if(age==18){rate(h,fixtures.get(1).t,1,"60% tenacity shortens Q to ten real ticks after impact");
                            var f=fixtures.get(19);f.t.setPos(f.origin.add(0,0,8));KehengTimeFields.refreshActor(f.t);rate(h,f.t,1,"Leaving the field restores next-tick action speed");
                            rate(h,fixtures.get(18).t,1,"Center-wall occlusion blocks field slow independently of caster visibility");}
                        if(age==23)hp(h,fixtures.get(5),9956.4f,"First dome pulse lands 0.5s after opening");
                        if(age==25){rate(h,fixtures.get(16).t,.5,"Fixed 0.75s cleanse immunity expires without deleting field");var f=fixtures.get(15);SkillSystem.cast(f.p,3);}
                        if(age==42){hp(h,fixtures.get(15),8481.5f,"S230 real Q231 + field291.5 + penetration840.5 + one echo155.5 =1518.5; no hidden multiplier");}
                        if(age==36){h.assertTrue(Math.abs(ModShieldSystem.value(fixtures.get(27).p)-144)<.05,"Re-entering own field does not stack or refresh Constant Step source shield");}
                        if(age==40){hp(h,fixtures.get(7),10000,"Manual following-field cancellation has no ending hit");hp(h,fixtures.get(8),10000,"Hard-control following cancellation has no ending hit");
                            var f=fixtures.get(14);AbilityRuntime.clear(f.p);}
                        if(age==44)h.assertTrue(PlayerDataProvider.get(fixtures.get(26).p).getBuff("g1_keheng_marks")==0,"Third distinct cast clears echo stacks");
                        if(age==51)h.assertTrue(ModShieldSystem.value(fixtures.get(27).p)==0,"Re-entry does not extend the original two-second shield");
                        if(age==55){hp(h,fixtures.get(17),9895,"External root permits natural following-field ending damage");}
                        if(age==73){hp(h,fixtures.get(5),9738.4f,"Dome performs exactly six 43.6 damage real-time pulses");
                            hp(h,fixtures.get(14),10000-(24+.12f*30)*2,"Lifecycle clearing cancels unspent dome pulses");}
                        if(age==74){hp(h,fixtures.get(6),9787,"Rank-7 following field naturally expires for 213 once");
                        }
                        if(age==75){var f=fixtures.get(25);PlayerDataProvider.get(f.p).setCooldown("keheng_chizhen",0);f.p.setXRot(0);SkillSystem.cast(f.p,0);}
                        if(age==86)h.assertTrue(PlayerDataProvider.get(fixtures.get(25).p).getBuff("g1_keheng_marks")==0,"The second hit does not renew the first hit's fixed four-second window");
                        if(age==94){hp(h,fixtures.get(26),9540.4f,"Multi-pulse dome adds one mark across all six jumps, including after three-stack clear");
                            h.assertTrue(PlayerDataProvider.get(fixtures.get(26).p).getBuff("g1_keheng_marks")==0,"Remaining jumps never re-enter a cleared passive window");
                            done[0]=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();
                            System.out.println("[MYRIAD-KEHENG] PASS: 30 real-cast scenarios; identity/growth/graph/save/melee, exact Q/W/field/follow/dome/corridor/echo resources and timings, actor/flight geometry, control/cleanse and lifecycle");h.succeed();}
                    }catch(Throwable failure){done[0]=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();throw new IllegalStateException(failure);}
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        }catch(Throwable failure){cleanup.run();throw new IllegalStateException(failure);}
    }
    private static void rate(GameTestHelper h,ServerPlayer p,double rate,String why){h.assertTrue(Math.abs(ActionTime.multiplier(p.getUUID(),p.level().getGameTime())-rate)<.00001,why);}
    private static void hp(GameTestHelper h,Fixture f,float hp,String why){h.assertTrue(Math.abs(f.t.getHealth()-hp)<.12,why+"; actual="+f.t.getHealth()+" expected="+hp);}
    private static void learn(Fixture f,int index,int rank){PlayerDataProvider.get(f.p).setSkillLevel(FirstGroupSkills.node("keheng",index).getId(),rank);}
    private static void point(Fixture f,double forward){f.p.setYRot(0);f.p.setXRot((float)Math.toDegrees(Math.atan2(f.p.getEyeHeight(),forward)));}
    private static void wall(Fixture f,GameTestHelper h,int x,int y,int z){var pos=BlockPos.containing(f.origin).offset(x,y-1,z);f.blocks.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
    private static Fixture fixture(GameTestHelper h,int i){
        var f=new Fixture();var floor=h.absolutePos(new BlockPos(-100+i*24,80,-45));
        for(int x=-5;x<=5;x++)for(int z=-5;z<=14;z++)for(int y=0;y<=6;y++){
            var pos=floor.offset(x,y,z);f.blocks.put(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());}
        f.origin=Vec3.atBottomCenterOf(floor.above());f.p=player(h,f.origin,"keheng",false);f.t=player(h,f.origin.add(0,0,2),"shuo",true);f.players.addAll(List.of(f.p,f.t));
        return f;
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 pos,String role,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
        p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(pos);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setCharacterId(role);d.setLevel(20);d.setMana(700);d.setModMaxHealth(10000);d.setModHealthInitialized(true);p.setHealth(10000);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(700);p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30);p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        p.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(ModItems.initialWeaponItem(role)));
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);
        p.getMainHandItem().set(net.minecraft.core.component.DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        if(add)h.getLevel().addNewPlayer(p);return p;
    }
    private static void identity(GameTestHelper h){
        var skills=FirstGroupSkills.candidates("keheng");h.assertTrue(skills.size()==8&&skills.stream().map(Object::getClass).distinct().count()==8,"Eight concrete independent Keheng modules");
        h.assertTrue(FirstGroupGrowth.health("keheng",1)==600&&FirstGroupGrowth.health("keheng",20)==2215,"Exact HP growth");
        h.assertTrue(Arrays.stream(CharacterType.playableValues()).noneMatch(t->t==CharacterType.KEHENG),"Production draft remains gated until integration");
        for(var skill:skills){var ranks=new HashMap<String,Integer>();int other=FirstGroupSkills.opposite(skill.index());
            if(other>=0){ranks.put(skills.get(other).getId(),1);h.assertTrue(FirstGroupSkills.learnError("keheng",ranks,20,20,skill.getId())!=null,"Exclusive branch rejects contradictory spend");}}
        var saved=new PlayerData();saved.setCharacterId("keheng");saved.setSkillLevel("keheng_chizhen",3);saved.setFirstGroupAttribute("wisdom",10);
        var tag=PlayerData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,saved).getOrThrow();var copy=PlayerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();
        h.assertTrue(copy.getCharacterId().equals("keheng")&&copy.skillLevel("keheng_chizhen")==3&&copy.firstGroupAttribute("wisdom")==10,"Valid Keheng state roundtrips persistence codec");
    }
}
