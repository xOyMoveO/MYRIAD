package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import java.nio.file.Files;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Identity, saved-data compatibility and actual server/model bindings; not visual acceptance. */
final class FactionIdentityChecks {
    static void run(GameTestHelper h){
        try{
            h.assertTrue(Faction.byId("guardian")==Faction.GUARDIAN&&Faction.byId("守护")==Faction.GUARDIAN,"Guardian input");
            h.assertTrue(Faction.byId("destruction")==Faction.DESTRUCTION&&Faction.byId("毁灭")==Faction.DESTRUCTION,"Destruction input");
            h.assertTrue(Faction.byId("xuanxiao")==Faction.GUARDIAN&&Faction.byId("youming")==Faction.DESTRUCTION,"Historical map IDs remain readable");
            h.assertTrue(com.myriad.network.NetworkSetup.factionId(Faction.GUARDIAN)==1&&com.myriad.network.NetworkSetup.factionId(Faction.DESTRUCTION)==2,"Wire team IDs unchanged");
            for(var team:Faction.values()){
                String json=MatchProgress.JSON.toJson(team);
                h.assertTrue(json.equals('"'+team.getName()+'"'),"New saves only write current team names");
                h.assertTrue(MatchProgress.JSON.fromJson(json,Faction.class)==team,"Current IDs round-trip");
            }
            h.assertTrue(MatchProgress.JSON.fromJson("\"XUANXIAO\"",Faction.class)==Faction.GUARDIAN&&MatchProgress.JSON.fromJson("\"YOUMING\"",Faction.class)==Faction.DESTRUCTION,"Old Gson enum names are readable");
            var oldPlayer=MatchProgress.JSON.fromJson("{\"faction\":\"XUANXIAO\"}",PlayerData.class);
            h.assertTrue(oldPlayer.getFaction()==Faction.GUARDIAN,"Existing player attachment keeps its team");
            var decoded=PlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    new com.google.gson.JsonPrimitive("{\"faction\":\"YOUMING\",\"gold\":77}")).getOrThrow();
            h.assertTrue(decoded.getFaction()==Faction.DESTRUCTION&&decoded.getGold()==77,"Production player codec reads historical team without resetting progression");
            var encoded=PlayerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,decoded).getOrThrow().getAsString();
            h.assertTrue(encoded.contains("\"faction\":\"destruction\"")&&!encoded.contains("YOUMING"),"Production player codec writes current IDs");
            h.assertTrue((Faction.GUARDIAN.rgb()&255)>(Faction.GUARDIAN.rgb()>>16)&& (Faction.DESTRUCTION.rgb()>>16)>(Faction.DESTRUCTION.rgb()&255),"Guardian blue, Destruction red");
            h.assertTrue(ModItems.defenseTower(Faction.GUARDIAN,1)==ModItems.TOWER_01_BLUE.get()&&ModItems.defenseTower(Faction.DESTRUCTION,3)==ModItems.TOWER_03_RED.get(),"Tower models match teams");
            h.assertTrue(ModItems.minion(Faction.GUARDIAN,0)==ModItems.MINION_MELEE_BLUE.get()&&ModItems.minion(Faction.DESTRUCTION,2)==ModItems.MINION_MAGIC_RED.get(),"Minion models match teams");
            verifyCheckpoint(h);verifyMapAliases(h);verifyScoreboard(h);
            System.out.println("[MYRIAD-FACTIONS] PASS: canonical IDs, old player/checkpoint/map input, numeric network IDs, blue/red models and scoreboard teams");
            h.succeed();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void verifyCheckpoint(GameTestHelper h){
        var server=h.getLevel().getServer();var before=GameManager.snapshot();var lanes=LaneSystem.snapshot();
        try{
            var legacy=before.deepCopy().getAsJsonObject();
            legacy.remove("guardianScore");legacy.remove("destructionScore");
            legacy.addProperty("xuanxiaoScore",123);legacy.addProperty("youmingScore",456);
            legacy.addProperty("winner","YOUMING");GameManager.restore(legacy,server);
            h.assertTrue(GameManager.get().getGuardianScore()==123&&GameManager.get().getDestructionScore()==456&&GameManager.get().getWinner()==Faction.DESTRUCTION,"Checkpoint score and winner aliases preserve values");
            var current=GameManager.snapshot().getAsJsonObject();
            h.assertTrue(current.has("guardianScore")&&!current.has("xuanxiaoScore")&&current.get("winner").getAsString().equals("destruction"),"Re-saved checkpoint writes only canonical names");
            var layout=JsonParser.parseString("{\"start\":-1,\"nextWave\":0,\"wave\":0,\"active\":false,\"destroyedTowers\":[\"xuanxiao/mid/1\",\"youming/mid/2\"],\"units\":[]}").getAsJsonObject();
            LaneSystem.restore(h.getLevel(),layout);
            String destroyed=LaneSystem.snapshot().getAsJsonArray("destroyedTowers").toString();
            h.assertTrue(destroyed.contains("guardian/mid/1")&&destroyed.contains("destruction/mid/2")&&!destroyed.contains("xuanxiao"),"Destroyed towers do not revive after team renaming");
        }finally{GameManager.restore(before,server);LaneSystem.restore(h.getLevel(),lanes);}
    }
    private static void verifyMapAliases(GameTestHelper h)throws Exception{
        var root=Files.createTempDirectory(h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),"faction-fixture-");
        var arena=Files.createDirectories(root.resolve("myriad_arenas/3v3"));
        for(String name:new String[]{"myriad_brush_zones.json","myriad_monster_spawns.json","myriad_vein_spawns.json"})Files.writeString(arena.resolve(name),"{}");
        Files.writeString(arena.resolve("myriad_minimap.json"),"{\"corners\":[0,0,0,0]}");
        for(String[] ids:new String[][]{{"guardian","destruction"},{"xuanxiao","youming"},{"guardian","youming"}}){
            String pos="\"x\":1,\"y\":65,\"z\":1",towers="",bases="";
            for(String id:ids){
                for(int tier=1;tier<=2;tier++)towers+=(towers.isEmpty()?"":",")+"{\"faction\":\""+id+"\",\"lane\":\"mid\",\"tier\":"+tier+","+pos+"}";
                bases+=(bases.isEmpty()?"":",")+"{\"faction\":\""+id+"\","+pos+"}";
            }
            Files.writeString(arena.resolve("myriad_lane_layout.json"),"{\"enabledLanes\":[\"mid\"],\"towerTiers\":[1,2],\"towers\":["+towers+"],\"fountains\":["+bases+"],\"crystals\":["+bases+"]}");
            h.assertTrue(MatchArena.singleLaneReady(root),"Current, legacy and mixed map names remain valid");
        }
    }
    private static void verifyScoreboard(GameTestHelper h){
        var server=h.getLevel().getServer();var list=server.getPlayerList().getPlayers();var scoreboard=server.getScoreboard();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled(),added=!list.contains(p);
        var existing=new java.util.HashSet<>(scoreboard.getTeamNames());
        try{
            if(added)list.add(p);ModState.setEnabled(true);TrainingMode.setEnabled(true);
            for(var faction:Faction.values()){
                PlayerDataProvider.get(p).setFaction(faction);MatchTeams.sync(server);
                var team=scoreboard.getPlayersTeam(p.getScoreboardName());
                h.assertTrue(team!=null&&team.getName().equals("myriad_team_"+faction.getName()),"Actual server scoreboard uses current team IDs");
                h.assertTrue(team.getColor().orElseThrow()==(faction==Faction.GUARDIAN?net.minecraft.world.scores.TeamColor.BLUE:net.minecraft.world.scores.TeamColor.RED),"Actual scoreboard colors are blue/red");
            }
        }finally{
            scoreboard.removePlayerFromTeam(p.getScoreboardName());if(added)list.remove(p);p.discard();
            for(var faction:Faction.values()){var id="myriad_team_"+faction.getName();if(!existing.contains(id)){var team=scoreboard.getPlayerTeam(id);if(team!=null)scoreboard.removePlayerTeam(team);}}
            ModState.setEnabled(enabled);TrainingMode.setEnabled(training);
        }
    }
}
