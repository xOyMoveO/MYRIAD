package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.io.InputStreamReader;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

final class FlameMajorAudioChecks {
    static void start(GameTestHelper h){
        try(var in=FlameMajorAudioChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String name:List.of("cage_cast","cage_open","burst_charge","burst_release","sea_jump","sea_impact","armor_ignite")){
                String id="skill_fenjue_"+name;
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("myriad",id)),"Registered major skill sound "+name);
                var sound=json.getAsJsonObject(id).getAsJsonArray("sounds").get(0).getAsJsonObject();
                h.assertTrue(sound.get("attenuation_distance").getAsInt()==24,"Local positional cue");
                try(var audio=FlameMajorAudioChecks.class.getResourceAsStream("/assets/myriad/sounds/skill/fenjue/"+name+".ogg")){
                    byte[] b=audio.readAllBytes();h.assertTrue(b.length<70_000&&b[0]=='O'&&b[1]=='g'&&b[2]=='g'&&b[3]=='S',"Small native Vorbis container");
                    int m=-1;for(int i=1;i<100;i++)if(new String(b,i,6,StandardCharsets.US_ASCII).equals("vorbis")){m=i;break;}
                    h.assertTrue(m>0&&b[m+10]==1&&ByteBuffer.wrap(b,m+11,4).order(ByteOrder.LITTLE_ENDIAN).getInt()==48000,"48kHz mono recording, not renamed MP3");
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var players=new ArrayList<ServerPlayer>();var heard=new ArrayList<String>();var saved=new HashMap<BlockPos,BlockState>();
        boolean cd=TrainingMode.isNoCooldown(),mana=TrainingMode.isNoMana();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getSound()!=null){var id=e.getSound().value().location().getPath();
            if(id.startsWith("skill_fenjue_cage_")||id.equals("skill_fenjue_armor_ignite")){
                h.assertTrue(!e.getLevel().isClientSide()&&e.getSource()==SoundSource.PLAYERS&&e.getOriginalVolume()<=.78f,"Logical server, bounded positional sound");heard.add(id);
            }}};
        NeoForge.EVENT_BUS.addListener(listener);
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int index=4;index<=7;index++){
                h.assertTrue(FirstGroupSkills.node("fenjue",index).hasOwnCastSound(),"Staged cues replace generic fire casting");
                var p=player(h,players,index);var skill=FirstGroupSkills.node("fenjue",index);var d=PlayerDataProvider.get(p);d.setSkillLevel(skill.getId(),1);d.setMana(0);
                SkillSystem.cast(p,skill.slot());h.assertTrue(heard.isEmpty(),"No sound for invalid no-mana requests");AbilityRuntime.clear(p);
            }
            for(int i=0;i<2;i++){
                var p=player(h,players,i);var skill=FirstGroupSkills.node("fenjue",4);PlayerDataProvider.get(p).setSkillLevel(skill.getId(),1);
                var floor=BlockPos.containing(p.position()).below();
                for(int x=-1;x<=1;x++)for(int z=-1;z<=12;z++)for(int y=0;y<=3;y++){
                    var at=floor.offset(x,y,z);saved.putIfAbsent(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
                }
                p.setXRot(20);SkillSystem.cast(p,skill.slot());h.assertTrue(count(heard,"cage_cast")==i,"Selecting a point must be silent");
                AbilityRuntime.confirm(p);h.assertTrue(count(heard,"cage_cast")==i+1&&count(heard,"cage_open")==0,"One cast sound, no premature field-open sound");
            }
            var armor=player(h,players,2);var s=FirstGroupSkills.node("fenjue",7);PlayerDataProvider.get(armor).setSkillLevel(s.getId(),1);
            SkillSystem.cast(armor,s.slot());h.assertTrue(count(heard,"armor_ignite")==1&&ModShieldSystem.value(armor)>0,"Immediate shield and exactly one armor ignition");
            SkillSystem.cast(armor,s.slot());h.assertTrue(count(heard,"armor_ignite")==1,"Cooldown repeat cannot duplicate ignition");
        }catch(Throwable error){NeoForge.EVENT_BUS.unregister(listener);cleanup(h,players,saved);throw error;}
        finally{TrainingMode.setNoCooldown(cd);TrainingMode.setNoMana(mana);}
        boolean[] done={false};
        for(int i=1;i<=15;i++){int t=i;h.runAfterDelay(i,()->{if(done[0])return;try{
            if(t==4)AbilityRuntime.clear(players.get(5));
            AbilityRuntime.tick(players.get(4));
            if(t<12)h.assertTrue(count(heard,"cage_open")==0,"No opening before the actual twelve-tick warning");
            if(t==15){h.assertTrue(count(heard,"cage_cast")==2&&count(heard,"cage_open")==1,"One real field-open; cancelled field cannot open or sound");
                done[0]=true;NeoForge.EVENT_BUS.unregister(listener);cleanup(h,players,saved);
                System.out.println("[MYRIAD-FLAME-MAJOR-AUDIO] PASS: seven mono48k assets/events, actual no-mana/select/confirm/cooldown requests, real twelve-tick cage activation, cancelled cage silent, immediate armor shield/one ignition");}
        }catch(Throwable error){done[0]=true;NeoForge.EVENT_BUS.unregister(listener);cleanup(h,players,saved);throw error;}});}
    }
    private static long count(List<String> heard,String id){return heard.stream().filter(s->s.equals("skill_fenjue_"+id)).count();}
    private static ServerPlayer player(GameTestHelper h,List<ServerPlayer> players,int i){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);players.add(p);
        var con=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(con);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),con,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(170+i*8,230,70))));
        p.setYRot(0);p.setXRot(0);var d=PlayerDataProvider.get(p);d.setCharacterId("fenjue");d.setLevel(20);d.setMana(700);d.setFaction(Faction.XUANXIAO);return p;
    }
    private static void cleanup(GameTestHelper h,List<ServerPlayer> players,Map<BlockPos,BlockState> saved){players.forEach(p->{AbilityRuntime.clear(p);p.discard();});saved.forEach((at,s)->h.getLevel().setBlockAndUpdate(at,s));}
}
