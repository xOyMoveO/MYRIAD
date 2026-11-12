package com.myriad.game;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Optional map-owned lobby. A playing match always preserves reconnect position. */
public final class LobbySystem {
    private LobbySystem() { }
    public static void teleportToLobby(ServerPlayer p) {
        var server = p.level().getServer();
        GuideBook.give(p);
        var path = server.getWorldPath(LevelResource.ROOT).resolve("myriad_lobby.json");
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            var o = JsonParser.parseReader(reader).getAsJsonObject();
            double x=o.get("x").getAsDouble(), y=o.get("y").getAsDouble(), z=o.get("z").getAsDouble();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x)>29000000 || Math.abs(z)>29000000 || y< -63 || y>318) return;
            p.setCamera(p);
            p.teleportTo(server.overworld(), x,y,z, java.util.Set.of(), o.has("yaw") ? o.get("yaw").getAsFloat() : 0F, 0F, true);
            p.setYRot(o.has("yaw") ? o.get("yaw").getAsFloat() : 0F);
            p.setXRot(0F);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.fallDistance=0;
        } catch (Exception e) {
            com.myriad.Myriad.LOGGER.warn("Unable to read map lobby {}",path,e);
        }
    }
}
