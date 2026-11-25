package com.myriad.client;

import com.myriad.MyriadClient;
import com.myriad.client.gui.DraftScreen;
import com.myriad.client.gui.CharacterGuideScreen;
import com.myriad.client.gui.InfoScreen;
import com.myriad.client.gui.LobbyScreen;
import com.myriad.client.gui.PickScreen;
import com.myriad.network.LobbyNetworking;
import com.myriad.network.Packets;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The client displays the server's most recent lobby snapshot and sends intents. */
public final class LobbyClient {
    private static LobbyNetworking.Snapshot current;
    private static boolean openDraftWhenReady;
    private static String lastConflict;
    private static int keyCheckTicks;

    private LobbyClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LobbyNetworking.Snapshot.TYPE, (snapshot, context) -> {
            boolean wasDraft = drafting();
            current = snapshot;
            var mc = context.client();
            var screen = mc.gui.screen();
            if (drafting() && !wasDraft) {
                openDraftWhenReady = true;
                openDraftIfReady(mc);
            } else if (wasDraft && !drafting() && (screen instanceof DraftScreen
                    || screen instanceof CharacterGuideScreen guide && guide.isQueueDraft())) {
                mc.setScreenAndShow(null);
            } else if (screen instanceof LobbyScreen lobby) {
                lobby.update();
            }
        });
    }

    public static LobbyNetworking.Snapshot snapshot() { return current; }
    public static boolean drafting() {
        return current != null && current.participant() && !current.picker().isEmpty();
    }

    public static int preparationSeconds() {
        return current != null && current.participant() ? current.preparationSeconds() : -1;
    }

    private static void openDraftIfReady(Minecraft mc) {
        if (openDraftWhenReady && drafting() && mc.player != null) {
            openDraftWhenReady = false;
            mc.setScreenAndShow(new DraftScreen());
        }
    }

    public static void tick(Minecraft mc) {
        openDraftIfReady(mc);
        if (mc.player == null || ++keyCheckTicks % 20 != 0) return;
        var conflict = conflict();
        String text = conflict == null ? null : conflict.getString();
        if (conflict != null && !text.equals(lastConflict)) mc.player.sendSystemMessage(conflict);
        lastConflict = text;
    }

    public static void clear() {
        current = null;
        openDraftWhenReady = false;
        lastConflict = null;
        keyCheckTicks = 0;
    }
    public static boolean reducedMotion() { return !HudLayoutConfig.menuAnimations(); }

    public static void send(String action, String value) {
        boolean refresh = action.equals("refresh");
        if (current == null && !refresh) return;
        long token = switch (action) {
            case "refresh" -> 0;
            case "lock", "preselect" -> current.pickToken();
            case "propose" -> current.proposalToken();
            default -> current.queueToken();
        };
        Packets.sendToServer(new LobbyNetworking.Action(action, value, token));
    }

    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (drafting()) {
            mc.setScreenAndShow(new DraftScreen());
            send("refresh", "");
            return;
        }
        var pick = ClientDataHolder.pickState();
        boolean hasLegacySeat = pick != null && pick.myFaction() != null && !pick.myFaction().isBlank();
        if (hasLegacySeat && pick.state() == 0) {
            mc.setScreenAndShow(new PickScreen());
            send("refresh", "");
            return;
        }
        if (preparationSeconds() >= 0 || hasLegacySeat && pick.countdownTick() >= 0) {
            mc.setScreenAndShow(new InfoScreen());
            return;
        }
        openLobby();
    }

    public static void openLobby() {
        Minecraft.getInstance().setScreenAndShow(new LobbyScreen());
        send("refresh", "");
    }

    public static Component conflict() {
        var options = Minecraft.getInstance().options;
        if (MyriadClient.KEY_LOBBY.isUnbound()) return null;
        for (var key : options.keyMappings) {
            if (key != MyriadClient.KEY_LOBBY && MyriadClient.KEY_LOBBY.same(key)
                    && !Arrays.asList(options.debugKeys).contains(key)) {
                return Component.translatable("lobby.myriad.key_conflict", Component.translatable(key.getName()));
            }
        }
        return null;
    }
}
