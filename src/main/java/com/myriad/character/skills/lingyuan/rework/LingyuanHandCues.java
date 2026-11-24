package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill.DashPose;
import com.myriad.character.ability.AbilityVisuals;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;

import static com.myriad.character.ability.AbilityVfxGeometry.CHARGE;

/** Private presentation signals on the existing packet; never a world-effect recipe. */
public final class LingyuanHandCues {
    public static final int THEME = 5;
    public static final int BASIC = 281, AIM = 282, CAST = 283, STOP = 284,
            DASH = 285, FINISH = 286, GATHER = 287, CHOP = 288, IMPACT = 289;

    private LingyuanHandCues() {}

    public static boolean owns(AbilityVfxPayload p) {
        if (p.theme() != THEME || p.kind() != CHARGE || !p.personal() || p.anchor() != -1
                || p.angle() != (int) p.angle()) return false;
        int phase = (int) p.angle();
        return switch (p.variant()) {
            case 0 -> phase == BASIC;
            case 2 -> phase == DASH || phase == FINISH || phase == STOP;
            case 3, 4 -> phase == CAST;
            case 5, 6 -> phase == AIM || phase == CAST || phase == STOP;
            case 7 -> phase == GATHER || phase == CHOP || phase == IMPACT || phase == STOP;
            default -> false;
        };
    }

    static void aim(Cast c, boolean active) { emit(c, active ? AIM : STOP, 0, 7); }
    static void release(Cast c) { emit(c, CAST, 0, 12); }
    static void volley(Cast c, int shot) { emit(c, CAST, shot, 7); }
    static void dash(Cast c, DashPose phase) {
        if (phase == DashPose.MOVING && c.now() % 2 != 0) return;
        emit(c, switch (phase) {
            case START, MOVING -> DASH;
            case FINISH -> FINISH;
            case CANCEL -> STOP;
        }, 0, 7);
    }

    static void heaven(Cast c, boolean active) {
        if (!active) { emit(c, STOP, 0, 7); return; }
        boolean chopping = c.memory("hand_phase").count == 1;
        if (chopping || c.windupProgress() == 0 || c.now() % 2 == 0)
            emit(c, chopping ? CHOP : GATHER, c.windupProgress(), 6);
    }
    static void impact(Cast c) { emit(c, IMPACT, 0, 10); }

    public static void basic(ServerPlayer p, String role) {
        if (!"lingyuan".equals(role) || !p.getMainHandItem().is(ModItems.LINGYUAN_SWORD.get())) return;
        var pos = p.position();
        float cadence = (float) Math.clamp(p.getCurrentItemAttackStrengthDelay(), 1, 24);
        Packets.sendToPlayer(p, new AbilityVfxPayload(p.getId(), -1, THEME, 0, CHARGE, pos, pos,
                cadence, BASIC, 9, p.level().getGameTime(), true));
    }

    private static void emit(Cast c, int phase, double value, int life) {
        var p = c.player.position();
        AbilityVisuals.emit(c, CHARGE, p, p, value, phase, life, true);
    }
}
