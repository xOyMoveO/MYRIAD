package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.game.VisionSystem;
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import com.myriad.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class LuoxiaBowPose {
    private LuoxiaBowPose() {}

    static void send(ServerPlayer p, int token, int skill, Phase phase, double work, double minimum,
                     double maximum, Vec3 aim) {
        var packet = new LuoxiaBowPosePayload(p.getUUID(), token, skill, phase, (float)work, (float)minimum,
                (float)maximum, (float)Math.toDegrees(Math.atan2(-aim.x, aim.z)),
                (float)Math.toDegrees(-Math.asin(Math.clamp(aim.y, -1, 1))), p.level().getGameTime());
        if (p.connection != null) Packets.sendToPlayer(p, packet);
        if (phase == Phase.EARLY) return;
        for (var viewer : p.level().players()) {
            if (viewer != p && viewer.distanceToSqr(p) <= 64*64 && VisionSystem.canSee(viewer, p))
                Packets.sendToPlayer(viewer, packet);
        }
    }

    static void skill(Cast cast, Phase phase, double fraction, Vec3 aim) {
        send(cast.player, cast.castId(), cast.skill.index(), phase, fraction, 1, 1, aim);
    }

    static void windup(Cast cast, boolean active, Vec3 locked) {
        skill(cast, !active ? Phase.CANCEL : locked == null ? Phase.WINDUP : Phase.LOCK,
                cast.windupProgress(), locked == null ? cast.player.getLookAngle() : locked);
    }
}
