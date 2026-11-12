package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.skills.mingzhu.MingZhuSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class MingZhuRegressionChecks {
    static void verify(GameTestHelper h) throws ReflectiveOperationException {
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
        ServerPlayer p = (ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        p.connection.markClientLoaded();
        PlayerData d=PlayerDataProvider.get(p);
        d.setCharacterId("mingzhu");d.setLevel(1);CharacterType.applyInitialAttributes(p,CharacterType.MINGZHU);
        com.myriad.player.LevelSystem.applyLevelAttributes(p,1);
        com.myriad.player.PlayerHealthSystem.fill(p);
        h.assertTrue(Math.abs(d.getModMaxHealth()-CharacterType.balancedInitialHp(CharacterType.MINGZHU))<.001,
                "Mingzhu must use the global opening-health balance factor");
        d.setMingzhuEnergy(999);h.assertTrue(d.getMingzhuEnergy()==400,"Solar energy cap");
        PlayerData copy=new PlayerData();copy.copyStateFrom(d);
        h.assertTrue(copy.getMingzhuEnergy()==400,"Reconnect copy preserves solar energy");
        d.setMingzhuEnergy(119);d.setMana(135);
        var r=CharacterType.MINGZHU.skill(3);
        MingZhuSkills.handleCast(p,d,3,r,false,false);
        h.assertTrue(!MingZhuSkills.channeling(p)&&d.getMana()==135&&d.getCooldown(r.getId())==0,"Low-energy R must be free and rejected");
        d.setMingzhuEnergy(120);double speed=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        MingZhuSkills.handleCast(p,d,3,r,false,false);
        h.assertTrue(MingZhuSkills.channeling(p)&&d.getMana()==95&&d.getCooldown(r.getId())==0,"R consumes mana but defers cooldown");
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*.45)<.0001,"R movement is 45 percent");
        MingZhuSkills.handleCast(p,d,3,r,false,false);
        h.assertTrue(!MingZhuSkills.channeling(p)&&d.getMingzhuEnergy()==120&&d.getCooldown(r.getId())>0,"Manual cancellation preserves energy and starts cooldown");
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<.0001,"Cancellation restores movement");
        Vec3 forward=new Vec3(0,0,1);
        Vec3 turn=MingZhuSkills.turnToward(forward,new Vec3(0,0,-1),Math.PI/60);
        h.assertTrue(Math.abs(Math.acos(forward.dot(turn))-Math.PI/60)<.00001,"Beam cannot snap behind its caster");
        var target=net.minecraft.world.entity.EntityTypes.COW.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        float before=target.getHealth();
        for(int i=0;i<16;i++) {
            var roll=DamageCalc.dealUncriticalSkillDamage(p,target,1,DamageCalc.Kind.MAGIC,3);
            h.assertTrue(!roll.crit(),"Beam damage must never crit");
        }
        h.assertTrue(before-target.getHealth()>15.9F,"All sixteen beam pulses must survive vanilla hurt cooldown");
        verifyRework(h,p,d);
        MingZhuSkills.clearFor(p,true);
        h.assertTrue(d.getMingzhuEnergy()==0&&!MingZhuSkills.channeling(p),"Death/reset clears energy and channel");
        d.setCharacterId("mingzhu"); d.setLevel(15); d.setMingzhuEnergy(400);
        d.setBuff("reset_probe", 100);
        p.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        PlayerDataProvider.resetForHostSession(p);
        var clean = PlayerDataProvider.get(p);
        h.assertTrue(clean.getCharacterId()==null && clean.getFaction()==null && clean.getLevel()==1
                && clean.getMingzhuEnergy()==0 && clean.getBuff("reset_probe")==0
                && p.getInventory().getItem(0).isEmpty(),
                "Full player reset clears role, faction, progression, resources, buffs, runes and match inventory");
        target.discard();p.discard();
    }

    private static Object state(ServerPlayer p) throws ReflectiveOperationException {
        var m=MingZhuSkills.class.getDeclaredMethod("state",ServerPlayer.class);m.setAccessible(true);return m.invoke(null,p);
    }
    private static Object get(Object s,String name) throws ReflectiveOperationException {
        var f=s.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(s);
    }
    private static void put(Object s,String name,Object value) throws ReflectiveOperationException {
        var f=s.getClass().getDeclaredField(name);f.setAccessible(true);f.set(s,value);
    }
    private static void verifyRework(GameTestHelper h,ServerPlayer p,PlayerData d) throws ReflectiveOperationException {
        MingZhuSkills.clearFor(p,true);d.setMana(135);d.setMingzhuEnergy(120);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,12,2));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setYRot(0);p.setXRot(0);
        for(int i=0;i<40;i++)MingZhuSkills.tick(p,d);
        h.assertTrue(!MingZhuSkills.channeling(p)&&d.getMana()==135&&d.getMingzhuEnergy()==120,"Reaching 30 energy while idle must never auto-cast R");
        var weaponName=PlayerDataProvider.class.getDeclaredMethod("initialWeaponName",String.class);
        weaponName.setAccessible(true);
        var name=(net.minecraft.network.chat.Component)weaponName.invoke(null,"mingzhu");
        h.assertTrue(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && translated.getKey().equals("item.myriad.mingzhu_core"),"Mingzhu weapon must use Solar Nexus name");
        d.setMingzhuHeroCombatAt(h.getLevel().getGameTime()-1000);
        for(int energy : new int[]{241, 400, 775, 800}) {
            d.setMingzhuEnergyEighths(energy);
            // Force the old decay timer due, even though this synchronous test does not advance world time.
            put(state(p),"decayAt",0L);
            MingZhuSkills.tick(p,d);
            h.assertTrue(d.getMingzhuEnergyEighths()==energy&&!MingZhuSkills.channeling(p),
                    "Energy above thirty must persist out of combat without an explicit R input");
        }
        for(var type : CharacterType.values()) h.assertTrue(
                com.myriad.character.SkillPresentation.impactSound(type)!=null,"Every hero has an impact sound");
        d.setMingzhuEnergyEighths(775);var saved=new PlayerData();saved.copyStateFrom(d);
        h.assertTrue(saved.getMingzhuEnergyEighths()==775,"Half-cost energy preserves eighth-unit remainder on reconnect");
        Object s=state(p);put(s,"mirror",p.position().add(0,0,2));put(s,"mirrorDir",new Vec3(0,0,1));put(s,"mirrorUntil",h.getLevel().getGameTime()+80);put(s,"wLevel",1);
        Class<?> ball=Class.forName("com.myriad.character.skills.mingzhu.MingZhuSkills$Ball");
        var constructor=ball.getDeclaredConstructor(Vec3.class,Vec3.class,boolean.class,float.class);constructor.setAccessible(true);
        Object b=constructor.newInstance(p.getEyePosition().add(0,0,1.5),new Vec3(0,0,1),true,100F);put(b,"travelled",9D);
        var tickBall=MingZhuSkills.class.getDeclaredMethod("tickBall",ServerPlayer.class,PlayerData.class,s.getClass(),ball);tickBall.setAccessible(true);tickBall.invoke(null,p,d,s,b);
        h.assertTrue((Boolean)get(b,"reflected")&&Math.abs((Float)get(b,"damage")-130)<.001&&(Double)get(b,"travelled")==0,"Q mirror: +30 percent and fresh travel budget");
        tickBall.invoke(null,p,d,s,b);
        h.assertTrue(Math.abs((Double)get(b,"travelled")-1.8)<.001,"Refracted Q moves at 36 blocks/s");
        Vec3 beforeDash=p.position();
        MingZhuSkills.handleCast(p,d,2,CharacterType.MINGZHU.skill(2),true,true);
        h.assertTrue(p.position().equals(beforeDash)&&Math.abs(p.getDeltaMovement().horizontalDistance()-.5)<.001,
                "Solar Step starts motion immediately without teleporting the player");
        double initialShield=com.myriad.player.ModShieldSystem.value(p);
        h.assertTrue((Double)get(s,"dashLimit")==5,"E starts with five-block motion budget");
        p.setPos(p.getX(),p.getY(),p.getZ()+2.4); // Simulate a completed client/server movement for swept crossing.
        var dash=MingZhuSkills.class.getDeclaredMethod("tickDash",ServerPlayer.class,s.getClass());dash.setAccessible(true);dash.invoke(null,p,s);
        h.assertTrue((Double)get(s,"dashLimit")==9&&com.myriad.player.ModShieldSystem.value(p)>initialShield+1.39,"Crossing mirror grants four blocks and ten-percent AP shield");
        dash.invoke(null,p,s);h.assertTrue((Double)get(s,"dashLimit")==9,"One E cannot repeatedly farm its mirror");
        Vec3 endMotion=p.getDeltaMovement();
        var finish=MingZhuSkills.class.getDeclaredMethod("finishDash",ServerPlayer.class,s.getClass());finish.setAccessible(true);finish.invoke(null,p,s);
        h.assertTrue(p.getDeltaMovement().equals(endMotion),"Motion handoff must not delete residual dash velocity");
        MingZhuSkills.clearFor(p,true);d.setMingzhuEnergy(400);
        MingZhuSkills.handleCast(p,d,3,CharacterType.MINGZHU.skill(3),true,true);s=state(p);
        var cow=net.minecraft.world.entity.EntityTypes.COW.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);cow.setPos(p.getX(),p.getY()+.5,p.getZ()+6);h.getLevel().addFreshEntity(cow);
        var pulse=MingZhuSkills.class.getDeclaredMethod("pulse",ServerPlayer.class,PlayerData.class,s.getClass(),long.class);pulse.setAccessible(true);
        for(int i=0;i<5;i++)pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+i*5);
        h.assertTrue((Integer)get(s,"radiance")==10&&d.getMingzhuEnergyEighths()==550,"Five successful NPC pulses build 10 radiance at full energy cost");
        cow.setPos(p.getX(),p.getY()+.5,p.getZ()+24);
        float hp=cow.getHealth();pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+25);
        h.assertTrue(hp-cow.getHealth()>13&& (Integer)get(s,"radiance")==2,"Sixth pulse is double damage and consumes 10 radiance");
        put(s,"mirror",p.position().add(0,0,2));put(s,"mirrorDir",new Vec3(0,0,1));put(s,"mirrorUntil",h.getLevel().getGameTime()+80);
        cow.setPos(p.getX(),p.getY()+.5,p.getZ()+6);
        var segments=MingZhuSkills.class.getDeclaredMethod("beamSegments",ServerPlayer.class,s.getClass(),boolean.class);segments.setAccessible(true);
        h.assertTrue(((java.util.List<?>)segments.invoke(null,p,s,false)).size()==4,"R has one incoming and three outgoing mirror segments");
        hp=cow.getHealth();pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+30);
        h.assertTrue(hp-cow.getHealth()>7&&hp-cow.getHealth()<8,"Mirror fan adds ten percent without overlapping triple damage");
        cow.discard();
        MingZhuSkills.clearFor(p,true);
        ServerPlayer enemy=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"beam-target"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            // GameTest levels disable PvP by default; this fixture tests the skill settlement.
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return true; }
        };
        enemy.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),enemy,
                net.minecraft.server.network.CommonListenerCookie.createInitial(enemy.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        enemy.connection.markClientLoaded();
        enemy.setGameMode(GameType.SURVIVAL);
        var enemyData=PlayerDataProvider.get(enemy);enemyData.setCharacterId("tianxing");enemyData.setFaction(Faction.YOUMING);
        CharacterType.applyInitialAttributes(enemy,CharacterType.TIANXING);com.myriad.player.LevelSystem.applyLevelAttributes(enemy,1);
        com.myriad.player.PlayerHealthSystem.fill(enemy);enemy.invulnerableTime=0;
        enemy.setPos(p.getX(),p.getY(),p.getZ()+6);h.getLevel().addNewPlayer(enemy);
        var oldFaction=d.getFaction();d.setFaction(Faction.XUANXIAO);d.setMingzhuEnergy(400);
        MingZhuSkills.handleCast(p,d,3,CharacterType.MINGZHU.skill(3),true,true);s=state(p);
        var select=MingZhuSkills.class.getDeclaredMethod("targets",ServerPlayer.class,Vec3.class,Vec3.class,double.class,boolean.class);select.setAccessible(true);
        var selected=(java.util.List<?>)select.invoke(null,p,p.getEyePosition(),p.getEyePosition().add(0,0,18),.4,false);
        h.assertTrue(selected.contains(enemy),"Enemy fixture must be in beam query; pos="+enemy.position()+", p="+p.position()+", box="+enemy.getBoundingBox()+", spectator="+enemy.isSpectator()+", removed="+enemy.isRemoved()+", changing="+enemy.isChangingDimension());
        pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+40);
        h.assertTrue(d.getMingzhuEnergyEighths()==775,"Actual hero damage must cost exactly 3.125 energy; actual="+d.getMingzhuEnergyEighths()+", hp="+enemy.getHealth()+", loaded="+enemy.connection.hasClientLoaded()+", invulnerable="+enemy.isInvulnerableTo(h.getLevel(),enemy.damageSources().playerAttack(p))+", abilities="+enemy.getAbilities().invulnerable+", changing="+enemy.isChangingDimension());
        enemy.setInvulnerable(true);pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+45);
        h.assertTrue(d.getMingzhuEnergyEighths()==725&&(Integer)get(s,"radiance")==2,"Invulnerable hero grants neither half-cost nor radiance");
        enemy.setInvulnerable(false);com.myriad.player.ModShieldSystem.grant(enemy,"beam_test",100,80);
        float protectedHp=enemy.getHealth();pulse.invoke(null,p,d,s,h.getLevel().getGameTime()+50);
        h.assertTrue(d.getMingzhuEnergyEighths()==700&&enemy.getHealth()==protectedHp&&(Integer)get(s,"radiance")==4,"Actual mod-shield damage also qualifies for half-cost and radiance");
        com.myriad.player.ModShieldSystem.clearFor(enemy);
        enemy.discard();d.setFaction(oldFaction);MingZhuSkills.clearFor(p,true);
    }
}
