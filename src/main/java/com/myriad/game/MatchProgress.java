package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** World-local checkpoint; player progression uses NeoForge's persistent attachment. */
public final class MatchProgress {
    public static final Gson JSON = new GsonBuilder().enableComplexMapKeySerialization().create();
    private static Path path(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("myriad_match.json"); }
    public static void save(MinecraftServer server) {
        if (GameManager.get()==null || Boolean.getBoolean("myriad.combatTests")) return;
        try {
            JsonObject root=new JsonObject();
            root.addProperty("version",1); root.addProperty("enabled",ModState.isEnabled());
            root.addProperty("arena", MatchArena.active().id);
            root.addProperty("training",TrainingMode.isEnabled());
            root.addProperty("noCooldown",TrainingMode.isNoCooldown());
            root.addProperty("noGoldCost",TrainingMode.isNoGoldCost());
            root.addProperty("noMana",TrainingMode.isNoMana());
            root.add("match",GameManager.snapshot());
            root.add("lanes",LaneSystem.snapshot()); root.add("monsters",MonsterManager.snapshot());
            root.add("respawns",HeroRespawnSystem.snapshot());
            Path target=path(server), temp=Files.createTempFile(target.getParent(),"myriad-match-",".tmp");
            try {
                Files.writeString(temp,JSON.toJson(root));
                try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch(Exception e) { com.myriad.Myriad.LOGGER.error("Unable to save match checkpoint",e); }
    }
    public static void load(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            ModState.setEnabled(false);
            TrainingMode.setEnabled(false);
            TrainingMode.setNoCooldown(false);
            TrainingMode.setNoGoldCost(false);
            TrainingMode.setNoMana(false);
            HeroRespawnSystem.clear();
            com.myriad.player.PlayerDataProvider.clearModGeneratedState(server);
            ModEntityTracker.clearGenerated(server);
            return;
        }
        if(!Files.isRegularFile(path(server)) || Boolean.getBoolean("myriad.combatTests"))return;
        try {
            JsonObject root=JSON.fromJson(Files.readString(path(server)),JsonObject.class);
            if (root.has("arena") && !root.get("arena").getAsString().equals(MatchArena.active().id))
                throw new IllegalStateException("Checkpoint arena does not match available map configuration");
            // A local/LAN match belongs to the host's current world session.
            // Never resume it when the host opens the world again, including
            // checkpoints written by older versions or before an abrupt exit.
            if (server.isDedicatedServer()) GameManager.restore(root.get("match"),server);
            ModState.setEnabled(root.get("enabled").getAsBoolean());
            TrainingMode.setEnabled(root.has("training") && root.get("training").getAsBoolean());
            TrainingMode.setNoCooldown(root.has("noCooldown") && root.get("noCooldown").getAsBoolean());
            TrainingMode.setNoGoldCost(root.has("noGoldCost") && root.get("noGoldCost").getAsBoolean());
            TrainingMode.setNoMana(root.has("noMana") && root.get("noMana").getAsBoolean());
            if(server.isDedicatedServer() && ModState.isEnabled() && GameManager.get().getPhase()==GameManager.Phase.PLAYING){
                LaneSystem.restore(server.overworld(),root.getAsJsonObject("lanes"));
                MonsterManager.restore(server.overworld(),root.getAsJsonObject("monsters"));
                HeroRespawnSystem.restore(root.get("respawns"));
            }
        } catch(Exception e) { throw new IllegalStateException("Failed to restore myriad_match.json; preserve the checkpoint before retrying",e); }
    }

    /** Host leaving a local/LAN world ends the session before the final checkpoint. */
    public static void onServerStopping(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            GameManager.create();
            GameManager.get().setServer(server);
            HeroRespawnSystem.clear();
            LaneSystem.tick(server.overworld());
        }
        save(server);
    }
}
