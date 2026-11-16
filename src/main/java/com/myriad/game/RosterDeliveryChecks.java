package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yike.YikeStance;
import com.myriad.character.skills.yike.YikeState;
import com.myriad.network.NetworkSetup;
import com.myriad.network.YikeStatePayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Formal server entry and actual observer packets; no graphical client or visual acceptance. */
final class RosterDeliveryChecks {
    private record Viewer(ServerPlayer player,List<CustomPacketPayload> packets) {}
    private RosterDeliveryChecks() {}

    static void run(GameTestHelper h) {
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled();
        var previous=GameManager.get();
        var viewers=new ArrayList<Viewer>();
        boolean[] done={false};
        Runnable cleanup=()->{
            for(var viewer:viewers) {
                AbilityRuntime.clear(viewer.player);
                viewer.player.level().players().remove(viewer.player);
                h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(viewer.player.getUUID());
                viewer.player.remove(Entity.RemovalReason.DISCARDED);
            }
            set(null,"instance",previous);
            ModState.setEnabled(enabled);TrainingMode.setEnabled(training);
        };
        try {
            ModState.setEnabled(true);TrainingMode.setEnabled(false);
            GameManager.create();var manager=GameManager.get();manager.setServer(h.getLevel().getServer());
            var origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(-80,80,-80)));
            viewers.add(viewer(h,origin));
            viewers.add(viewer(h,origin.add(2,0,0)));
            viewers.add(viewer(h,origin.add(0,0,-5)));
            viewers.add(viewer(h,origin.add(50,0,0)));
            viewers.add(viewer(h,origin.add(100,0,0)));
            var owner=viewers.getFirst().player;
            verifyFormalEntries(h,owner);
            verifyDraft(h,manager,viewers.get(0).player,viewers.get(1).player);
            set(manager,"phase",GameManager.Phase.PLAYING);
            @SuppressWarnings("unchecked") var factions=(Map<UUID,Faction>)get(manager,"factions");
            factions.clear();
            for(int i:new int[]{0,1,4})factions.put(viewers.get(i).player.getUUID(),Faction.XUANXIAO);
            factions.put(viewers.get(3).player.getUUID(),Faction.YOUMING);
            for(var viewer:viewers)PlayerDataProvider.get(viewer.player).setFaction(factions.get(viewer.player.getUUID()));
            var data=PlayerDataProvider.get(owner);
            data.setCharacterId("yike");data.setLevel(20);data.setMana(700);
            data.refundAllSkillPointsForTraining();
            for(int index:new int[]{2,4})data.setSkillLevel(FirstGroupSkills.node("yike",index).getId(),1);
            owner.getInventory().setItem(0,ModItems.HONG_LIE.get().getDefaultInstance());
            owner.setYRot(0);owner.setXRot(0);
            SkillSystem.cast(owner,2);
            snapshot(h,viewers,4,0,0);
            for(int n=1;n<=18;n++) {
                final int tick=n;
                h.runAfterDelay(n,()->{
                    if(done[0])return;
                    try {
                        AbilityRuntime.tick(owner);
                        if(tick==10) {
                            h.assertTrue(YikeStance.active(owner),"Formal non-training E enters its paid stance");
                            snapshot(h,viewers,-1,1,5);
                            SkillSystem.cast(owner,0);
                        }
                        if(tick==13) {
                            for(int duplicate=0;duplicate<4;duplicate++)SkillSystem.cast(owner,0);
                            snapshot(h,viewers,2,1,5);
                        }
                        if(tick==16) {
                            h.assertTrue(YikeStance.shots(owner)==4&&data.getMana()==595,
                                    "Repeated pending input produces one paid shell in the formal match");
                            snapshot(h,viewers,-1,1,4);
                            PlayerDataProvider.syncToClient(owner);
                            var hud=last(viewers.getFirst(),NetworkSetup.PlayerDataPayload.class);
                            h.assertTrue(hud.characterId().equals("yike")&&!hud.training()&&hud.mana()==595
                                            &&hud.skillLevels().equals(data.getSkillLevels()),
                                    "Actual owner HUD packet agrees with formal role, mana and learned skills");
                            var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                            try {
                                NetworkSetup.PlayerDataPayload.STREAM_CODEC.encode(wire,hud);
                                var decoded=NetworkSetup.PlayerDataPayload.STREAM_CODEC.decode(wire);
                                h.assertTrue(decoded.characterId().equals(hud.characterId())&&decoded.mana()==595
                                                &&decoded.skillLevels().equals(hud.skillLevels()),
                                        "Owner state survives the actual wire codec");
                            } finally {wire.release();}
                        }
                        if(tick==18) {
                            viewers.forEach(v->v.packets.clear());AbilityRuntime.clear(owner);
                            for(int i:new int[]{0,1,2}) {
                                var clear=last(viewers.get(i),YikeStatePayload.class);
                                h.assertTrue(clear.mode()==0&&clear.shells()==0&&clear.windup()==-1,
                                        "Lifecycle clear sends the idle state to every prior observer");
                            }
                            done[0]=true;cleanup.run();
                            System.out.println("[MYRIAD-ROSTER13-DELIVERY] PASS: fourteen formal wire selections/112 candidates, new-role commands, draft turn/unique rules, paid formal stance/Q, actual owner/ally/enemy packets, hidden/far filtering, wire state and lifecycle clear");
                            h.succeed();
                        }
                    } catch(Throwable failure) {done[0]=true;cleanup.run();throw failure;}
                });
            }
        } catch(Throwable failure) {done[0]=true;cleanup.run();throw failure;}
    }

    private static void verifyFormalEntries(GameTestHelper h,ServerPlayer p) {
        int candidates=0;
        for(var type:CharacterType.playableValues()) {
            PlayerDataProvider.resetForHostSession(p);p.getInventory().clearContent();
            NetworkSetup.handleChooseCharacter(p,new NetworkSetup.ChooseCharacterPayload(type.getId()));
            var d=PlayerDataProvider.get(p);
            h.assertTrue(type.getId().equals(d.getCharacterId())&&d.getSkillPoints()==1
                            &&p.getInventory().getItem(0).is(ModItems.initialWeaponItem(type.getId())),
                    "Formal wire selection grants one skill point and locks the matching weapon: "+type.getId());
            candidates+=FirstGroupSkills.candidates(type.getId()).size();
        }
        h.assertTrue(CharacterType.playableValues().length==14&&candidates==112,
                "Formal selection exposes all fourteen complete eight-candidate heroes");
        try {
            for(String id:new String[]{"keheng","yike","rivet"}) {
                p.level().getServer().getCommands().getDispatcher().execute("myriad char "+id,p.createCommandSourceStack());
                h.assertTrue(id.equals(PlayerDataProvider.get(p).getCharacterId()),"New role command needs no training mode: "+id);
            }
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure) {throw new IllegalStateException(failure);}
    }

    @SuppressWarnings("unchecked")
    private static void verifyDraft(GameTestHelper h,GameManager manager,ServerPlayer first,ServerPlayer second) {
        set(manager,"phase",GameManager.Phase.PICKING);set(manager,"pickIndex",0);
        var order=(List<UUID>)get(manager,"pickOrder");order.clear();order.add(first.getUUID());order.add(second.getUUID());
        var factions=(Map<UUID,Faction>)get(manager,"factions");
        factions.put(first.getUUID(),Faction.XUANXIAO);factions.put(second.getUUID(),Faction.YOUMING);
        PlayerDataProvider.get(first).setCharacterId(null);PlayerDataProvider.get(second).setCharacterId(null);
        NetworkSetup.handleChooseCharacter(second,new NetworkSetup.ChooseCharacterPayload("keheng"));
        h.assertTrue(PlayerDataProvider.get(second).getCharacterId()==null,"Out-of-turn new-role wire input is rejected");
        NetworkSetup.handleChooseCharacter(first,new NetworkSetup.ChooseCharacterPayload("yike"));
        h.assertTrue("yike".equals(PlayerDataProvider.get(first).getCharacterId()),"Formal first pick accepts Yike");
        NetworkSetup.handleChooseCharacter(second,new NetworkSetup.ChooseCharacterPayload("yike"));
        h.assertTrue(PlayerDataProvider.get(second).getCharacterId()==null,"Duplicate new-role pick cannot steal an occupied hero");
        NetworkSetup.handleChooseCharacter(second,new NetworkSetup.ChooseCharacterPayload("keheng"));
        h.assertTrue("keheng".equals(PlayerDataProvider.get(second).getCharacterId())
                        &&((Map<?,?>)get(manager,"charPicks")).size()==2,
                "Formal second pick accepts Keheng without bypassing the unique roster");
    }

    private static void snapshot(GameTestHelper h,List<Viewer> viewers,int windup,int mode,int shells) {
        viewers.forEach(v->v.packets.clear());var p=viewers.getFirst().player;
        YikeState.sync(p);var owner=last(viewers.getFirst(),YikeStatePayload.class);
        h.assertTrue(owner.valid()&&owner.windup()==windup&&owner.mode()==mode&&owner.shells()==shells
                        &&owner.remaining()==YikeStance.remaining(p)&&owner.nextShot()==YikeStance.nextShotTicks(p),
                "Authoritative snapshot matches live preparation, stance, ammunition and real deadlines");
        if(windup==2)h.assertTrue(owner.progress()>0&&owner.progress()<1,"Pending Q publishes its actual partial progress");
        for(int i:new int[]{1,2})h.assertTrue(last(viewers.get(i),YikeStatePayload.class).equals(owner),
                "Ally and visible enemy receive the same authoritative state");
        for(int i:new int[]{3,4})h.assertTrue(viewers.get(i).packets.stream().noneMatch(YikeStatePayload.class::isInstance),
                "Unseen enemy and out-of-range ally receive no pose/aim disclosure");
        var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {YikeStatePayload.CODEC.encode(wire,owner);h.assertTrue(YikeStatePayload.CODEC.decode(wire).equals(owner),
                "Observer snapshot roundtrips the production wire codec");} finally {wire.release();}
    }

    private static Viewer viewer(GameTestHelper h,Vec3 pos) {
        var packets=new ArrayList<CustomPacketPayload>();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof ClientboundCustomPayloadPacket custom)packets.add(custom.payload());
            }
        };
        p.connection.markClientLoaded();p.setPos(pos);p.setNoGravity(true);
        PlayerDataProvider.get(p).setCharacterId("shuo");
        h.getLevel().players().add(p);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(),p);
        return new Viewer(p,packets);
    }
    private static <T> T last(Viewer viewer,Class<T> type) {
        return viewer.packets.stream().filter(type::isInstance).map(type::cast).reduce((a,b)->b).orElseThrow();
    }
    private static Object get(Object target,String name) {
        try {var field=GameManager.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
        catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static void set(Object target,String name,Object value) {
        try {var field=GameManager.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
        catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
}
