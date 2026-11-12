package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.fenjue.rework.FlameSlashAudio;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.Consumer;

/** Validate real supplied recordings and actual public casts, not only a mocked sound helper. */
final class FenjueSlashAudioChecks {
    static void verify(GameTestHelper h){
        try(var in=FenjueSlashAudioChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String action:List.of("blade","fire")){
                String id="skill_fenjue_slash_"+action;
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("myriad",id)),"New slash layer is registered: "+id);
                var entries=json.getAsJsonObject(id).getAsJsonArray("sounds");h.assertTrue(entries.size()==1,"One authored double-slash recording per layer, not a 10s collection or client loop");
                var sound=entries.get(0).getAsJsonObject();h.assertTrue(sound.get("attenuation_distance").getAsInt()==24,"Local positional combat cue, not map-wide");
                String name=sound.get("name").getAsString().substring("myriad:".length());
                try(var ogg=FenjueSlashAudioChecks.class.getResourceAsStream("/assets/myriad/sounds/"+name+".ogg")){
                    h.assertTrue(ogg!=null,"Layer OGG is packaged: "+name);byte[] bytes=ogg.readAllBytes();
                    h.assertTrue(bytes.length<100_000&&Arrays.equals(Arrays.copyOf(bytes,4),new byte[]{'O','g','g','S'}),"Small actual OGG container");
                    int marker=-1;byte[] signature="vorbis".getBytes(StandardCharsets.US_ASCII);
                    for(int i=1;i<Math.min(120,bytes.length-15);i++)if(Arrays.equals(Arrays.copyOfRange(bytes,i,i+6),signature)){marker=i;break;}
                    h.assertTrue(marker>0&&bytes[marker-1]==1&&bytes[marker+10]==1
                        &&ByteBuffer.wrap(bytes,marker+11,4).order(ByteOrder.LITTLE_ENDIAN).getInt()==48000,"48kHz mono Vorbis, not a renamed MP3");
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var skill=FirstGroupSkills.node("fenjue",2);
        h.assertTrue(skill.hasOwnCastSound()&&FirstGroupSkills.node("fenjue",3).hasOwnCastSound()&&FirstGroupSkills.node("fenjue",5).hasOwnCastSound(),"Slash, Step and the newly recorded Burst own their cues without generic duplication");
        h.assertTrue(FlameSlashAudio.FIRE_VOLUME<FlameSlashAudio.BLADE_VOLUME*.5,"Fire is a quieter extra layer, not a replacement for selected slash");
        var players=new ArrayList<ServerPlayer>();var sounds=new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getSound()!=null){String path=e.getSound().value().location().getPath();
            if(path.startsWith("skill_fenjue_slash_")||path.equals("foley_fire"))sounds.add(e);}};
        boolean noMana=TrainingMode.isNoMana(),noCd=TrainingMode.isNoCooldown();NeoForge.EVENT_BUS.addListener(listener);
        try{
            TrainingMode.setNoMana(false);TrainingMode.setNoCooldown(false);
            for(int i=0;i<3;i++){
                var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);players.add(p);
                var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                new io.netty.channel.embedded.EmbeddedChannel(connection);
                p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
                };
                p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(120+i*10,250,150))));
                p.setYRot(0);p.setXRot(0);var d=PlayerDataProvider.get(p);d.setCharacterId("fenjue");d.setLevel(20);d.setMana(i==2?0:700);d.setSkillLevel(skill.getId(),1);
                if(i==1)d.setBuff("g1_stun",30);
                int before=sounds.size();SkillSystem.cast(p,skill.slot());
                h.assertTrue(sounds.size()-before==(i==0?2:0),"Success emits exactly two layers; stunned or no-mana requests emit none: "+i);
                if(i==0){
                    h.assertTrue(d.getCooldown(skill.getId())>0&&d.getMana()==700-skill.getManaCost(),"The original successful resource transaction is retained");
                    SkillSystem.cast(p,skill.slot());h.assertTrue(sounds.size()==2,"Repeated/cooldown input cannot echo either layer");
                }
            }
            h.assertTrue(sounds.stream().filter(e->e.getSound().value().location().getPath().equals("skill_fenjue_slash_blade")).count()==1
                &&sounds.stream().filter(e->e.getSound().value().location().getPath().equals("skill_fenjue_slash_fire")).count()==1,"Each selected layer plays once, with no generic fire cast stacked over it");
            h.assertTrue(sounds.stream().allMatch(e->e.getSource()==SoundSource.PLAYERS&&!e.getLevel().isClientSide()&&e.getOriginalVolume()<=.70f),"Nearby server positional PLAYERS audio, bounded volume and no client echo");
            System.out.println("[MYRIAD-FENJUE-SLASH-AUDIO] PASS: two native mono OGG assets / two registered layers; actual success, cooldown, control and no-mana requests; original cost/CD, no generic duplicate");
        }finally{
            NeoForge.EVENT_BUS.unregister(listener);players.forEach(p->{AbilityRuntime.clear(p);p.discard();});
            TrainingMode.setNoMana(noMana);TrainingMode.setNoCooldown(noCd);
        }
    }
}
