package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.game.VisionSystem;
import com.myriad.registry.ModSounds.Night;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Server-confirmed one-shots. Resolve each endpoint before disclosing its coordinates. */
public final class NightAudio {
    private NightAudio() {}

    public static boolean ownsImpact(String source) {
        return "g1_yingxiao_n1".equals(source) || "g1_yingxiao_ma".equals(source) || "g1_yingxiao_ua".equals(source);
    }

    public static boolean canHear(ServerPlayer actor, ServerPlayer viewer, Vec3 point) {
        if (viewer.level() != actor.level() || viewer.position().distanceToSqr(point) > 16 * 16) return false;
        if (viewer == actor) return true;
        if (actor.isInvisibleTo(viewer) || !VisionSystem.canSee(viewer, actor)
                || !viewer.hasLineOfSight(actor) || !VisionSystem.canSeePosition(viewer, point)) return false;
        return clearLine(viewer, point);
    }

    private static boolean clearLine(ServerPlayer viewer, Vec3 point) {
        var ray = new ClipContext(viewer.getEyePosition(), point.add(0, .20, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer);
        return viewer.level().clip(ray).getType() == HitResult.Type.MISS;
    }

    static List<ServerPlayer> listeners(Cast cast, Vec3 point) {
        return cast.player.level().players().stream().filter(viewer -> canHear(cast.player, viewer, point)).toList();
    }

    static void impact(Cast cast, LivingEntity target, Night cue) {
        impact(cast, target, target.getBoundingBox().getCenter(), cue);
    }

    static void impact(Cast cast, LivingEntity target, Vec3 at, Night cue) {
        for (var viewer : listeners(cast, at)) {
            if (!target.isInvisibleTo(viewer) && VisionSystem.canSee(viewer, target) && clearLine(viewer, at)) {
                send(viewer, at, cue);
            } else if (viewer == cast.player) {
                personal(cast, cue);
            }
        }
    }

    static void personal(Cast cast, Night cue) {
        send(cast.player, cast.player.getEyePosition(), cue);
    }

    static void play(Cast cast, Vec3 point, Night cue) {
        send(listeners(cast, point), point, cue);
    }

    static void send(List<ServerPlayer> listeners, Vec3 point, Night cue) {
        for (var viewer : listeners) send(viewer, point, cue);
    }

    private static void send(ServerPlayer viewer, Vec3 point, Night cue) {
        if (viewer.connection == null) return;
        viewer.connection.send(new ClientboundSoundPacket(Holder.direct(cue.sound), SoundSource.PLAYERS,
                point.x, point.y, point.z, cue.volume, 1, viewer.getRandom().nextLong()));
    }
}
