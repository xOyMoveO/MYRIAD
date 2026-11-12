package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.*;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Validate packaged assets and capture actual server sound events during real skill casts. */
final class ThunderAudioChecks {
    static void verify(GameTestHelper h){
        try(var in=ThunderAudioChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String action:List.of("charge","cast","hit","guard","pulse","judgment")){
                String id="skill_tianxing_"+action;
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("myriad",id)),"Registered sound event: "+id);
                var variants=json.getAsJsonObject(id).getAsJsonArray("sounds");
                h.assertTrue(variants.size()==2,"Two recording edits selected by sounds.json, not random gameplay code");
                for(var v:variants){
                    var item=v.getAsJsonObject();String name=item.get("name").getAsString().substring("myriad:".length());
                    h.assertTrue(item.get("attenuation_distance").getAsInt()<=36,"Bounded positional cue range");
                    try(var ogg=ThunderAudioChecks.class.getResourceAsStream("/assets/myriad/sounds/"+name+".ogg")){
                        h.assertTrue(ogg!=null,"Packaged OGG: "+name);
                        h.assertTrue(Arrays.equals(ogg.readNBytes(4),new byte[]{'O','g','g','S'}),"Real Ogg container");
                    }
                }
            }
            for(int i=2;i<8;i++)h.assertTrue(FirstGroupSkills.node("tianxing",i).hasOwnCastSound(),"Six active modules own staged audio");
            h.assertTrue(!FirstGroupSkills.node("yingxiao",2).hasOwnCastSound(),"Other characters retain their audio policy");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    static void start(GameTestHelper h){
        var players=new ArrayList<ServerPlayer>();var sounds=new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{
            if(e.getSound()!=null){String path=e.getSound().value().location().getPath();
                if(path.startsWith("skill_tianxing_")||path.equals("foley_thunder"))sounds.add(e);}
        };
        NeoForge.EVENT_BUS.addListener(listener);
        boolean noMana=TrainingMode.isNoMana(),noCd=TrainingMode.isNoCooldown();
        try{
            TrainingMode.setNoMana(false);TrainingMode.setNoCooldown(false);
            for(int i=0;i<6;i++){
                var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
                var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                new io.netty.channel.embedded.EmbeddedChannel(connection);
                p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
                };
                p.connection.markClientLoaded();p.setNoGravity(true);
                p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(80+i*3,250,100))));p.setYRot(0);p.setXRot(0);
                players.add(p);var d=PlayerDataProvider.get(p);d.setCharacterId("tianxing");d.setLevel(20);d.setMana(700);
                var skill=FirstGroupSkills.node("tianxing",i<2?3:i<4?5:7);d.setSkillLevel(skill.getId(),1);
                SkillSystem.cast(p,skill.slot());
                h.assertTrue(count(sounds,p,"charge")==1,"One start cue from the actual cast");
                h.assertTrue(count(sounds,p,"cast")==0&&count(sounds,p,"judgment")==0,"No release sound during windup");
            }
        }catch(Throwable failure){cleanup(players,listener);throw failure;}
        finally{TrainingMode.setNoMana(noMana);TrainingMode.setNoCooldown(noCd);}
        boolean[] done={false};
        for(int tick=1;tick<=20;tick++){final int elapsed=tick;h.runAfterDelay(tick,()->{
            if(done[0])return;
            try{
                if(elapsed==3)for(int i:new int[]{1,3,5})PlayerDataProvider.get(players.get(i)).setBuff("g1_stun",30);
                players.forEach(AbilityRuntime::tick);
                if(elapsed==20){
                    for(int i=0;i<6;i++){
                        var p=players.get(i);String release=i<4?"cast":"judgment";
                        h.assertTrue(count(sounds,p,release)==(i%2==0?1:0),"Successful completion plays once; interrupted charge never plays release: "+i);
                        h.assertTrue(count(sounds,p,"charge")==1,"Charge never loops every tick");
                        h.assertTrue(sounds.stream().noneMatch(e->e.getPosition().distanceToSqr(p.position())<.1&&e.getSound().value().location().getPath().equals("foley_thunder")),"No old generic thunder layered over new cast");
                    }
                    h.assertTrue(sounds.stream().allMatch(e->e.getSource()==SoundSource.PLAYERS&&!e.getLevel().isClientSide()&&e.getOriginalVolume()<=.75f),"Server positional PLAYERS category and bounded volume");
                    done[0]=true;cleanup(players,listener);
                    System.out.println("[MYRIAD-THUNDER-AUDIO] PASS: six registered events / twelve OGGs; actual arc/slash/judgment start, completion and interruption; no generic cast duplication");
                }
            }catch(Throwable failure){done[0]=true;cleanup(players,listener);throw failure;}
        });}
    }
    private static long count(List<PlayLevelSoundEvent.AtPosition> sounds,ServerPlayer p,String action){
        return sounds.stream().filter(e->e.getPosition().distanceToSqr(p.position())<.1&&e.getSound().value().location().getPath().equals("skill_tianxing_"+action)).count();
    }
    private static void cleanup(List<ServerPlayer> players,Consumer<PlayLevelSoundEvent.AtPosition> listener){
        NeoForge.EVENT_BUS.unregister(listener);
        players.forEach(p->{AbilityRuntime.clear(p);p.discard();});
    }
}
