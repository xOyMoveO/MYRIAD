package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** World-local checkpoint; player progression uses Fabric's persistent attachment. */
public final class MatchProgress {
    // Fabric may run the normal shutdown callbacks after SERVER_STARTED throws.
    // A server may replace its checkpoint only after it has finished loading it.
    private static MinecraftServer writableServer;
    public static final Gson JSON = new GsonBuilder().enableComplexMapKeySerialization().create();
    private static Path path(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("myriad_fabric_match.json"); }
    public static void save(MinecraftServer server) {
        if (server != writableServer || GameManager.get()==null || Boolean.getBoolean("myriad.combatTests")) return;
        try {
            JsonObject root = checkpoint();
            Path target=path(server), temp=Files.createTempFile(target.getParent(),"myriad-match-",".tmp");
            try {
                Files.writeString(temp,JSON.toJson(root));
                try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch(Exception e) { com.myriad.Myriad.LOGGER.error("Unable to save match checkpoint",e); }
    }

    static JsonObject checkpoint() {
        var root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("enabled", ModState.isEnabled());
        if (ModState.isEnabled()) root.addProperty("session", ModState.session());
        root.addProperty("arena", MatchArena.active().id);
        root.addProperty("training", TrainingMode.isEnabled());
        root.addProperty("noCooldown", TrainingMode.isNoCooldown());
        root.addProperty("noGoldCost", TrainingMode.isNoGoldCost());
        root.addProperty("noMana", TrainingMode.isNoMana());
        root.add("match", GameManager.snapshot());
        root.add("lanes", LaneSystem.snapshot());
        root.add("monsters", MonsterManager.snapshot());
        root.add("respawns", HeroRespawnSystem.snapshot());
        return root;
    }

    public static void load(MinecraftServer server) {
        writableServer = null;
        // A local/LAN match belongs to the host's current world session.
        // Never resume it when the host opens the world again.
        if (!server.isDedicatedServer()) {
            openHostSession(server);
            writableServer = server;
            return;
        }
        if (Boolean.getBoolean("myriad.combatTests")) return;
        if (Files.notExists(path(server))) {
            writableServer = server;
            return;
        }
        try {
            restoreCheckpoint(server, JSON.fromJson(Files.readString(path(server)), JsonObject.class));
            writableServer = server;
        } catch(Exception e) { throw new IllegalStateException("Failed to restore myriad_fabric_match.json; preserve the checkpoint before retrying",e); }
    }

    static void restoreCheckpoint(MinecraftServer server, JsonObject root) {
        if (root.has("arena") && !root.get("arena").getAsString().equals(MatchArena.active().id))
            throw new IllegalStateException("Checkpoint arena does not match available map configuration");
        GameManager.restore(root.get("match"), server);
        ModState.resume(root.get("enabled").getAsBoolean(), root.has("session") ? root.get("session").getAsString() : "");
        TrainingMode.setEnabled(root.has("training") && root.get("training").getAsBoolean());
        TrainingMode.setNoCooldown(root.has("noCooldown") && root.get("noCooldown").getAsBoolean());
        TrainingMode.setNoGoldCost(root.has("noGoldCost") && root.get("noGoldCost").getAsBoolean());
        TrainingMode.setNoMana(root.has("noMana") && root.get("noMana").getAsBoolean());
        if (ModState.isEnabled() && GameManager.get().getPhase() == GameManager.Phase.PLAYING) {
            LaneSystem.restore(server.overworld(), root.getAsJsonObject("lanes"));
            MonsterManager.restore(server.overworld(), root.getAsJsonObject("monsters"));
            HeroRespawnSystem.restore(root.get("respawns"));
        }
    }

    /** Host leaving a local/LAN world ends the session before the final checkpoint. */
    public static void onServerStopping(MinecraftServer server) {
        try {
            if (!server.isDedicatedServer()) endHostSession(server);
            save(server);
        } finally {
            if (writableServer == server) writableServer = null;
        }
    }

    /** Local/LAN world sessions intentionally do not resume a checkpoint. */
    static void openHostSession(MinecraftServer server) {
        ModState.setEnabled(false);
        TrainingMode.setEnabled(false);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoGoldCost(false);
        TrainingMode.setNoMana(false);
        HeroRespawnSystem.clear();
        com.myriad.player.PlayerDataProvider.clearModGeneratedState(server);
        ModEntityTracker.clearGenerated(server);
    }

    static void endHostSession(MinecraftServer server) {
        GameManager.create();
        GameManager.get().setServer(server);
        HeroRespawnSystem.clear();
        LaneSystem.tick(server.overworld());
    }
}
