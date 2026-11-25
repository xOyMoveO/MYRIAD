package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.network.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Registered Fabric packet entry + externally observed branch behavior. Mock transports are not real clients. */
final class RosterBChecks {
    static final List<String> ROLES=List.of("yike","rivet","yingxiao","sulan","lingyuan","hanze","yunsheng");
    private static final List<String> LEGACY=List.of("yingxiao","sulan","lingyuan","hanze","yunsheng");
    private static final class Scene {
        String role;int index;ServerPlayer actor,target;Vec3 origin;float mana,loss;boolean root,slow,hadMotion;int firstHit=-1,motionEnd=-1;
        final List<CustomPacketPayload> packets=new ArrayList<>();
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
    }
    static void run(GameTestHelper h) {
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled(),nm=TrainingMode.isNoMana(),nc=TrainingMode.isNoCooldown(),ng=TrainingMode.isNoGoldCost();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);TrainingMode.setNoMana(false);TrainingMode.setNoCooldown(false);TrainingMode.setNoGoldCost(false);
        var scenes=new ArrayList<Scene>();boolean[] done={false};
        Runnable cleanup=()->{for(var s:scenes){for(var p:List.of(s.actor,s.target)){AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}s.blocks.forEach(h.getLevel()::setBlockAndUpdate);}ModState.setEnabled(mod);TrainingMode.setEnabled(training);TrainingMode.setNoMana(nm);TrainingMode.setNoCooldown(nc);TrainingMode.setNoGoldCost(ng);};
        try {
            var probe=new Scene();probe.actor=player(h,h.absoluteVec(new Vec3(4,2,4)),probe.packets,false);probe.target=player(h,probe.actor.position().add(0,0,3),new ArrayList<>(),false);scenes.add(probe);
            verifyLearningAndPersistence(h,probe.actor);
            AcceptedDamageCooldownChecks.verify(h,probe.actor,probe.target);
            AbilityRuntime.clear(probe.actor);
            int serial=0;
            for(String role:LEGACY)for(int index=0;index<8;index++)for(int pass=0;pass<(index==slowIndex(role)?2:1);pass++) {
                var s=new Scene();s.role=role;s.index=index;s.slow=pass==1;int n=serial++;
                // Keep every scene inside roster_b_empty's declared bounds so the
                // runner tracks its entity sections independently of random chunk alignment.
                var floor=h.absolutePos(new BlockPos(8+(n%3)*40,12+(n/3)*12,4));CombatChunks.prepare(h.getLevel(),floor);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=19;z++)for(int y=0;y<=5;y++){
                    var at=floor.offset(x,y,z);s.blocks.put(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
                }
                s.origin=Vec3.atBottomCenterOf(floor.above());
                s.actor=player(h,s.origin,s.packets,false);s.target=player(h,s.origin.add(0,0,role.equals("sulan")&&index==6?12:3),new ArrayList<>(),true);scenes.add(s);
                receive(h,s.actor,new NetworkSetup.ChooseCharacterPayload(role));var d=PlayerDataProvider.get(s.actor);d.setLevel(20);d.refundAllSkillPointsForTraining();
                receive(h,s.actor,new NetworkSetup.UpgradeSkillPayload(node(s).getId()));
                s.actor.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(0);d.setMana(700);s.mana=d.getMana();
                if(index<2){
                    if(role.equals("lingyuan")&&index==1)receive(h,s.actor,new NetworkSetup.UpgradeSkillPayload(FirstGroupSkills.node(role,3).getId()));
                    if(role.equals("yunsheng")&&index==0)receive(h,s.actor,new NetworkSetup.UpgradeSkillPayload(FirstGroupSkills.node(role,7).getId()));
                    if(role.equals("yunsheng")&&index==1)s.actor.setHealth(100);
                    continue;
                }

            }
            for(int tick=1;tick<=165;tick++){int age=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try {
                    for(var s:scenes){if(s.role==null)continue;
                        if(age%20==0)CombatChunks.hold(h.getLevel(),BlockPos.containing(s.origin));
                        if(age==1&&s.index>=2)startScene(h,s);
                        if(AbilityRuntime.moving(s.actor))s.actor.move(MoverType.SELF,s.actor.getDeltaMovement());
                        s.target.damageCooldownTime=0;
                        if(s.index<2)passiveStep(h,s,age);
                        AbilityRuntime.tick(s.actor);
                        if(s.index==0&&s.role.equals("hanze"))com.myriad.combat.MagicBullet.tickBullets(s.actor,PlayerDataProvider.get(s.actor));
                        s.root|=PlayerDataProvider.get(s.target).getBuff("g1_root")>0;
                        if(s.firstHit<0&&s.target.getHealth()<10000)s.firstHit=age;
                        if(AbilityRuntime.moving(s.actor))s.hadMotion=true;
                        else if(s.hadMotion&&s.motionEnd<0)s.motionEnd=age;
                        if(age==2&&s.index>=2)verifyImmediate(h,s);
                        if(age==32&&s.role.equals("yingxiao")&&s.index==5){
                            h.assertTrue(s.actor.position().distanceTo(s.origin)>2,"Return branch performs actual outward motion");float mana=PlayerDataProvider.get(s.actor).getMana();
                            receive(h,s.actor,new NetworkSetup.CastSkillPayload(2,0,0));
                            h.assertTrue(s.actor.position().distanceTo(s.origin)<.01&&PlayerDataProvider.get(s.actor).getMana()==mana,"Return second input blinks to its original anchor without a second payment");
                        }
                        if(age==165){if(s.index<2)verifyPassive(h,s);else verifyOutcome(h,s);}
                    }
                    if(age==165){verifyTimePairs(h,scenes);done[0]=true;cleanup.run();System.out.println("[MYRIAD-ROSTER-B] PASS: 7-role/56-candidate registered packet learning, branch/gate rejection, native attachment disk+clone, five-role 30 active-branch exact damage/shield/motion outcomes + ten actual passive hooks + five public-rate paired windup/motion scenarios and actual presentation packets; Yike/Rivet exhaustive scenarios separate; no native-client or visual acceptance");h.succeed();}
                }catch(Throwable fail){fail.printStackTrace();done[0]=true;cleanup.run();throw fail;}
            });}
        }catch(Throwable fail){fail.printStackTrace();cleanup.run();throw fail;}
    }
    private static void startScene(GameTestHelper h,Scene s){
        var d=PlayerDataProvider.get(s.actor);
        if(s.slow)ActionTime.apply(s.actor.getUUID(),s.actor.getUUID(),"roster-b-public-rate",.5,s.actor.level().getGameTime()+400);
        if(node(s).needsPoint())aim(s.actor,s.role.equals("yunsheng")&&s.index==5?6:3);
        receive(h,s.actor,new NetworkSetup.CastSkillPayload(node(s).slot(),0,0));
        if(node(s).needsPoint())receive(h,s.actor,new NetworkSetup.ConfirmPlacementPayload());
        h.assertTrue(d.getMana()==700-node(s).getManaCost()&&d.getCooldown(node(s).getId())==node(s).getCooldownTicks(1),"Production cast pays one exact base resource/CD: "+label(s)+" mana="+d.getMana()+" cd="+d.getCooldown(node(s).getId())+" target="+s.target.isAlive()+"/"+s.target.isRemoved()+" selected="+AbilityRuntime.context(s.actor,node(s)).target(14,false)+" level="+s.target.level().dimension());
        s.actor.setXRot(0);
    }
    private static AbilitySkill node(Scene s){return FirstGroupSkills.node(s.role,s.index);}
    private static String label(Scene s){return s.role+"/"+node(s).getId()+(s.slow?" mu=.5":" mu=1");}
    private static int slowIndex(String role){return switch(role){case "yingxiao"->4;case "sulan"->6;case "lingyuan"->2;case "hanze"->4;case "yunsheng"->5;default->-1;};}
    private static void verifyTimePairs(GameTestHelper h,List<Scene> scenes){
        for(var slow:scenes)if(slow.slow){var base=scenes.stream().filter(s->s.role!=null&&!s.slow&&s.role.equals(slow.role)&&s.index==slow.index).findFirst().orElseThrow();
            if(slow.role.equals("hanze"))h.assertTrue(slow.firstHit>base.firstHit,"Public .5 source delays the actual twenty-action-tick warning release: "+base.firstHit+"/"+slow.firstHit);
            else h.assertTrue(base.motionEnd>0&&slow.motionEnd>base.motionEnd&&base.actor.position().subtract(base.origin).distanceTo(slow.actor.position().subtract(slow.origin))<.15,"Public .5 source slows unfinished motion while retaining its endpoint: "+label(slow)+" end="+base.motionEnd+"/"+slow.motionEnd);
        }
    }
    private static void verifyImmediate(GameTestHelper h,Scene s){
        double shield=switch(s.role){case "yingxiao"->s.index==7?306:0;case "lingyuan"->s.index==3?100:0;case "hanze"->s.index==3?150:s.index==7?230:0;case "yunsheng"->s.index==7?210:0;default->0;};
        if(shield>0)near(h,ModShieldSystem.value(s.actor),shield,"Exact rank-one S0 shield "+label(s));
        if(s.role.equals("yingxiao")&&s.index==3)h.assertTrue(s.actor.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),"Vanish grants actual invisibility");
        if(s.role.equals("sulan")&&s.index==4)h.assertTrue(com.myriad.combat.WindWallSystem.firstEnemyHit(s.target,s.origin.add(0,1,0),s.origin.add(0,1,8))!=null,"Barrier branch creates a collision-active wind wall");
        if(s.role.equals("lingyuan")&&s.index==3)near(h,AbilityRuntime.incoming(s.actor,com.myriad.combat.DamageCalc.Kind.MAGIC),.85,"Mantle actual incoming multiplier");
    }
    private static void verifyOutcome(GameTestHelper h,Scene s){
        double expected=switch(s.role){
            case "yingxiao"->switch(s.index){case 2->61.5;case 4->94;case 5->74.5;case 6->219;default->0;};
            case "sulan"->switch(s.index){case 2->68;case 3->32.5;case 5->117;case 6->196;case 7->109.5;default->0;};
            case "lingyuan"->switch(s.index){case 2->81.5;case 4->105;case 5->140;case 6->330;case 7->300;default->0;};
            case "hanze"->switch(s.index){case 2->75;case 4->115;case 5->85;case 6->270;case 7->90;default->0;};
            case "yunsheng"->switch(s.index){case 2->55;case 3->70;case 4->65;case 6->180;default->0;};
            default->throw new IllegalArgumentException(s.role);
        };
        near(h,10000-s.target.getHealth(),expected,"Exact whole-window branch damage "+label(s)+" actor="+s.actor.position().subtract(s.origin)+" target="+s.target.position().subtract(s.origin)+" alive="+s.target.isAlive()+"/"+s.target.isRemoved());
        if(s.role.equals("hanze")&&(s.index==4||s.index==7)||s.role.equals("yunsheng")&&s.index==3)h.assertTrue(s.root,"Branch actually roots its legal enemy: "+label(s));
        if(s.role.equals("yunsheng")&&s.index==5)h.assertTrue(s.actor.position().distanceTo(s.origin.add(0,0,6))<.1,"Cloud escort reaches its confirmed standing point with motion");
        h.assertTrue(s.packets.stream().anyMatch(AbilityVfxPayload.class::isInstance),"Actual release emits production-encoded world presentation: "+label(s));
        AbilityRuntime.clear(s.actor);h.assertTrue(!AbilityRuntime.channeling(s.actor)&&!AbilityRuntime.moving(s.actor)&&PlayerDataProvider.get(s.actor).getPlacementType()==0,"Lifecycle releases all pending input/action: "+label(s));
    }
    private static void passiveStep(GameTestHelper h,Scene s,int age){
        var d=PlayerDataProvider.get(s.actor);var cast=AbilityRuntime.context(s.actor,FirstGroupSkills.node(s.role,2));
        if(age==1&&((s.role.equals("yingxiao"))||(s.role.equals("sulan")&&s.index==1)||(s.role.equals("hanze")&&s.index==1)||(s.role.equals("yunsheng")&&s.index==1)))cast.damage(s.target,10,com.myriad.combat.DamageCalc.Kind.PHYSICAL);
        if(s.role.equals("sulan")&&s.index==0&&age<=62){s.actor.setOnGround(true);s.actor.setPos(s.origin.add(age%2*.5,0,0));}
        if(s.role.equals("sulan")&&(s.index==0&&age==65||s.index==1&&age==5)||s.role.equals("lingyuan")&&age==15||s.role.equals("hanze")&&s.index==0&&Set.of(1,31,61,91).contains(age))basic(s);
        if(s.role.equals("lingyuan")&&s.index==1&&Set.of(1,6,11).contains(age)){var skill=FirstGroupSkills.node(s.role,3);d.setCooldown(skill.getId(),0);receive(h,s.actor,new NetworkSetup.CastSkillPayload(1,0,0));}
        if(s.role.equals("yunsheng")&&s.index==0&&Set.of(1,6).contains(age)){if(age==6)ModShieldSystem.consume(s.actor,10);var skill=FirstGroupSkills.node(s.role,7);d.setCooldown(skill.getId(),0);receive(h,s.actor,new NetworkSetup.CastSkillPayload(3,0,0));}
        if(age==100&&s.role.equals("hanze")&&s.index==0)h.assertTrue(d.getBuff("g1_stun")==0&&PlayerDataProvider.get(s.target).getBuff("g1_root")>0,"Four distinct actual basics trigger the frost passive root");
        if(age==20&&s.role.equals("lingyuan")&&s.index==1)near(h,ModShieldSystem.sourceValue(s.actor,node(s).getId()+"/"+s.actor.getUUID()),45,"Three actual casts + basic consume resonance and grant exact passive shield");
        if(age==7&&s.role.equals("yunsheng")&&s.index==0)near(h,ModShieldSystem.sourceValue(s.actor,node(s).getId()+"/"+s.actor.getUUID()),45,"Two separated actual shelter casts trigger exact echo shield");
        if(age==2&&s.role.equals("hanze")&&s.index==1)near(h,ModShieldSystem.value(s.actor),65,"An effective hit triggers exact rime shield");
    }
    private static void basic(Scene s){
        if(com.myriad.combat.MagicBullet.isMage(s.actor)){com.myriad.combat.MagicBullet.tryFire(s.actor,s.actor.getMainHandItem());return;}
        try{var f=LivingEntity.class.getDeclaredField("attackStrengthTicker");f.setAccessible(true);f.setInt(s.actor,200);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        s.actor.getRandom().setSeed(28);s.actor.fallDistance=0;s.target.damageCooldownTime=0;float before=s.target.getHealth();s.actor.attack(s.target);s.loss=before-s.target.getHealth();
    }
    private static void verifyPassive(GameTestHelper h,Scene s){
        var d=PlayerDataProvider.get(s.actor);
        if(s.role.equals("yingxiao"))near(h,10000-s.target.getHealth(),s.index==0?11:47.5,"Actual backstab/isolated-hero hit hook "+label(s));
        if(s.role.equals("sulan"))h.assertTrue(s.loss>=(s.index==0?96:86)&&d.getBuff("sulan_passive_ready")==0,"Walking/skill-hit primed passive adds actual basic damage once and consumes readiness: "+label(s)+" damage="+s.loss);
        if(s.role.equals("lingyuan")&&s.index==0)h.assertTrue(d.getSwordQi()==10,"Actual basic grants exactly ten sword qi");
        if(s.role.equals("lingyuan")&&s.index==1)h.assertTrue(s.loss>=96&&d.getBuff(node(s).getId()+"_ready")==0,"Resonance adds actual magic damage once and consumes readiness");
        if(s.role.equals("hanze")&&s.index==0)h.assertTrue(Math.abs(10000-s.target.getHealth()-265)<.15&&s.root,"Four actual magic basics add frost damage and actual sourced control; loss="+(10000-s.target.getHealth()));
        if(s.role.equals("yunsheng")&&s.index==1)near(h,s.actor.getHealth(),118,"Effective hero hit triggers exact spirit chime healing");
        AbilityRuntime.clear(s.actor);
    }
    private static void verifyLearningAndPersistence(GameTestHelper h,ServerPlayer p){
        for(String role:ROLES){
            PlayerDataProvider.resetForHostSession(p);receive(h,p,new NetworkSetup.ChooseCharacterPayload(role));var d=PlayerDataProvider.get(p);
            h.assertTrue(role.equals(d.getCharacterId())&&d.getSkillPoints()==1&&p.getInventory().getItem(0).is(ModItems.initialWeaponItem(role)),"Formal selection owns initial point and matching weapon: "+role);
            near(h,d.getModMaxHealth(),FirstGroupGrowth.health(role,1),"Formal level-one health "+role);
            for(var skill:FirstGroupSkills.candidates(role)){
                d.setLevel(1);d.refundAllSkillPointsForTraining();
                if(skill.isUltimate()){receive(h,p,new NetworkSetup.UpgradeSkillPayload(skill.getId()));h.assertTrue(d.skillLevel(skill.getId())==0&&d.getSkillPoints()==1,"Level-one ultimate input rejected without spending "+skill.getId());}
                d.setLevel(20);d.refundAllSkillPointsForTraining();receive(h,p,new NetworkSetup.UpgradeSkillPayload(skill.getId()));
                h.assertTrue(d.skillLevel(skill.getId())==1&&d.getSkillPoints()==19,"Every real candidate learns through registered input: "+skill.getId());
                int other=FirstGroupSkills.opposite(skill.index());if(other>=0){receive(h,p,new NetworkSetup.UpgradeSkillPayload(FirstGroupSkills.node(role,other).getId()));h.assertTrue(d.skillLevel(FirstGroupSkills.node(role,other).getId())==0&&d.getSkillPoints()==19,"Opposite branch receiver rejects without spending: "+skill.getId());}
            }
            FirstGroupGrowth.initialize(p,d);d.setGold(2000);
            for(String key:FirstGroupGrowth.ATTRIBUTES)receive(h,p,new NetworkSetup.CraftEquipPayload("g1_attribute_"+key));
            h.assertTrue(FirstGroupGrowth.ATTRIBUTES.stream().allMatch(key->d.firstGroupAttribute(key)==1)&&d.getGold()==200,"Five actual purchase inputs charge exactly 5x360 gold: "+role);
            near(h,p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK),80,"Purchased strength "+role);near(h,p.getAttributeValue(ModAttributes.SPELL_POWER),50,"Purchased wisdom "+role);
            near(h,p.getAttributeValue(ModAttributes.ATTACK_SPEED),.76,"Purchased attack speed "+role);h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-.1015)<.00001,"Purchased movement speed "+role);
            near(h,p.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE),50,"Purchased physical defense "+role);near(h,p.getAttributeValue(ModAttributes.MAGIC_RESISTANCE),50,"Purchased magic resistance "+role);near(h,p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),3,"Purchased cooldown reduction "+role);
            near(h,d.getModMaxHealth(),FirstGroupGrowth.health(role,20),"Gold attributes retain level-twenty health "+role);
            d.setMana(123);d.setGold(4321);d.setFirstGroupAttribute("wisdom",2);verifyPersistence(h,p);
        }
    }
    private static void verifyPersistence(GameTestHelper h,ServerPlayer p){
        var problems=new net.minecraft.util.ProblemReporter.Collector();var out=net.minecraft.world.level.storage.TagValueOutput.createWithContext(problems,h.getLevel().registryAccess());p.saveWithoutId(out);
        var restored=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var clone=player(h,p.position(),new ArrayList<>(),false);java.nio.file.Path path=null;
        try {
            path=java.nio.file.Files.createTempFile(h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),"myriad-roster-b-",".nbt");net.minecraft.nbt.NbtIo.writeCompressed(out.buildResult(),path);
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(problems,h.getLevel().registryAccess(),net.minecraft.nbt.NbtIo.readCompressed(path,net.minecraft.nbt.NbtAccounter.unlimitedHeap())));
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(p,clone,false);
            var before=PlayerDataProvider.get(p);
            for(var actual:List.of(PlayerDataProvider.get(restored),PlayerDataProvider.get(clone)))h.assertTrue(actual!=before&&Objects.equals(actual.getCharacterId(),before.getCharacterId())&&actual.getSkillLevels().equals(before.getSkillLevels())&&actual.getSkillPoints()==before.getSkillPoints()&&actual.getGold()==4321&&actual.firstGroupAttribute("wisdom")==2,"Native new attachment disk load/death-clone keeps role, branch, points and purchases: "+before.getCharacterId());
            h.assertTrue(problems.isEmpty(),"Native save/load reports no attachment problems");
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}finally{restored.discard();clone.discard();if(path!=null)try{java.nio.file.Files.deleteIfExists(path);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
    }
    private static void aim(ServerPlayer p,double distance){p.setYRot(0);p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeHeight(),distance)));}
    private static void near(GameTestHelper h,double value,double expected,String text){h.assertTrue(Math.abs(value-expected)<.15,text+" actual="+value+" expected="+expected);}
    private static void receive(GameTestHelper h,ServerPlayer p,CustomPacketPayload packet){ShuoPilotChecks.receive(h,p,packet);}
    private static ServerPlayer player(GameTestHelper h,Vec3 pos,List<CustomPacketPayload> packets,boolean entity){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var c=new Connection(PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),c,p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom){var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(buf,custom);packets.add(ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(buf).payload());h.assertTrue(!buf.isReadable(),"Complete production S2C codec");}finally{buf.release();}}}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());p.setNoGravity(true);p.setPos(pos);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setCharacterId("shuo");d.setLevel(20);d.setModMaxHealth(10000);d.setModHealthInitialized(true);p.setHealth(10000);p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);if(entity)h.getLevel().addNewPlayer(p);else h.getLevel().players().add(p);return p;
    }
}
