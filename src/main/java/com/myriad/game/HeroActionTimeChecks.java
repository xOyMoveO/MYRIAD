package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real input + real rank-seven fields, paired with mu=1; server behaviour only. */
final class HeroActionTimeChecks {
    private HeroActionTimeChecks(){}
    private record Scenario(String role,int skill,double distance,double point,boolean drawn,String kind){}
    private static Scenario hit(String role,int skill,double distance){return new Scenario(role,skill,distance,0,false,"attack");}
    private static Scenario point(String role,int skill,double distance,double point){return new Scenario(role,skill,distance,point,false,"attack");}
    private static final List<Scenario> SCENARIOS=scenarios();
    private static List<Scenario> scenarios(){var all=new ArrayList<>(List.of(
        hit("yingxiao",2,8),hit("yingxiao",4,3),point("yingxiao",5,3,7),hit("yingxiao",6,3),
        hit("tianxing",3,8),hit("tianxing",5,8),hit("tianxing",7,3),
        hit("xuanchen",2,8),hit("xuanchen",6,2),
        hit("sulan",2,3),hit("sulan",5,8),hit("sulan",6,5),
        hit("lingyuan",2,3),hit("lingyuan",4,8),hit("lingyuan",7,8),
        hit("hanze",2,8),hit("hanze",5,8),point("hanze",4,3,3),
        hit("qingluo",3,8),hit("chixiao",3,3),hit("chixiao",5,8),point("chixiao",6,6,6),
        hit("yunsheng",2,8),point("yunsheng",6,3,3),new Scenario("yunsheng",5,12,6,false,"motion"),
        hit("fenjue",2,3),hit("fenjue",3,3),hit("fenjue",5,3),point("fenjue",6,6,6),
        hit("shuo",4,3),hit("shuo",5,3),hit("shuo",6,3),hit("shuo",7,3),
        new Scenario("shuo",5,3,0,true,"attack"),new Scenario("shuo",2,2,0,true,"attack"),
        new Scenario("shuo",4,3,0,true,"attack"),new Scenario("shuo",6,8,0,true,"attack"),new Scenario("shuo",7,3,0,true,"attack"),
        new Scenario("shuo",2,12,0,false,"motion"),new Scenario("shuo",7,3,0,false,"chase"),
        new Scenario("chixiao",6,6,6,false,"forced"),
        new Scenario("shuo",2,12,0,true,"sheath"),
        new Scenario("tianxing",6,3,3,false,"real"),new Scenario("xuanchen",7,3,3,false,"real"),
        new Scenario("fenjue",4,3,3,false,"real"),new Scenario("yunsheng",3,3,0,false,"real"),
        new Scenario("fenjue",2,3,0,false,"boundary"),new Scenario("fenjue",2,3,0,false,"cancel"),
        new Scenario("shuo",5,3,0,false,"interrupt"),new Scenario("fenjue",2,3,0,false,"needle"),
        new Scenario("fenjue",2,3,0,false,"ally"),new Scenario("fenjue",2,3,0,false,"dot")));
        for(var role:List.of("yingxiao","tianxing","xuanchen","sulan","lingyuan","hanze","qingluo","chixiao","yunsheng","fenjue","shuo"))all.add(new Scenario(role,2,Set.of("xuanchen","hanze","qingluo","yunsheng").contains(role)?8:2,0,false,"basic"));
        return List.copyOf(all);
    }
    private static final class Fixture {
        Scenario scenario;boolean slow;ServerPlayer actor,target,clock;Vec3 origin;
        float lastHealth;final List<Integer> hits=new ArrayList<>();final List<Float> damage=new ArrayList<>();
        int motionEnd=-1,sheathEnd=-1,recasts,initialCd;boolean hadMotion,wasDrawn,cancelled;float mana;
        final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();
    }
    static void run(GameTestHelper h){
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled(),nc=TrainingMode.isNoCooldown(),nm=TrainingMode.isNoMana();
        boolean madeManager=GameManager.get()==null;if(madeManager)GameManager.create();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        var fs=new ArrayList<Fixture>();boolean[] done={false};
        Runnable cleanup=()->{for(var f:fs){for(var p:List.of(f.actor,f.target,f.clock)){AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);com.myriad.combat.KnockbackBuffer.clear(p.getUUID());
                    h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
                f.blocks.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));}
            ModState.setEnabled(enabled);TrainingMode.setEnabled(training);TrainingMode.setNoCooldown(nc);TrainingMode.setNoMana(nm);if(madeManager)GameManager.destroy();};
        try {
            for(int i=0;i<SCENARIOS.size();i++)for(int slow=0;slow<2;slow++){
                var f=fixture(h,i,slow==1);fs.add(f);
                if(f.slow&&!f.scenario.kind.equals("needle")){var d=PlayerDataProvider.get(f.clock);d.setSkillLevel(FirstGroupSkills.node("keheng",4).getId(),7);
                    aim(f.clock,6);SkillSystem.cast(f.clock,2);AbilityRuntime.confirm(f.clock);}
            }
            long start=h.getLevel().getGameTime();
            java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> listener=new java.util.function.Consumer<>(){
                public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(done[0])return;
                    int age=(int)(h.getLevel().getGameTime()-start),action=age-12;
                    try{
                        for(var f:fs){
                            f.target.invulnerableTime=0;f.target.setDeltaMovement(Vec3.ZERO);
                            if(f.scenario.kind.equals("basic")){KehengTimeFields.refreshActor(f.actor);f.actor.doTick();}
                            if(AbilityRuntime.moving(f.actor))f.actor.move(MoverType.SELF,f.actor.getDeltaMovement());
                            AbilityRuntime.tick(f.clock);AbilityRuntime.tick(f.actor);
                            if(f.scenario.kind.equals("basic"))com.myriad.combat.MagicBullet.tickBullets(f.actor,PlayerDataProvider.get(f.actor));
                            // Mock transports have no connection tick; drive the existing real-time data consumer once.
                            if(f.scenario.kind.equals("dot")&&action>0)PlayerDataProvider.get(f.actor).tickCooldowns();
                            if(age==9&&Set.of("boundary","needle").contains(f.scenario.kind)&&f.slow){f.clock.setPos(f.origin.add(0,0,-3));f.clock.setXRot(0);PlayerDataProvider.get(f.clock).setSkillLevel(FirstGroupSkills.node("keheng",2).getId(),1);SkillSystem.cast(f.clock,0);}
                            if(age==12){
                                double rate=ActionTime.multiplier(f.actor.getUUID(),h.getLevel().getGameTime());
                                h.assertTrue(Math.abs(rate-(f.slow&&!Set.of("needle","ally").contains(f.scenario.kind)?.5:1))<.0001,"Actual field before "+label(f)+"; rate="+rate);
                                // Opening damage is already asserted by KehengChecks; equalize health for
                                // missing-health finishers before comparing only this actor's damage.
                                f.target.setHealth(10000);f.lastHealth=f.target.getHealth();
                                if(f.scenario.drawn)ShuoCombat.draw(f.actor,com.myriad.network.ShuoViewPayload.CUT_LEFT,1);
                                if(f.scenario.kind.equals("chase"))f.actor.setShiftKeyDown(true);
                                if(f.scenario.kind.equals("sheath")){h.assertTrue(ShuoCombat.requestSheathe(f.actor),"Actual manual sheathe accepted");f.wasDrawn=true;}
                                else if(f.scenario.kind.equals("basic")){attackTicker(f.actor,1000);basic(f);}
                                else cast(f,h);
                                sample(f,0);
                            }
                            if(action>0){
                                if(f.scenario.kind.equals("basic")&&f.hits.size()<2)basic(f);
                                sample(f,action);
                                if(f.scenario.kind.equals("boundary")&&f.slow&&(action==4||action==6||action==8))f.actor.setPos(f.origin.add(action==6?Vec3.ZERO:new Vec3(8,0,0)));
                                if(f.scenario.kind.equals("cancel")&&!f.cancelled&&!f.hits.isEmpty()){AbilityRuntime.clear(f.actor);f.cancelled=true;}
                                if(f.scenario.kind.equals("interrupt")&&action==3)PlayerDataProvider.get(f.actor).setBuff("g1_stun",2);
                                if(f.scenario.kind.equals("interrupt")&&action==4)PlayerDataProvider.get(f.actor).setBuff("g1_stun",0);
                                if(f.scenario.kind.equals("forced")&&action==3)h.assertTrue(com.myriad.combat.KnockbackBuffer.apply(f.actor,new Vec3(.5,.2,0)),"Real forced impulse is accepted during autonomous leap");
                                if(f.scenario.kind.equals("forced")&&action==4)h.assertTrue(!AbilityRuntime.moving(f.actor)&&f.actor.getDeltaMovement().distanceTo(new Vec3(.5,.2,0))<.0001,"Forced displacement cancels local arc without scaling the impulse");
                                if(f.scenario.kind.equals("boundary")&&f.slow&&action==1){
                                    AbilityRuntime.context(f.clock,FirstGroupSkills.node("keheng",2)).slow(f.actor,.2f,30);
                                }
                                if(f.scenario.kind.equals("boundary")&&f.slow&&action==2)h.assertTrue(Math.abs(f.actor.getAttributeValue(Attributes.MOVEMENT_SPEED)/f.actor.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()-.5)<.001,"Actual field and ordinary .8 limit take .5, not .4");
                                if(f.scenario.kind.equals("boundary")&&f.slow&&action==6)h.assertTrue(Math.abs(ActionTime.multiplier(f.actor.getUUID(),h.getLevel().getGameTime())-.85)<.001,"Real needle retains its own rate after exit, with earlier attack work preserved; rate="+ActionTime.multiplier(f.actor.getUUID(),h.getLevel().getGameTime()));
                                if(f.scenario.kind.equals("boundary")&&f.slow&&action==7){h.assertTrue(ActionTime.multiplier(f.actor.getUUID(),h.getLevel().getGameTime())==.5,"Needle and re-entered field take strongest .5");
                                    AbilityRuntime.context(f.clock,FirstGroupSkills.node("keheng",2)).slow(f.actor,.7f,30);KehengTimeFields.refreshActor(f.actor);
                                    h.assertTrue(Math.abs(f.actor.getAttributeValue(Attributes.MOVEMENT_SPEED)/f.actor.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()-.3)<.001,"Stronger ordinary .3 limit is not multiplied by field");}
                                if(f.scenario.kind.equals("dot")&&action==20)h.assertTrue(PlayerDataProvider.get(f.actor).getCooldown(FirstGroupSkills.node("fenjue",2).getId())==f.initialCd-20,"Actual cooldown consumes real ticks while the slash is slowed; "+label(f)+" initial="+f.initialCd+" now="+PlayerDataProvider.get(f.actor).getCooldown(FirstGroupSkills.node("fenjue",2).getId())+" playerTicks="+f.actor.tickCount);
                                if(f.scenario.kind.equals("ally")&&f.slow&&action==3)h.assertTrue(AbilityRuntime.projectileRate(f.clock,UUID.randomUUID(),f.origin.add(0,1,0),h.getLevel().getGameTime())==1&&AbilityRuntime.projectileRate(f.actor,UUID.randomUUID(),f.origin.add(0,1,0),h.getLevel().getGameTime())==1,"Actual field exempts owner and allied independent flights");
                                if(f.scenario.role.equals("shuo")&&f.scenario.skill==7&&f.recasts<2&&!ShuoCombat.blocked(f.actor)){
                                    var skill=FirstGroupSkills.node("shuo",7);if(skill.recastAvailable(AbilityRuntime.context(f.actor,skill))){SkillSystem.cast(f.actor,3);f.recasts++;}}
                                if(AbilityRuntime.moving(f.actor))f.hadMotion=true;
                                else if(f.hadMotion&&f.motionEnd<0)f.motionEnd=action;
                                if(f.wasDrawn&&!ShuoCombat.drawn(f.actor)&&f.sheathEnd<0)f.sheathEnd=action;
                            }
                        }
                        if(action==120){
                            for(int i=0;i<fs.size();i+=2)trace(fs.get(i),fs.get(i+1));
                            for(int i=0;i<fs.size();i+=2)verify(h,fs.get(i),fs.get(i+1));
                            System.out.println("[MYRIAD-HERO-ACTION-TIME] PASS: "+fs.size()+" actual-cast paired scenarios; 11 retained roles, actual .5 fields vs mu1, damage timelines/motion distances, private slash/draw/follow/sheathe, real-time warnings and lifecycle");
                            done[0]=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();h.succeed();
                        }
                    }catch(Throwable failure){done[0]=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();throw new IllegalStateException(failure);}
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        }catch(Throwable failure){cleanup.run();throw new IllegalStateException(failure);}
    }
    private static void cast(Fixture f,GameTestHelper h){
        var s=FirstGroupSkills.node(f.scenario.role,f.scenario.skill);var d=PlayerDataProvider.get(f.actor);d.setSkillLevel(s.getId(),1);
        if(f.scenario.kind.equals("dot"))d.setSkillLevel(FirstGroupSkills.node("fenjue",0).getId(),1);
        if(f.scenario.point>0)aim(f.actor,f.scenario.point);
        f.mana=d.getMana();SkillSystem.cast(f.actor,s.slot());if(s.needsPoint())AbilityRuntime.confirm(f.actor);
        h.assertTrue(d.getMana()==f.mana-s.getManaCost(),"Real input commits one cost: "+label(f));
        f.initialCd=d.getCooldown(s.getId());
        f.actor.setXRot(0);
    }
    private static void sample(Fixture f,int action){float health=f.target.getHealth();if(health<f.lastHealth-.01){f.hits.add(action);f.damage.add(f.lastHealth-health);}f.lastHealth=health;}
    private static float total(Fixture f){return (float)f.damage.stream().mapToDouble(Float::doubleValue).sum();}
    private static void trace(Fixture baseline,Fixture slow){
        System.out.println("[MYRIAD-HERO-TRACE] "+label(baseline)+" base="+baseline.hits+"/"+baseline.damage+" slow="+slow.hits+"/"+slow.damage+" motion="+baseline.motionEnd+"/"+slow.motionEnd+" positions="+baseline.actor.position().subtract(baseline.origin)+"/"+slow.actor.position().subtract(slow.origin)+" sheath="+baseline.sheathEnd+"/"+slow.sheathEnd);
    }
    private static void verify(GameTestHelper h,Fixture baseline,Fixture slow){
        String kind=baseline.scenario.kind;
        if(kind.equals("interrupt")){h.assertTrue(baseline.hits.isEmpty()&&slow.hits.isEmpty(),"Brief control cancels private windup permanently");return;}
        if(kind.equals("forced")){h.assertTrue(baseline.hits.isEmpty()&&slow.hits.isEmpty(),"Interrupted leap never replays a landing attack");return;}
        if(kind.equals("cancel")){h.assertTrue(baseline.hits.size()==1&&slow.hits.size()==1&&Math.abs(total(slow)-total(baseline))<.1,"Clearing after first real slash cancels the outstanding second slash");return;}
        if(kind.equals("basic")){h.assertTrue(baseline.hits.size()==2&&slow.hits.size()==2&&Math.abs(total(baseline)-total(slow))<.1,"Two actual baseline attacks, blocked inputs never create weak/extra damage: "+label(slow));
            if(com.myriad.combat.MagicBullet.isMage(baseline.actor)){float expected=baseline.scenario.role.equals("hanze")?156:130;h.assertTrue(Math.abs(total(baseline)-expected)<.1&&slow.hits.getFirst()>baseline.hits.getFirst(),"Mage's actual damage and independent far flight are preserved");}
            else for(var damage:baseline.damage)h.assertTrue(damage>=65*.95-.01&&damage<=65*1.05+.01,"Existing retained melee random roll remains full damage, never weak recovery damage");
            h.assertTrue(slow.hits.getLast()-slow.hits.getFirst()>=(baseline.hits.getLast()-baseline.hits.getFirst())*2-1,"Actual Player.tick preserves all eleven roles' basic recovery: "+label(slow));return;}
        if(kind.equals("sheath")){h.assertTrue(baseline.sheathEnd==8&&slow.sheathEnd==16,"Private sheathe consumes eight action ticks; "+label(slow));return;}
        if(kind.equals("motion")){h.assertTrue(baseline.motionEnd>0&&slow.motionEnd>baseline.motionEnd,"Support motion is slowed: "+label(slow));}
        else {
            h.assertTrue(!baseline.hits.isEmpty()&&!slow.hits.isEmpty(),"Both real casts hit: "+label(slow));
            h.assertTrue(Math.abs(total(baseline)-total(slow))<.15,"Same actual total damage, no catch-up/replay: "+label(slow)+" base="+total(baseline)+" slow="+total(slow));
            if(kind.equals("real")||kind.equals("ally"))h.assertTrue(baseline.hits.equals(slow.hits),"Committed warning/effect or allied action retains real ticks: "+label(slow));
            else h.assertTrue(slow.hits.getLast()>baseline.hits.getLast(),"Unfinished attack advances more slowly: "+label(slow));
            if(kind.equals("dot"))for(var f:List.of(baseline,slow))h.assertTrue(f.hits.size()==5&&f.hits.get(3)-f.hits.get(2)==20&&f.hits.get(4)-f.hits.get(3)==20,"Burn remains three real twenty-tick jumps despite action slow");
        }
        if(baseline.motionEnd>0){
            h.assertTrue(slow.motionEnd>baseline.motionEnd,"Autonomous motion takes more real ticks: "+label(slow));
            var a=baseline.actor.position().subtract(baseline.origin);var b=slow.actor.position().subtract(slow.origin);
            h.assertTrue(a.distanceTo(b)<.15,"Autonomous endpoint/distance retained: "+label(slow)+" base="+a+" slow="+b);
        }
        AbilityRuntime.clear(slow.actor);h.assertTrue(!AbilityRuntime.moving(slow.actor)&&!AbilityRuntime.channeling(slow.actor)&&!ShuoCombat.blocked(slow.actor),"Normal lifecycle removes action state: "+label(slow));
    }
    private static String label(Fixture f){return f.scenario.role+"/"+f.scenario.skill+(f.scenario.drawn?" drawn":"")+" "+f.scenario.kind;}
    private static void aim(ServerPlayer p,double distance){p.setYRot(0);p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeHeight(),distance)));}
    private static Fixture fixture(GameTestHelper h,int i,boolean slow){
        var f=new Fixture();f.scenario=SCENARIOS.get(i);f.slow=slow;var floor=h.absolutePos(new BlockPos(-150+i*24,100,slow?55:10));
        for(int x=-4;x<=4;x++)for(int z=-7;z<=16;z++)for(int y=0;y<=12;y++){
            var pos=floor.offset(x,y,z);f.blocks.put(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());}
        f.origin=Vec3.atBottomCenterOf(floor.above());f.actor=player(h,f.origin,f.scenario.role,Set.of("basic","needle","boundary","dot").contains(f.scenario.kind));
        f.target=player(h,f.origin.add(0,0,f.scenario.distance),"shuo",true);f.clock=player(h,f.origin.add(0,0,-6),"keheng",false);
        if(f.scenario.kind.equals("ally")){PlayerDataProvider.get(f.actor).setFaction(Faction.XUANXIAO);PlayerDataProvider.get(f.clock).setFaction(Faction.XUANXIAO);}
        return f;
    }
    private static void attackTicker(ServerPlayer p,int ticks){try{var field=LivingEntity.class.getDeclaredField("attackStrengthTicker");field.setAccessible(true);field.setInt(p,ticks);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static void basic(Fixture f){if(com.myriad.combat.MagicBullet.isMage(f.actor))com.myriad.combat.MagicBullet.tryFire(f.actor,f.actor.getMainHandItem());else {f.actor.getRandom().setSeed(28);f.actor.attack(f.target);}}
    private static ServerPlayer player(GameTestHelper h,Vec3 pos,String role,boolean add){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
            net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
        p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(pos);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setCharacterId(role);d.setLevel(20);d.setMana(700);d.setModMaxHealth(10000);d.setModHealthInitialized(true);p.setHealth(10000);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(700);p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30);
        p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.getAttribute(ModAttributes.PHYSICAL_ATTACK).setBaseValue(65);p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1);
        p.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(ModItems.initialWeaponItem(role)));
        p.getMainHandItem().set(net.minecraft.core.component.DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);if(add)h.getLevel().addNewPlayer(p);return p;
    }
}
