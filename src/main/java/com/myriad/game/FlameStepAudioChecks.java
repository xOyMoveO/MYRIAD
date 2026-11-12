package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.io.InputStreamReader;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

final class FlameStepAudioChecks {
    static void verify(GameTestHelper h){
        try(var input=FlameStepAudioChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String action:List.of("wind","slash")){
                String id="skill_fenjue_step_"+action;
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("myriad",id)),"Registered Step sound: "+id);
                var entries=json.getAsJsonObject(id).getAsJsonArray("sounds");h.assertTrue(entries.size()==1,"Single short recorded cue per phase");
                var sound=entries.get(0).getAsJsonObject();h.assertTrue(sound.get("attenuation_distance").getAsInt()==24,"Positional combat sound, not map-wide");
                String name=sound.get("name").getAsString().substring("myriad:".length());
                try(var ogg=FlameStepAudioChecks.class.getResourceAsStream("/assets/myriad/sounds/"+name+".ogg")){
                    h.assertTrue(ogg!=null,"OGG is packaged");byte[] bytes=ogg.readAllBytes();
                    h.assertTrue(bytes.length<50_000&&Arrays.equals(Arrays.copyOf(bytes,4),new byte[]{'O','g','g','S'}),"Small native Ogg container");
                    int marker=-1;byte[] signature="vorbis".getBytes(StandardCharsets.US_ASCII);
                    for(int i=1;i<Math.min(120,bytes.length-15);i++)if(Arrays.equals(Arrays.copyOfRange(bytes,i,i+6),signature)){marker=i;break;}
                    h.assertTrue(marker>0&&bytes[marker-1]==1&&bytes[marker+10]==1&&ByteBuffer.wrap(bytes,marker+11,4).order(ByteOrder.LITTLE_ENDIAN).getInt()==48000,"48kHz mono Vorbis, not renamed MP3/stereo");
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var skill=FirstGroupSkills.node("fenjue",3);var players=new ArrayList<ServerPlayer>();
        var heard=new ArrayList<String>();Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getSound()!=null){
            String id=e.getSound().value().location().getPath();if(id.startsWith("skill_fenjue_step_")||id.equals("foley_fire"))heard.add(id);
        }};
        NeoForge.EVENT_BUS.addListener(listener);boolean oldCd=TrainingMode.isNoCooldown(),oldMana=TrainingMode.isNoMana(),oldMod=ModState.isEnabled();
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int test=0;test<5;test++){
                var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);players.add(p);
                var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
                p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
                };
                p.connection.markClientLoaded();var d=com.myriad.player.PlayerDataProvider.get(p);d.setCharacterId("fenjue");d.setLevel(20);d.setSkillLevel(skill.getId(),1);d.setMana(test==1?0:700);
                if(test==0)d.setBuff("g1_stun",30);if(test==2)d.setCooldown(skill.getId(),30);if(test==3)d.setBuff("g1_root",30);if(test==4)ModState.setEnabled(false);
                SkillSystem.cast(p,skill.slot());h.assertTrue(heard.isEmpty(),"Stun/no-mana/cooldown/root/mod-off never starts either sound, case="+test);
                ModState.setEnabled(oldMod);
            }
            h.assertTrue(skill.hasOwnCastSound(),"Step's recorded whoosh replaces only its old generic cast Foley");
            System.out.println("[MYRIAD-FLAME-STEP-AUDIO-ASSETS] PASS: two mono48k OGGs/events, local range; actual stun/mana/cooldown/root/mod-off requests are silent");
        }finally{
            NeoForge.EVENT_BUS.unregister(listener);players.forEach(p->{AbilityRuntime.clear(p);p.discard();});
            TrainingMode.setNoCooldown(oldCd);TrainingMode.setNoMana(oldMana);ModState.setEnabled(oldMod);
        }
    }
}
