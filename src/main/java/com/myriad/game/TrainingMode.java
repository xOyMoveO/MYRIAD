package com.myriad.game;

import net.minecraft.server.level.ServerPlayer;

/** Resolve individual practice settings, or the administrator's idle-world map preview settings. */
public final class TrainingMode {
    // No-argument accessors cover the existing administrator sandbox only. Personal visitors
    // use their own options, and a running official match always uses official costs.
    private static boolean legacyAvailable() {
        var manager = GameManager.get();
        return enabled && (manager == null || manager.getPhase() == GameManager.Phase.WAITING);
    }

    public static boolean isEnabled(ServerPlayer player) {
        return TrainingGround.isTraining(player) || legacyAvailable();
    }

    public static boolean isNoCooldown(ServerPlayer player) {
        var personal = TrainingGround.options(player);
        return personal == null ? legacyAvailable() && noCooldown : personal.noCooldown;
    }

    public static boolean isNoMana(ServerPlayer player) {
        var personal = TrainingGround.options(player);
        return personal == null ? legacyAvailable() && noMana : personal.noMana;
    }

    public static boolean isNoGoldCost(ServerPlayer player) {
        var personal = TrainingGround.options(player);
        return personal == null ? legacyAvailable() && noGoldCost : personal.noGoldCost;
    }

    private static boolean enabled = false;
    private static boolean noCooldown = false;
    private static boolean noGoldCost = false;
    private static boolean noMana = false;

    private TrainingMode() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (!value) noCooldown = noGoldCost = noMana = false;
        enabled = value;
    }

    public static boolean isNoCooldown() {
        return noCooldown;
    }

    public static void setNoCooldown(boolean value) {
        noCooldown = value;
    }

    public static boolean isNoGoldCost() {
        return noGoldCost;
    }

    public static void setNoGoldCost(boolean value) {
        noGoldCost = value;
    }

    public static boolean isNoMana() {
        return noMana;
    }

    public static void setNoMana(boolean value) {
        noMana = value;
    }
}
