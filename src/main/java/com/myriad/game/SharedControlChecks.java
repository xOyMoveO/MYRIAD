package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.WindWallSystem;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Shared control behavior survives removal of the historical character implementations. */
final class SharedControlChecks {
    static void start(GameTestHelper h) {
        verifyFreeze(h);
        verifyGeometry(h);
        verifyCombatContracts(h);
        startWallLifecycle(h);
        startControlExpiry(h);
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(180,160,180)));
        var owner=player(h,origin,"sulan");
        var enemy=player(h,origin.add(0,0,6),"shuo");
        var d=PlayerDataProvider.get(owner);
        d.setSkillLevel(FirstGroupSkills.node("sulan",4).getId(),1);
        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        try {
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            SkillSystem.cast(owner,CharacterType.SLOT_E);
            h.assertTrue(d.getMana()==590&&d.getCooldown("g1_sulan_ma")==480,
                    "Real selected Sulan wind wall commits its normal mana and cooldown");
            Vec3 hit=WindWallSystem.firstEnemyHit(enemy,origin.add(0,1,-2),origin.add(0,1,5));
            h.assertTrue(hit!=null&&Math.abs(hit.z-(origin.z-.3))<.001,
                    "A real Sulan cast is immediately visible through shared projectile interception");
        } finally {
            AbilityRuntime.clear(owner);AbilityRuntime.clear(enemy);
            owner.discard();enemy.discard();
            TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);
        }
        System.out.println("[MYRIAD-SHARED-CONTROL] PASS: real selected Sulan cast exposes shared interception");
    }
    private static void startWallLifecycle(GameTestHelper h) {
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,100,8)));
        var owner=player(h,origin,"sulan");
        var other=player(h,origin.add(20,0,0),"sulan");
        var enemy=player(h,origin.add(0,0,6),"shuo");
        var d=PlayerDataProvider.get(owner);
        var od=PlayerDataProvider.get(other);
        d.setSkillLevel("g1_sulan_ma",1);od.setSkillLevel("g1_sulan_ma",7);
        d.setFaction(Faction.XUANXIAO);od.setFaction(Faction.XUANXIAO);
        PlayerDataProvider.get(enemy).setFaction(Faction.YOUMING);
        Vec3 from=origin.add(0,1.5,-2),to=origin.add(0,1.5,8);
        BlockPos obstruction=BlockPos.containing(origin.add(0,1.5,4));
        var previous=h.getLevel().getBlockState(obstruction);
        var arrows=new java.util.ArrayList<net.minecraft.world.entity.projectile.Projectile>();
        boolean[] done={false};
        Runnable cleanup=()->{
            done[0]=true;arrows.forEach(net.minecraft.world.entity.Entity::discard);
            AbilityRuntime.clear(owner);AbilityRuntime.clear(other);AbilityRuntime.clear(enemy);
            owner.discard();other.discard();enemy.discard();h.getLevel().setBlockAndUpdate(obstruction,previous);
        };
        try {
            SkillSystem.cast(owner,CharacterType.SLOT_E);SkillSystem.cast(other,CharacterType.SLOT_E);
            h.assertTrue(d.getBuff("fengqiang")==50&&od.getBuff("fengqiang")==74,"Both selected wall ranks keep their original real-time duration");
            h.assertTrue(WindWallSystem.firstEnemyHit(owner,from,to)==null,"Own wall never blocks its caster's projectile");
            h.getLevel().setBlockAndUpdate(obstruction,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        } catch(Throwable failure) { cleanup.run();throw failure; }
        for(int tick=1;tick<=50;tick++) {
            int elapsed=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try {
                    WindWallSystem.tick(owner,d);WindWallSystem.tick(other,od);
                    if(elapsed==5||elapsed==10) {
                        Vec3 hit=WindWallSystem.firstEnemyHit(enemy,from,to);
                        double expected=origin.z+(elapsed==5?.7:1.7);
                        h.assertTrue(hit!=null&&Math.abs(hit.z-expected)<.001,"Wall advances one block in five ticks and two blocks in ten ticks");
                    }
                    if(elapsed==10) {
                        h.assertTrue(WindWallSystem.firstEnemyHit(enemy,from.add(3.01,0,0),to.add(3.01,0,0))==null
                                &&WindWallSystem.firstEnemyHit(enemy,from.add(0,2.51,0),to.add(0,2.51,0))==null,
                                "Wind wall preserves its six-block width and four-block height");
                        Vec3 end=com.myriad.character.ability.AbilityGeometry.clip(enemy,from,to);
                        h.assertTrue(Math.abs(end.z-(origin.z+1.7))<.001,"Beam clips to a hostile wind wall before the solid wall behind it");
                        for(var caster:java.util.List.of(enemy,owner)) {
                            var arrow=net.minecraft.world.entity.EntityTypes.ARROW.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                            arrow.setOwner(caster);arrow.setNoGravity(true);arrow.setPos(origin.add(0,1.5,2));
                            arrow.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(arrow);arrows.add(arrow);
                        }
                        WindWallSystem.tick(owner,d);
                        h.assertTrue(arrows.get(0).isRemoved()&&!arrows.get(1).isRemoved(),"Actual vanilla enemy arrow is intercepted while own arrow survives: enemyRemoved="+arrows.get(0).isRemoved()+" ownRemoved="+arrows.get(1).isRemoved()+" ownOwner="+arrows.get(1).getOwner());
                    }
                    if(elapsed==20) {
                        AbilityRuntime.clear(other);
                        h.assertTrue(WindWallSystem.firstEnemyHit(enemy,from.add(20,0,0),to.add(20,0,0))==null
                                &&WindWallSystem.firstEnemyHit(enemy,from,to)!=null,"Owner cleanup removes only that owner's wall");
                    }
                    if(elapsed==49)h.assertTrue(WindWallSystem.firstEnemyHit(enemy,from,to)!=null,"Rank-one wall remains active immediately before its real deadline");
                    if(elapsed==50) {
                        h.assertTrue(d.getBuff("fengqiang")==0&&WindWallSystem.firstEnemyHit(enemy,from,to)==null,"Wall stops intercepting exactly at its fifty-tick deadline");
                        Vec3 end=com.myriad.character.ability.AbilityGeometry.clip(enemy,from,to);
                        h.assertTrue(end.z>origin.z+3&&end.z<origin.z+5,"After wall expiry the beam still clips at the solid collider");
                        cleanup.run();
                        System.out.println("[MYRIAD-SHARED-WALL-LIFECYCLE] PASS: real selected ranks, 10-tick movement, 3D bounds, actual arrows, beam obstruction, scoped cleanup and tick-50 expiry");
                    }
                } catch(Throwable failure) { cleanup.run();throw failure; }
            });
        }
    }
    private static void startControlExpiry(GameTestHelper h) {
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,5,4)));
        var shuo=player(h,origin,"shuo");
        var night=player(h,origin.add(0,0,20),"yingxiao");
        var target=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new BlockPos(4,5,4));
        target.setNoAi(true);target.setNoGravity(true);
        boolean[] done={false};
        Runnable cleanup=()->{
            done[0]=true;
            for(var caster:java.util.List.of(shuo,night)) {
                com.myriad.combat.SilenceSystem.cleanse(caster);
                com.myriad.combat.FrozenControlSystem.clearFor(caster.getUUID());
                AbilityRuntime.clear(caster);caster.discard();
            }
            target.discard();
        };
        try {
            for(var caster:java.util.List.of(shuo,night)) {
                var data=PlayerDataProvider.get(caster);var skill=FirstGroupSkills.node(data.getCharacterId(),2);
                data.setSkillLevel(skill.getId(),1);
                com.myriad.combat.SilenceSystem.apply(caster,6);SkillSystem.cast(caster,CharacterType.SLOT_Q);
                h.assertTrue(data.getMana()==700&&data.getCooldown(skill.getId())==0,"Shared silence blocks actual Shuo and reworked skill inputs without payment");
            }
            com.myriad.combat.FrozenControlSystem.apply(h.getLevel(),shuo,target,6);
        } catch(Throwable failure) { cleanup.run();throw failure; }
        for(int tick=1;tick<=6;tick++) {
            int elapsed=tick;h.runAfterDelay(tick,()->{
                if(done[0])return;
                try {
                    com.myriad.combat.FrozenControlSystem.tick(shuo,h.getLevel());
                    com.myriad.combat.SilenceSystem.tick(h.getLevel().getGameTime());
                    if(elapsed==5)h.assertTrue(com.myriad.combat.SilenceSystem.isSilenced(shuo)&&shells(target)==3,
                            "Silence and freeze shells remain present before their real-time deadlines");
                    if(elapsed==6) {
                        h.assertTrue(!com.myriad.combat.SilenceSystem.isSilenced(shuo)&&!com.myriad.combat.SilenceSystem.isSilenced(night)&&shells(target)==0,
                                "Silence and freeze shells expire exactly on their real-time deadline");
                        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
                        try {
                            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
                            for(var caster:java.util.List.of(shuo,night)) {
                                SkillSystem.cast(caster,CharacterType.SLOT_Q);
                                var data=PlayerDataProvider.get(caster);var skill=FirstGroupSkills.node(data.getCharacterId(),2);
                                h.assertTrue(data.getMana()<700&&data.getCooldown(skill.getId())>0,"Expired silence restores real Shuo and reworked cast availability: "+data.getCharacterId()+" mana="+data.getMana()+" cooldown="+data.getCooldown(skill.getId()));
                            }
                        } finally { TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana); }
                        cleanup.run();
                        System.out.println("[MYRIAD-SHARED-CONTROL-EXPIRY] PASS: real Shuo/rework input gates, no early payment, exact silence/freeze expiry and restored casts");
                    }
                } catch(Throwable failure) { cleanup.run();throw failure; }
            });
        }
    }
    /** These contracts belonged to a retiring hero's test but are shared combat behavior. */
    static void verifyCombatContracts(GameTestHelper h) {
        h.assertTrue(FullGameReset.stale("new", "old") && FullGameReset.stale("new", "")
                && !FullGameReset.stale("new", "new") && !FullGameReset.stale("", "old"),
                "Reset generation invalidates old and offline data exactly once");
        h.assertTrue(com.myriad.network.EnemyHealthPayload.inRange(400)
                && !com.myriad.network.EnemyHealthPayload.inRange(400.01),"Health display cutoff is exactly twenty blocks");
        h.assertTrue(MatchBalance.towerScale(0,0)==1.0 && Math.abs(MatchBalance.towerScale(24000,4)-4.4)<.000001,
                "Tower pressure has bounded time and focus scaling");
        h.assertTrue(MatchBalance.killReward(true,0,0,0,0)<MatchBalance.killReward(true,9600,0,0,0)
                && MatchBalance.killReward(false,9600,0,0,2)>MatchBalance.killReward(false,9600,0,0,0),
                "Kill reward ramps to eight minutes and compensates a two-level underdog");
        for(var type : CharacterType.values()) h.assertTrue(
                com.myriad.character.SkillPresentation.impactSound(type)!=null,"Every hero has an impact sound");
        var p=player(h,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,4,2))),"xuanchen");
        var target=net.minecraft.world.entity.EntityTypes.COW.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        float before=target.getHealth();
        for(int i=0;i<16;i++) {
            var roll=com.myriad.combat.DamageCalc.dealUncriticalSkillDamage(p,target,1,com.myriad.combat.DamageCalc.Kind.MAGIC,3);
            h.assertTrue(!roll.crit(),"Beam damage must never crit");
        }
        h.assertTrue(before-target.getHealth()>15.9F,"All sixteen beam pulses must survive vanilla hurt cooldown");
        target.discard();AbilityRuntime.clear(p);p.discard();
    }
    static void verifyGeometry(GameTestHelper h) {
        Vec3 forward=new Vec3(0,0,1);
        Vec3 turn=com.myriad.character.ability.AbilityGeometry.turnToward(forward,new Vec3(0,0,-1),Math.PI/60);
        h.assertTrue(Math.abs(Math.acos(forward.dot(turn))-Math.PI/60)<.00001,
                "Shared finite beam turn cannot snap behind its caster");
    }
    private static void verifyFreeze(GameTestHelper h) {
        Vec3 at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,5,8)));
        var player=player(h,at,"yingxiao");
        var other=player(h,at.add(8,0,0),"shuo");
        var unrelated=player(h,at.add(0,0,8),"hanze");
        var d=PlayerDataProvider.get(player);
        d.setSkillLevel(FirstGroupSkills.node("yingxiao",7).getId(),1);
        try {
            com.myriad.combat.FrozenControlSystem.apply(h.getLevel(),player,player,60);
            com.myriad.combat.FrozenControlSystem.apply(h.getLevel(),player,other,60);
            com.myriad.combat.FrozenControlSystem.apply(h.getLevel(),unrelated,unrelated,60);
            com.myriad.combat.SilenceSystem.apply(player,60);
            com.myriad.combat.SilenceSystem.apply(other,60);
            h.assertTrue(shells(player)==3&&shells(other)==3,"Freeze creates three visible ice shells per target");
            SkillSystem.cast(player,CharacterType.SLOT_R);
            h.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)&&shells(player)==0,
                    "Real control-break removes the target's shared freeze and all ice shells");
            h.assertTrue(!com.myriad.combat.SilenceSystem.isSilenced(player)&&com.myriad.combat.SilenceSystem.isSilenced(other),
                    "Targeted cleanse removes only this player's silence");
            h.assertTrue(other.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)&&shells(other)==3,
                    "Targeted cleanse preserves the caster's freeze on other targets");
            com.myriad.combat.FrozenControlSystem.clearFor(player.getUUID());
            h.assertTrue(shells(other)==0&&shells(unrelated)==3,
                    "Owner cleanup removes its frozen shells and preserves unrelated sources");
        } finally {
            com.myriad.combat.FrozenControlSystem.clearFor(player.getUUID());
            com.myriad.combat.FrozenControlSystem.clearFor(other.getUUID());
            com.myriad.combat.FrozenControlSystem.clearFor(unrelated.getUUID());
            com.myriad.combat.SilenceSystem.cleanse(player);com.myriad.combat.SilenceSystem.cleanse(other);
            AbilityRuntime.clear(player);AbilityRuntime.clear(other);AbilityRuntime.clear(unrelated);
            player.discard();other.discard();unrelated.discard();
        }
    }
    private static long shells(net.minecraft.world.entity.LivingEntity target) {
        return target.level().getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
                target.getBoundingBox().inflate(2),net.minecraft.world.entity.Entity::isAlive).size();
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 at,String role) {
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setCharacterId(role);d.setLevel(20);d.setMana(700);
        return p;
    }
}
