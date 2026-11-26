package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class LuoxiaVfx {
    private static final int THEME = 14;
    private LuoxiaVfx() {}

    static void sound(ServerPlayer p, SoundEvent event, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), event, SoundSource.PLAYERS, volume, pitch);
    }
    static void shot(Cast c, boolean heavy, float pitch) {
        Vec3 aim = c.player.getLookAngle();
        shot(c, heavy, pitch, LuoxiaArrow.muzzle(c).add(aim.scale(.58)), aim, 0);
    }
    static void shot(Cast c, boolean heavy, float pitch, Vec3 direction, int round) {
        shot(c, heavy, pitch, LuoxiaArrow.muzzle(c), direction, round);
    }
    private static void shot(Cast c, boolean heavy, float pitch, Vec3 at, Vec3 direction, int round) {
        send(c, -1, CHARGE, at, at.add(direction), heavy ? .28f : .16f, round == 0 ? 0 : 2 + round, 5, 0);
        sound(c.player, SoundEvents.ARROW_SHOOT, heavy ? .95f : .65f, pitch);
        if (heavy) {
            sound(c.player, SoundEvents.PLAYER_ATTACK_SWEEP, .42f, .85f);
            sound(c.player, SoundEvents.AMETHYST_BLOCK_HIT, .23f, .72f);
        }
    }
    static void charge(Cast c, boolean heavy) {
        var d = c.player.getLookAngle();
        var at = LuoxiaArrow.muzzle(c).add(d.scale(.83));
        send(c, -1, CHARGE, at, at.add(d), heavy ? .22f : .13f, 1, 3, 0);
    }
    static void warning(Cast c, Vec3 direction, double range) {
        var at = LuoxiaArrow.muzzle(c);
        send(c, -1, BEAM, at, LuoxiaArrow.clip(c, at, at.add(direction.scale(range))), .05f, 1, 2, 0);
    }
    static void step(Cast c, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < .005) return;
        var d = to.subtract(from).normalize();
        var side = new Vec3(-d.z, 0, d.x).scale(.33);
        for (int i : new int[]{-1, 1}) send(c, -1, TRAIL, from.add(side.scale(i)).add(0,.13,0),
                to.add(side.scale(i)).add(0,.13,0), .15f, 2, 7, 0);
    }
    static void mark(Cast c, LivingEntity target) {
        var from = target.position().add(0, target.getBbHeight()*.72, 0);
        send(c, target.getId(), SHIELD, from, from.add(0,1,0), .24f, 0, 5, 0);
    }
    static void flight(Cast c, Vec3 tip, Vec3 along, LuoxiaArrow.Shape shape, int id) {
        send(c, -1, FLIGHT, tip, along, .20f, shape.ordinal(), 3, id);
    }
    static void wake(Cast c, Vec3 from, Vec3 to, LuoxiaArrow.Shape shape, int lane) {
        if (from.distanceToSqr(to) < 1E-6) return;
        int detail = shape == LuoxiaArrow.Shape.VOLLEY && lane == 0 ? 3 : (shape.heavy ? 1 : 0);
        send(c, -1, TRAIL, from, to, shape.heavy ? .11f : .06f, detail,
                shape.heavy ? 7 : 4, 0);
    }
    static void impact(Cast c, Vec3 at, Vec3 direction, boolean heavy) {
        impact(c, at, direction, heavy, false);
    }
    static void impact(Cast c, Vec3 at, Vec3 direction, boolean heavy, boolean graze) {
        send(c, -1, BURST, at, at.add(direction), heavy ? .66f : .32f, graze ? 1 : 0, graze ? 4 : 8, 0);
    }
    static void finish(Cast c, int id) { AbilityVisuals.finishFlight(c, id); }

    private static void send(Cast c, int anchor, int kind, Vec3 from, Vec3 to, float radius, int detail, int life, int id) {
        var packet = new AbilityVfxPayload(c.player.getId(), anchor, THEME, c.skill.index(), kind,
                from, to, radius, detail, life, c.now(), false, id);
        if (!packet.valid()) return;
        for (var viewer : c.player.level().players()) {
            if (viewer.distanceToSqr(from) > 64*64 && viewer.distanceToSqr(to) > 64*64) continue;
            if (anchor >= 0) {
                var marked = c.player.level().getEntity(anchor);
                if (marked == null || !VisionSystem.canSee(viewer, marked) || !viewer.hasLineOfSight(marked)) continue;
            }
            if (viewer == c.player || VisionSystem.canSeePosition(viewer, from)) Packets.sendToPlayer(viewer, packet);
        }
    }
}
