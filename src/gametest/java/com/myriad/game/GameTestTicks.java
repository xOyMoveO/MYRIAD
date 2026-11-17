package com.myriad.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Removable observations of the actual Fabric end-server-tick event. */
final class GameTestTicks {
    private static final List<Consumer<MinecraftServer>> LISTENERS = new ArrayList<>();
    private GameTestTicks() { }
    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var listener : List.copyOf(LISTENERS)) listener.accept(server);
        });
    }
    static void addListener(Consumer<MinecraftServer> listener) { LISTENERS.add(listener); }
    static void removeListener(Consumer<MinecraftServer> listener) { LISTENERS.remove(listener); }
}
