package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.CharacterType;
import com.myriad.combat.*;
import com.myriad.network.UiFeedbackPayload;
import com.myriad.player.*;
import com.myriad.registry.ModSounds;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validates real audio assets and authoritative confirmation, not a speculative swing. */
final class CombatImpactChecks {
    static void verify(GameTestHelper h,ServerPlayer player){
        try(var in=CombatImpactChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var hashes=new HashSet<Integer>();
            for(String family:List.of("blade","blunt","projectile","arcane","critical","structure","shield","swing")){
                String event=family.equals("swing")?"combat_swing":"combat_impact_"+family;
                var sounds=json.getAsJsonObject(event).getAsJsonArray("sounds");
                h.assertTrue(sounds.size()==3,"Frequent contact events each have three real sample variations");
                for(var sound:sounds){
                    String asset=sound.getAsString().split(":",2)[1];
                    try(var ogg=CombatImpactChecks.class.getResourceAsStream("/assets/myriad/sounds/"+asset+".ogg")){
                        h.assertTrue(ogg!=null,"Registered sample actually packaged: "+asset);
                        byte[] bytes=ogg.readAllBytes();hashes.add(Arrays.hashCode(bytes));
                        h.assertTrue(new String(bytes,0,4,StandardCharsets.US_ASCII).equals("OggS"),"Valid OGG container");
                        int ident=new String(bytes,StandardCharsets.ISO_8859_1).indexOf("\u0001vorbis");
                        h.assertTrue(ident>=0&&bytes[ident+11]==1
                                &&ByteBuffer.wrap(bytes,ident+12,4).order(ByteOrder.LITTLE_ENDIAN).getInt()==48000,"Mono 48kHz Vorbis, not Opus in an OGG wrapper");
                    }
                }
            }
            h.assertTrue(hashes.size()==24,"Twenty-four distinct audio files");
            try(var license=CombatImpactChecks.class.getResourceAsStream("/META-INF/licenses/combat-impact-sources.txt")){
                h.assertTrue(license!=null&&new String(license.readAllBytes(),StandardCharsets.UTF_8).contains("CC0"),"Source/permission notice is shipped");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        for(var profile:CombatImpactProfile.values())for(boolean crit:new boolean[]{false,true}){
            int kind=profile.payload(crit);
            h.assertTrue(CombatImpactProfile.isPayload(kind)&&CombatImpactProfile.fromPayload(kind)==profile
                    &&((kind-CombatImpactProfile.PAYLOAD_BASE)%2==1)==crit,"Stable compact outcome roundtrip");
            h.assertTrue(ModSounds.impact(profile)!=null,"Typed sound registration exists");
        }
        h.assertTrue(!CombatImpactProfile.isPayload(5)&&!CombatImpactProfile.isPayload(6)&&!CombatImpactProfile.isPayload(100),"Legacy marker/UI codes are not reinterpreted as sound profiles");
        h.assertTrue(CombatImpactProfile.choose(CharacterType.LINGYUAN,0,false,false,false)==CombatImpactProfile.BLADE,"Sword contact is not a spell-cast whoosh");
        h.assertTrue(CombatImpactProfile.choose(CharacterType.SHENLI,0,false,false,false)==CombatImpactProfile.PROJECTILE,"Ranged physical contact has its own material");
        h.assertTrue(CombatImpactProfile.choose(CharacterType.LINGYUAN,1,false,false,false)==CombatImpactProfile.ARCANE,"Actual magical damage uses the spell contact profile");
        h.assertTrue(CombatImpactProfile.choose(CharacterType.LINGYUAN,0,true,true,false)==CombatImpactProfile.STRUCTURE,"Structures retain their own impact even on crit");
        h.assertTrue(CombatImpactProfile.choose(CharacterType.LINGYUAN,0,true,false,true)==CombatImpactProfile.SHIELD,"Shield absorption does not pretend to hit flesh");
        h.assertTrue(CombatImpactProfile.allow(100,Long.MIN_VALUE,false,false,false),"First hit is immediate");
        h.assertTrue(!CombatImpactProfile.allow(100,100,false,true,false),"AoE and nested same-tick damage do not stack sounds");
        h.assertTrue(!CombatImpactProfile.allow(102,100,false,false,false)&&CombatImpactProfile.allow(103,100,false,false,false),"Direct contacts have a three-tick audio cap");
        h.assertTrue(!CombatImpactProfile.allow(105,100,false,false,true)&&CombatImpactProfile.allow(106,100,false,false,true),"Periodic contacts have a six-tick cap");
        h.assertTrue(CombatImpactProfile.allow(101,100,false,true,false)&&CombatImpactProfile.allow(10,100,false,false,false),"A later critical contact can supersede normal; world time reset does not lock feedback");

        var previous=player.connection;var data=PlayerDataProvider.get(player);String role=data.getCharacterId();
        var captured=new ArrayList<Integer>();boolean mod=ModState.isEnabled();
        var target=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(4,2,4));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof UiFeedbackPayload outcome)captured.add(outcome.kind());
            }
        };
        player.connection.markClientLoaded();
        try{
            data.setCharacterId("lingyuan");ModState.setEnabled(true);var state=player.getPersistentData();
            state.remove("myriad_impact_audio_at");
            CombatImpactFeedback.confirmed(player,target,0,false,false,0,false);
            CombatImpactFeedback.confirmed(player,target,0,false,false,Float.NaN,false);
            h.assertTrue(captured.isEmpty(),"No contact confirmation for zero/invalid effective loss");
            CombatImpactFeedback.confirmed(player,target,0,false,false,10,false);
            CombatImpactFeedback.confirmed(player,target,0,false,false,10,false);
            h.assertTrue(captured.size()==1&&captured.getFirst()==CombatImpactProfile.BLADE.payload(false),"One authoritative private audio outcome for repeated same-tick contacts");
            captured.clear();state.remove("myriad_impact_audio_at");
            ModState.setEnabled(false);CombatImpactFeedback.confirmed(player,target,0,false,false,10,false);
            h.assertTrue(captured.isEmpty(),"Mod off leaves vanilla audio untouched");
            ModState.setEnabled(true);state.remove("myriad_impact_audio_at");
            ModShieldSystem.grant(target,"impact_audio_test",500,40);float hp=target.getHealth();
            target.invulnerableTime=0;
            DamageCalc.dealUncriticalSkillDamage(player,target,12,DamageCalc.Kind.MAGIC,4);
            h.assertTrue(target.getHealth()==hp&&ModShieldSystem.value(target)<500,"The real incoming chain consumed shield without HP loss");
            h.assertTrue(captured.contains(CombatImpactProfile.SHIELD.payload(false)),"Full shield absorption still sends a real shield contact confirmation");
        }finally{
            player.connection=previous;data.setCharacterId(role);ModState.setEnabled(mod);
            player.getPersistentData().remove("myriad_impact_audio_at");player.getPersistentData().remove("myriad_impact_audio_crit");
            ModShieldSystem.clearFor(target.getUUID());target.discard();
        }
        System.out.println("[MYRIAD-IMPACT-AUDIO] PASS: 8 events / 24 mono Vorbis assets, CC0 credits, profile wire, contact cadence, mod-off, actual full-shield hit");
    }
}
