package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.*;

/** Real fields, actual private match AI and vanilla arrow ticks/collision; no client automation. */
final class WorldTimeChecks {
    private WorldTimeChecks() {}
    static void run(GameTestHelper h) {
        boolean enabled=ModState.isEnabled(), training=TrainingMode.isEnabled();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);
        var f=new Fixture(h);
        try {
            f.prepare();
            LaneSystem.tick(h.getLevel()); // Consume startup layout cleanup before spawning temporary lane units.
            npcCases(f);
            arrowCases(f);
            needleCases(f,()->{f.clear();ModState.setEnabled(enabled);TrainingMode.setEnabled(training);});
        } catch(Throwable e) {com.myriad.Myriad.LOGGER.error("World time test failed",e);f.clear();ModState.setEnabled(enabled);TrainingMode.setEnabled(training);h.fail("world_time: "+e);}
    }
    private static void needleCases(Fixture f,Runnable cleanup)throws Exception {
        final Mob[] current={null};final float[] hp={0};final double[] step={0};
        final AbstractArrow[] launched=new AbstractArrow[2];final long start=f.h.getLevel().getGameTime();
        java.util.function.IntConsumer prepare=index->{try{
            f.now=f.h.getLevel().getGameTime();f.target.setPos(f.origin.add(0,0,12));f.owner.setPos(f.origin.add(-30,0,-5));
            current[0]=index==0?f.minion("MELEE",Faction.YOUMING):f.monster(index==1?MonsterType.SPRING_REEF_GUARDIAN:MonsterType.AZURE_HORN_GUARDIAN);
            if(MonsterManager.isMonster(current[0]))MonsterManager.onAttacked(current[0],f.target,1);
            Vec3 at=current[0].position();f.tickNpc(current[0]);step[0]=at.subtract(current[0].position()).horizontalDistance();
            current[0].setPos(f.origin);hp[0]=KehengTimeFields.health(current[0]);
            f.owner.setPos(f.origin.add(0,0,-4));f.owner.setYRot(0);
            f.owner.setXRot((float)Math.toDegrees(Math.atan2(f.owner.getEyeY()-current[0].getBoundingBox().getCenter().y,4)));
            var d=PlayerDataProvider.get(f.owner);d.setMana(700);d.setSkillLevel(FirstGroupSkills.node("keheng",2).getId(),1);d.setCooldown("keheng_chizhen",0);
            launched[0]=f.arrow(false,f.origin.add(0,10,0),new Vec3(0,.2,1),f.owner);
            launched[1]=f.arrow(false,f.origin.add(0,10,0),new Vec3(0,.2,1),current[0]);
            launched[0].tick();launched[1].tick();
            com.myriad.character.SkillSystem.cast(f.owner,0);
        }catch(Exception e){throw new RuntimeException(e);}};
        prepare.accept(0);
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> listener=new java.util.function.Consumer<>(){
            boolean done;
            public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(done)return;
                int age=(int)(f.h.getLevel().getGameTime()-start);
                try{
                    AbilityRuntime.tick(f.owner);
                    if(age==12||age==26||age==40){
                        int index=age==12?0:age==26?1:2;Mob mob=current[0];f.now=f.h.getLevel().getGameTime();
                        f.h.assertTrue(KehengTimeFields.health(mob)<hp[0],"Actual Q projectile damages NPC category "+index+" hp="+KehengTimeFields.health(mob)+" before="+hp[0]+" removed="+mob.isRemoved()+" pos="+mob.position()+" caster="+f.owner.position()+" mana="+PlayerDataProvider.get(f.owner).getMana()+" role="+PlayerDataProvider.get(f.owner).getCharacterId()+" active="+AbilityRuntime.channeling(f.owner));
                        double rate=KehengTimeFields.managedNpcRate(mob,f.now);
                        f.h.assertTrue(Math.abs(rate-(index==2?1:.85))<1E-9,"Actual Q needle action source / boss exemption "+index);
                        f.owner.setPos(f.origin.add(index==0?-30:-8,0,-5));
                        if(MonsterManager.isMonster(mob))MonsterManager.onAttacked(mob,f.target,10000);
                        Vec3 at=mob.position();f.tickNpc(mob);
                        f.h.assertTrue(Math.abs(mob.position().subtract(at).horizontalDistance()/step[0]-(index==2?1:.85))<.04,"Real Q slows private NPC movement "+index+" step="+mob.position().subtract(at).horizontalDistance()+" baseline="+step[0]);
                        launched[0].tick();launched[1].tick();near(f,launched[0].position(),launched[1].position(),"Already launched vanilla arrow ignores its shooter's needle rate "+index);
                        if(index<2){
                            f.target.setPos(mob.position().add(0,0,.9));float health=f.target.getHealth();
                            for(int i=0;i<6&&health==f.target.getHealth();i++)f.tickNpc(mob);
                            f.h.assertTrue(f.target.getHealth()<health,"Needled NPC still attacks legally "+index);
                            health=f.target.getHealth();for(int i=0;i<10;i++)f.tickNpc(mob);
                            AbilityRuntime.clear(f.owner);
                            int remaining=(int)Math.ceil((index==0?22:20)-8.5);
                            for(int i=0;i<remaining-1;i++)f.tickNpc(mob);
                            f.h.assertTrue(health==f.target.getHealth(),"Needle recovery retains completed fraction after cleanup "+index);
                            f.tickNpc(mob);f.h.assertTrue(f.target.getHealth()<health,"Needle recovery completes without catch-up "+index);
                        }
                        AbilityRuntime.clear(f.owner);f.removeMob(mob);
                    }
                    if(age==14)prepare.accept(1);if(age==28)prepare.accept(2);
                    if(age>=41){done=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();System.out.println("[MYRIAD-WORLD-TIME] PASS: four lane kinds, nine ordinary camps, four bosses; actual Q against lane/camp/boss; vanilla ordinary/tipped/spectral flight, damage/effects, water/slope/wall/boundaries, ownership/sync and 1200/2400 budgets");f.h.succeed();}
                }catch(Throwable e){com.myriad.Myriad.LOGGER.error("World time needle failed",e);done=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);cleanup.run();f.h.runAfterDelay(1,()->f.h.fail("world_time needle: "+e));}
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    }
    private static void npcCases(Fixture f)throws Exception {
        for(String kind:List.of("MELEE","RANGED","MAGIC","SIEGE")) {
            Mob mob=f.minion(kind,Faction.YOUMING);int cooldown=kind.equals("MELEE")?22:kind.equals("SIEGE")?30:36;
            npcMovement(f,mob,"lane "+kind);
            npcRecovery(f,mob,cooldown,"lane "+kind);
            f.removeMob(mob);
        }
        Mob friend=f.minion("MELEE",Faction.XUANXIAO);f.field(.5);
        f.h.assertTrue(KehengTimeFields.managedNpcRate(friend,f.now)==1,"Allied minions are exempt from an actual field");
        f.removeMob(friend);f.clearFields();
        for(MonsterType type:MonsterType.values()) {
            Mob mob=f.monster(type);
            if(!type.isLargeBoss()) {npcMovement(f,mob,type.getId());npcRecovery(f,mob,20,type.getId());}
            else {
                f.field(.5);float before=MonsterManager.modHealth(mob);
                f.cast().damage(mob,50,com.myriad.combat.DamageCalc.Kind.MAGIC);
                f.h.assertTrue(MonsterManager.modHealth(mob)<before,"Large boss remains legally damageable: "+type);
                f.h.assertTrue(KehengTimeFields.managedNpcRate(mob,f.now)==1,"Large boss action exemption: "+type);
                f.clearFields();
            }
            f.removeMob(mob);
        }
    }
    private static void npcMovement(Fixture f,Mob mob,String label)throws Exception {
        f.target.setPos(f.origin.add(0,0,12));
        if(MonsterManager.isMonster(mob))MonsterManager.onAttacked(mob,f.target,1);
        Vec3 before=mob.position();f.tickNpc(mob);double baseline=mob.position().distanceTo(before);
        f.field(.5);before=mob.position();f.tickNpc(mob);double slow=mob.position().distanceTo(before);
        f.h.assertTrue(baseline>.02&&Math.abs(slow/baseline-.5)<.025,"Actual autonomous movement halves: "+label+" "+baseline+"/"+slow);
        f.cast().slow(mob,.7f,100);before=mob.position();f.tickNpc(mob);double ordinary=mob.position().distanceTo(before);
        f.h.assertTrue(Math.abs(ordinary/baseline-.3)<.025,"Ordinary slow and local movement take stronger, not product: "+label);
        // Remove only this fixture's ordinary source through the same owner lifecycle.
        AbilityRuntime.clear(f.owner);f.rearmOwner();
        f.field(.5);f.field(.7);before=mob.position();f.tickNpc(mob);
        f.h.assertTrue(Math.abs(mob.position().distanceTo(before)/baseline-.5)<.025,"Overlapping fields use minimum: "+label);
        f.clearFields();before=mob.position();f.tickNpc(mob);
        f.h.assertTrue(Math.abs(mob.position().distanceTo(before)/baseline-1)<.025,"Source cleanup restores movement next tick: "+label);
        // Autonomous stepping does not scale an externally supplied vertical velocity.
        mob.setDeltaMovement(0,-.27,0);f.field(.5);f.tickNpc(mob);
        f.h.assertTrue(Math.abs(mob.getDeltaMovement().y+.27)<1E-9,"Character gravity/vertical velocity preserved: "+label);
        f.clearFields();
    }
    private static void npcRecovery(Fixture f,Mob mob,int cooldown,String label)throws Exception {
        // The target is now in attack range; no final recovery value is written by the test.
        f.target.setPos(mob.position().add(0,0,.9));
        if(MonsterManager.isMonster(mob))MonsterManager.onAttacked(mob,f.target,1);
        float hp=f.target.getHealth();for(int i=0;i<6&&f.target.getHealth()==hp;i++)f.tickNpc(mob);
        f.h.assertTrue(f.target.getHealth()<hp,"Actual first NPC attack: "+label);
        hp=f.target.getHealth();
        int normal=cooldown/2;
        for(int i=0;i<normal;i++)f.tickNpc(mob);
        f.field(.5);
        for(int i=0;i<10;i++)f.tickNpc(mob);
        f.h.assertTrue(f.target.getHealth()==hp,"Unfinished attack recovery slows without resetting: "+label);
        f.clearFields();
        int remaining=cooldown-normal-5;
        for(int i=0;i<remaining-1;i++)f.tickNpc(mob);
        f.h.assertTrue(f.target.getHealth()==hp,"Exiting does not burst or catch up: "+label);
        f.tickNpc(mob);
        f.h.assertTrue(f.target.getHealth()<hp,"Retained attack fraction completes exactly after exit: "+label);
        hp=f.target.getHealth();f.tickNpc(mob);
        f.h.assertTrue(f.target.getHealth()==hp,"Only one attack is resolved at recovery completion: "+label);
    }
    private static void arrowCases(Fixture f)throws Exception {
        f.target.setPos(f.origin.add(15,0,-5));f.owner.setPos(f.origin.add(-8,0,-5));
        Vec3 at=f.origin.add(0,7,0),velocity=new Vec3(.4,.3,1.4);
        for(boolean spectral:new boolean[]{false,true}) {
            AbstractArrow normal=f.arrow(spectral,at,velocity,f.owner),slow=f.arrow(spectral,at,velocity,f.enemy);
            f.fieldAt(at,.5);
            for(int i=0;i<8;i++){normal.tick();slow.tick();slow.tick();}
            near(f,normal.position(),slow.position(),"Fractional vanilla trajectory matches by flight progress "+spectral);
            near(f,normal.getDeltaMovement(),slow.getDeltaMovement(),"Air drag/gravity preserve physical velocity "+spectral);
            f.clearFields();
        }
        // Changing rate midway through a vanilla step keeps its unfinished fraction, including at exit.
        AbstractArrow normal=f.arrow(false,at,velocity,f.owner),slow=f.arrow(false,at,velocity,f.enemy);
        f.fieldAt(at,.5);slow.tick();f.clearFields();slow.tick();f.fieldAt(at,.5);slow.tick();normal.tick();normal.tick();
        near(f,normal.position(),slow.position(),"Entry/exit during half-step retains path without extra range");
        near(f,normal.getDeltaMovement(),slow.getDeltaMovement(),"Entry/exit keeps gravity and inertia progress");f.clearFields();
        // Two owners' overlapping fields, friendly arrows and ownerless arrows.
        f.fieldAt(at,.7);f.fieldAt(at,.5);
        var ally=f.arrow(false,at,velocity,f.owner);var enemy=f.arrow(false,at,velocity,f.enemy);var unowned=f.arrow(false,at,velocity,null);
        ally.tick();enemy.tick();unowned.tick();
        f.h.assertTrue(enemy.getData(com.myriad.combat.VanillaArrowTime.RATE)==.5&&ally.getData(com.myriad.combat.VanillaArrowTime.RATE)==1,
                "NeoForge synced entity attachment carries each arrow's current server rate");
        near(f,ally.position(),unowned.position(),"Ownerless flight is not assigned a fictional enemy faction");
        near(f,at.add(velocity.scale(.5)),enemy.position(),"Overlapping real fields choose strongest flight rate");
        f.clearFields();
        PlayerDataProvider.get(f.target).setCharacterId("keheng");
        var second=AbilityRuntime.context(f.target,FirstGroupSkills.node("keheng",4));
        KehengTimeFields.ground(second,at,24,0,100,.7,0,0);f.fieldAt(at,.5);
        f.h.assertTrue(KehengTimeFields.projectileRate(f.enemy,at,f.h.getLevel().getGameTime())==.5,"Different field owners aggregate minimum rate");
        f.clearFields();f.h.assertTrue(KehengTimeFields.projectileRate(f.enemy,at,f.h.getLevel().getGameTime())==.7,"Cleaning one owner preserves another owner's field");
        AbilityRuntime.clear(f.target);PlayerDataProvider.get(f.target).setCharacterId("shuo");
        // A shooter's living-body susceptibility does not decide an already airborne arrow's affiliation.
        f.fieldAt(at,.5);f.enemy.setHealth(0);
        var orphan=f.arrow(false,at,velocity,f.enemy);orphan.tick();
        near(f,at.add(velocity.scale(.5)),orphan.position(),"Independent arrow retains hostile attribution after shooter death");
        f.enemy.setHealth(10000);f.clearFields();
        // Traverse a bounded field with actual movement, then restore on the first tick outside it.
        at=f.origin.add(0,6,0);var crossing=f.arrow(false,at,new Vec3(0,0,1),f.enemy);crossing.setNoGravity(true);
        KehengTimeFields.ground(f.cast(),at.add(0,0,3),1.5,0,100,.5,0,0);
        boolean slowed=false,restored=false;
        for(int i=0;i<18;i++){
            Vec3 before=crossing.position(),physical=crossing.getDeltaMovement();
            double rate=KehengTimeFields.projectileRate(f.enemy,before,f.h.getLevel().getGameTime());crossing.tick();
            double moved=crossing.position().distanceTo(before);
            if(rate==.5){slowed=true;f.h.assertTrue(moved<=physical.length()*.501,"Crossing flight slows only after entering field");}
            else if(slowed){restored=true;f.h.assertTrue(moved<=physical.length()+1E-6&&moved>physical.length()*.98,"Exit restores one local tick without catch-up");}
        }
        f.h.assertTrue(slowed&&restored,"Arrow physically enters and leaves real bounded field");f.clearFields();
        damageAndWalls(f);
        waterAndSlope(f);
        lifetime(f);
    }
    private static void damageAndWalls(Fixture f)throws Exception {
        Vec3 at=f.origin.add(0,1,0);f.target.setPos(f.origin.add(0,0,5));
        // Use a real vanilla target to keep this check independent of character-specific damage hooks.
        var target=EntityTypes.COW.create(f.h.getLevel(),EntitySpawnReason.MOB_SUMMONED);
        target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        target.setPos(f.origin.add(0,0,4));f.h.getLevel().addFreshEntity(target);f.entities.add(target);
        var base=f.arrow(false,at,new Vec3(0,0,2),f.enemy);
        for(int i=0;i<5&&!base.isRemoved();i++)base.tick();float damage=100-target.getHealth();
        target.setHealth(100);target.invulnerableTime=0;target.setDeltaMovement(Vec3.ZERO);target.setPos(f.origin.add(0,0,4));
        f.fieldAt(at,.5);var slow=f.arrow(false,at,new Vec3(0,0,2),f.enemy);
        for(int i=0;i<10&&!slow.isRemoved();i++)slow.tick();
        f.h.assertTrue(damage>0&&Math.abs(100-target.getHealth()-damage)<.01,"Vanilla arrow hit damage retains physical speed under slow: "+damage+"/"+(100-target.getHealth()));
        for(boolean spectral:new boolean[]{false,true}){
            target.setHealth(100);target.invulnerableTime=0;target.removeAllEffects();target.setDeltaMovement(Vec3.ZERO);target.setPos(f.origin.add(0,0,4));
            var stack=new ItemStack(Items.TIPPED_ARROW);
            stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.POISON));
            AbstractArrow special=spectral?f.arrow(true,at,new Vec3(0,0,2),f.enemy):new Arrow(f.h.getLevel(),at.x,at.y,at.z,stack,null);
            if(!spectral){special.setOwner(f.enemy);special.setDeltaMovement(0,0,2);f.entities.add(special);}
            for(int i=0;i<10&&!special.isRemoved();i++)special.tick();
            f.h.assertTrue(target.getHealth()<100&&target.hasEffect(spectral?net.minecraft.world.effect.MobEffects.GLOWING:net.minecraft.world.effect.MobEffects.POISON),
                    "Actual slowed "+(spectral?"spectral":"tipped")+" arrow retains vanilla post-hit effect");
        }
        target.discard();f.clearFields();
        // A thin collidable fence at the swept path stops both arrows, even at high speed.
        f.block(0,1,3,Blocks.IRON_BARS);f.block(0,2,3,Blocks.IRON_BARS);
        f.fieldAt(at,.5);var wall=f.arrow(false,at,new Vec3(0,0,12),f.enemy);wall.tick();
        f.h.assertTrue(wall.getZ()<f.origin.z+4&&wall.getDeltaMovement().lengthSqr()==0,"Fractional sweep uses vanilla thin-wall collision");
        f.clearFields();f.block(0,1,3,Blocks.AIR);f.block(0,2,3,Blocks.AIR);
    }
    private static void waterAndSlope(Fixture f)throws Exception {
        Vec3 at=f.origin.add(4,1,0),v=new Vec3(0,.1,.8);
        for(int x=3;x<=5;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=5;z++)f.block(x,y,z,Blocks.WATER);
        var base=f.arrow(false,at,v,f.owner);var slow=f.arrow(false,at,v,f.enemy);
        // First tick populates vanilla fluid state identically before the field is placed.
        base.tick();slow.tick();f.fieldAt(at,.5);
        for(int i=0;i<3;i++){base.tick();slow.tick();slow.tick();}
        near(f,base.position(),slow.position(),"Water drag follows local flight step");
        near(f,base.getDeltaMovement(),slow.getDeltaMovement(),"Water physical momentum remains vanilla");f.clearFields();
        for(int x=3;x<=5;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=5;z++)f.block(x,y,z,Blocks.AIR);
        // Rising terrain is a real staircase of blocks, not a computed endpoint.
        for(int z=3;z<=6;z++)for(int y=0;y<=z-3;y++)f.block(0,y,z,Blocks.STONE);
        at=f.origin.add(0,3,0);base=f.arrow(false,at,new Vec3(0,-.1,1),f.owner);slow=f.arrow(false,at,new Vec3(0,-.1,1),f.enemy);
        f.fieldAt(at,.5);
        for(int i=0;i<8;i++){base.tick();slow.tick();slow.tick();}
        near(f,base.position(),slow.position(),"Sloped terrain collision ends at the same actual block");f.clearFields();
        for(int z=3;z<=6;z++)for(int y=0;y<=z-3;y++)f.block(0,y,z,Blocks.AIR);
    }
    private static void lifetime(Fixture f) {
        Vec3 at=f.origin.add(0,12,0);
        var progress=f.arrow(false,at,Vec3.ZERO,f.enemy);progress.setNoGravity(true);
        for(int i=0;i<1199;i++)progress.tick();
        f.h.assertTrue(!progress.isRemoved(),"MYRIAD airborne progress cap does not expire early");progress.tick();
        f.h.assertTrue(progress.isRemoved(),"MYRIAD explicitly added 1200 action-tick airborne limit");
        f.fieldAt(at,.7);var slowedProgress=f.arrow(false,at,Vec3.ZERO,f.enemy);slowedProgress.setNoGravity(true);
        for(int i=0;i<1714;i++)slowedProgress.tick();
        f.h.assertTrue(!slowedProgress.isRemoved(),"Flight lifetime counts unfinished action progress, not real ticks");slowedProgress.tick();
        f.h.assertTrue(slowedProgress.isRemoved(),"Slowed airborne progress expires on reaching 1200 action ticks");f.clearFields();
        f.fieldAt(at,.4);var safety=f.arrow(false,at,Vec3.ZERO,f.enemy);safety.setNoGravity(true);
        for(int i=0;i<2399;i++)safety.tick();
        f.h.assertTrue(!safety.isRemoved(),"Real safety budget has not expired at 2399 ticks");safety.tick();
        f.h.assertTrue(safety.isRemoved(),"MYRIAD 2400 real-tick safety cap ends heavily slowed arrows");
        f.clearFields();
        var grounded=f.arrow(false,f.origin.add(0,1,0),new Vec3(0,-1,0),f.enemy);grounded.tick();grounded.tick();
        var life=field(AbstractArrow.class,"life");
        try {int before=life.getInt(grounded);for(int i=0;i<1199-before;i++)grounded.tick();
            f.h.assertTrue(!grounded.isRemoved(),"Vanilla grounded lifetime unchanged before 1200");grounded.tick();
            f.h.assertTrue(grounded.isRemoved(),"Vanilla grounded arrow despawns at original 1200 ticks");}
        catch(IllegalAccessException e){throw new RuntimeException(e);}
    }
    private static void near(Fixture f,Vec3 a,Vec3 b,String message){f.h.assertTrue(a.distanceToSqr(b)<1E-10,message+": "+a+" / "+b);}
    private static Field field(Class<?> type,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") private static Map<UUID,Object> states(Class<?> type,String name)throws Exception{return (Map<UUID,Object>)field(type,name).get(null);}
    private static final class Fixture {
        final GameTestHelper h;Vec3 origin;ServerPlayer owner,target,enemy;long now;
        final List<Entity> entities=new ArrayList<>();final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();
        Fixture(GameTestHelper h){this.h=h;now=h.getLevel().getGameTime();}
        void prepare(){
            origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(-90,65,-60)));
            for(int x=-10;x<=20;x++)for(int z=-8;z<=30;z++)block(x,-1,z,Blocks.STONE);
            owner=player(origin.add(-30,0,-5),Faction.XUANXIAO,"keheng");target=player(origin.add(0,0,12),Faction.XUANXIAO,"shuo");enemy=player(origin.add(18,0,-6),Faction.YOUMING,"shuo");
        }
        ServerPlayer player(Vec3 at,Faction faction,String role){
            var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
            p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);
            var d=PlayerDataProvider.get(p);d.setCharacterId(role);d.setFaction(faction);d.setLevel(20);d.setMana(700);d.setModMaxHealth(10000);d.setModHealthInitialized(true);p.setHealth(10000);
            p.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);p.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);h.getLevel().addNewPlayer(p);entities.add(p);return p;
        }
        void rearmOwner(){PlayerDataProvider.get(owner).setCharacterId("keheng");}
        AbilityRuntime.Cast cast(){return AbilityRuntime.context(owner,FirstGroupSkills.node("keheng",4));}
        void field(double rate){fieldAt(origin.add(0,1,6),rate);}
        void fieldAt(Vec3 at,double rate){KehengTimeFields.ground(cast(),at,24,0,10000,rate,0,0);}
        void clearFields(){KehengTimeFields.clear(owner);}
        void block(int x,int y,int z,net.minecraft.world.level.block.Block block){
            var pos=BlockPos.containing(origin).offset(x,y,z);blocks.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        }
        Mob monster(MonsterType type)throws Exception {
            var before=Set.copyOf(states(MonsterManager.class,"STATES").keySet());MonsterManager.spawn(h.getLevel(),BlockPos.containing(origin),type);
            for(var entry:states(MonsterManager.class,"STATES").entrySet())if(!before.contains(entry.getKey())){
                var mob=(Mob)WorldTimeChecks.field(entry.getValue().getClass(),"entity").get(entry.getValue());entities.add(mob);return mob;}
            throw new IllegalStateException("Monster not spawned: "+type);
        }
        @SuppressWarnings({"rawtypes","unchecked"}) Mob minion(String name,Faction faction)throws Exception {
            Class kind=Class.forName("com.myriad.game.LaneSystem$Kind");
            var method=LaneSystem.class.getDeclaredMethod("spawnMinion",ServerLevel.class,Faction.class,LaneSystem.Lane.class,List.class,kind,double.class);method.setAccessible(true);
            var before=Set.copyOf(states(LaneSystem.class,"UNITS").keySet());
            method.invoke(null,h.getLevel(),faction,LaneSystem.Lane.MID,List.of(BlockPos.containing(origin),BlockPos.containing(origin.add(0,0,25))),Enum.valueOf(kind,name),0d);
            for(var entry:states(LaneSystem.class,"UNITS").entrySet())if(!before.contains(entry.getKey())){var mob=(Mob)WorldTimeChecks.field(entry.getValue().getClass(),"entity").get(entry.getValue());entities.add(mob);return mob;}
            throw new IllegalStateException("Minion not spawned");
        }
        void tickNpc(Mob mob)throws Exception {
            target.invulnerableTime=0;boolean monster=MonsterManager.isMonster(mob);Class<?> system=monster?MonsterManager.class:LaneSystem.class;
            Object state=states(system,monster?"STATES":"UNITS").get(mob.getUUID());
            var tick=system.getDeclaredMethod(monster?"tickMonster":"tickMinion",ServerLevel.class,Mob.class,state.getClass(),long.class);tick.setAccessible(true);
            tick.invoke(null,h.getLevel(),mob,state,++now);
        }
        AbstractArrow arrow(boolean spectral,Vec3 at,Vec3 velocity,Entity shooter){
            AbstractArrow arrow=spectral?new SpectralArrow(h.getLevel(),at.x,at.y,at.z,new ItemStack(Items.SPECTRAL_ARROW),null)
                    :new Arrow(h.getLevel(),at.x,at.y,at.z,new ItemStack(Items.ARROW),null);
            arrow.setOwner(shooter);arrow.setDeltaMovement(velocity);entities.add(arrow);return arrow;
        }
        void removeMob(Mob mob)throws Exception {mob.discard();states(MonsterManager.class,"STATES").remove(mob.getUUID());states(LaneSystem.class,"UNITS").remove(mob.getUUID());}
        void clear(){
            if(owner!=null)AbilityRuntime.clear(owner);if(target!=null)AbilityRuntime.clear(target);if(enemy!=null)AbilityRuntime.clear(enemy);
            for(var entity:entities){if(entity instanceof ServerPlayer p){h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}else {entity.discard();if(entity instanceof Mob m)try{removeMob(m);}catch(Exception ignored){}}}
            blocks.forEach((p,s)->h.getLevel().setBlockAndUpdate(p,s));
        }
    }
}
