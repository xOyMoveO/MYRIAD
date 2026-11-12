package com.myriad.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/** World-local reset generation also invalidates attachments belonging to offline players. */
public final class FullGameReset {
    private static final String KEY = "myriad_full_reset_generation";
    private FullGameReset() { }
    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("myriad_reset_generation.txt");
    }
    public static void execute(MinecraftServer server) throws IOException {
        String generation = UUID.randomUUID().toString();
        Path target = path(server);
        Path temp = Files.createTempFile(target.getParent(), "myriad-reset-", ".tmp");
        try {
            Files.writeString(temp, generation);
            try { Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
        TrainingMode.setEnabled(false);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoGoldCost(false);
        TrainingMode.setNoMana(false);
        GameManager.get().resetToLobby(server, true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getPersistentData().putString(KEY, generation);
        }
        server.getPlayerList().saveAll();
        MatchProgress.save(server);
    }
    public static boolean stale(String generation, String applied) {
        return !generation.isBlank() && !generation.equals(applied);
    }
    public static void onLogin(ServerPlayer player) {
        Path file = path(player.level().getServer());
        if (!Files.isRegularFile(file)) return;
        try {
            String generation = Files.readString(file).trim();
            if (!stale(generation, player.getPersistentData().getStringOr(KEY, ""))) return;
            if (ModState.isEnabled()) {
                PlayerDataProvider.resetForHostSession(player);
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                LobbySystem.teleportToLobby(player);
            } else {
                // Never erase restored vanilla inventory while the mod is off.
                player.setData(PlayerDataProvider.PLAYER_DATA, new PlayerData());
            }
            player.getPersistentData().putString(KEY, generation);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read world reset generation", e);
        }
    }
}
