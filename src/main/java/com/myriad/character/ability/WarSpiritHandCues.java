package com.myriad.character.ability;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill.DashPose;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;

/** Owner-only hand cues on the existing cosmetic packet, never a world VFX or a combat request. */
public final class WarSpiritHandCues {
    public static final int WAR = 8, SPIRIT = 9;
    public static final int BASIC = 301, AIM = 302, STOP = 303, CAST = 304, FOLLOW = 305,
            HOLD = 306, MOVE = 307, RETURN = 308, FINISH = 309;

    private WarSpiritHandCues() {}

    public static boolean owns(AbilityVfxPayload p) {
        if (!p.personal() || p.kind() != AbilityVfxGeometry.CHARGE || p.anchor() != -1 || p.instance() != 0
                || p.angle() != (int) p.angle()) return false;
        int phase = (int) p.angle();
        if (p.theme() == WAR) return switch (p.variant()) {
            case 0 -> phase == BASIC;
            case 2, 5, 7 -> phase == CAST;
            case 3 -> phase == MOVE || phase == RETURN || phase == FINISH || phase == STOP;
            case 4 -> phase == HOLD || phase == FOLLOW;
            case 6 -> phase == AIM || phase == MOVE || phase == STOP || phase == FINISH;
            default -> false;
        };
        if (p.theme() == SPIRIT) return switch (p.variant()) {
            case 0 -> phase == BASIC;
            case 2, 7 -> phase == CAST;
            case 3 -> phase == CAST || phase == FOLLOW;
            case 4 -> phase == AIM || phase == CAST || phase == STOP;
            case 5 -> phase == AIM || phase == MOVE || phase == FINISH || phase == STOP;
            case 6 -> phase == AIM || phase == HOLD || phase == CAST || phase == STOP;
            default -> false;
        };
        return false;
    }

    public static void aim(Cast c, boolean active) { emit(c, active ? AIM : STOP, 0, 7); }
    public static void cast(Cast c) { emit(c, CAST, 0, 10); }
    public static void follow(Cast c) { emit(c, FOLLOW, 0, 8); }
    public static void brace(Cast c) { emit(c, HOLD, 0, 20); }
    public static void chorus(Cast c, boolean active) {
        if (!active || c.windupProgress() == 0 || c.now() % 2 == 0)
            emit(c, active ? HOLD : STOP, c.windupProgress(), 7);
    }
    public static void dash(Cast c, DashPose phase, boolean returning) {
        if (phase == DashPose.MOVING && c.now() % 2 != 0) return;
        emit(c, switch (phase) {
            case START, MOVING -> returning ? RETURN : MOVE;
            case FINISH -> FINISH;
            case CANCEL -> STOP;
        }, 0, 7);
    }
    public static void leap(Cast c, DashPose phase, double progress) {
        if (phase == DashPose.MOVING && c.now() % 2 != 0) return;
        emit(c, phase == DashPose.START || phase == DashPose.MOVING ? MOVE : STOP, progress, 7);
    }
    public static void landed(Cast c) { emit(c, FINISH, 0, 10); }

    public static void basic(ServerPlayer player, String role, int cadence) {
        int theme;
        var held = player.getMainHandItem();
        if ("chixiao".equals(role) && held.is(ModItems.CHIXIAO_BLADE.get())) theme = WAR;
        else if ("yunsheng".equals(role) && held.is(ModItems.YUNSHENG_BELL.get())) theme = SPIRIT;
        else return;
        var p = player.position();
        Packets.sendToPlayer(player, new AbilityVfxPayload(player.getId(), -1, theme, 0,
                AbilityVfxGeometry.CHARGE, p, p, Math.clamp(cadence, 1, 24), BASIC, 9, player.level().getGameTime(), true));
    }

    private static void emit(Cast c, int phase, double value, int life) {
        var p = c.player.position();
        AbilityVisuals.emit(c, AbilityVfxGeometry.CHARGE, p, p, value, phase, life, true);
    }
}
