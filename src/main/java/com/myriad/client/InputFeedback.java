package com.myriad.client;

import com.myriad.combat.InputFailure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class InputFeedback {
    private static LocalPlayer owner;
    private static InputFailure.Window window;
    private InputFeedback() {}

    private static boolean allow(InputFailure reason) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (player != owner) {
            owner = player;
            window = new InputFailure.Window();
        }
        return window.allow(reason, player.tickCount);
    }

    public static void show(InputFailure reason) {
        var player = Minecraft.getInstance().player;
        // Both local and network action bars enter the same ALLOW_GAME listener.
        if (player != null) player.sendOverlayMessage(reason.text());
    }

    public static boolean allowMessage(Component message, boolean overlay) {
        if (!overlay || !(message.getContents() instanceof TranslatableContents text)) return true;
        var reason = InputFailure.forKey(text.getKey());
        return reason == null || allow(reason);
    }

    public static void clear() { owner = null; window = null; }
}
