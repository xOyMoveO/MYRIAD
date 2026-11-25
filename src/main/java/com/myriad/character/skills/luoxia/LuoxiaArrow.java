package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityTargeting;
import com.myriad.character.ability.ActionTime;
import com.myriad.combat.SkillTargets;
import com.myriad.combat.WindWallSystem;
import com.myriad.game.DefenseTowerEntity;
import com.myriad.game.VisionSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class LuoxiaArrow {
    // First four wire indices match the original light, mark, heavy and lance visuals.
    enum Shape {
        BASIC("basic", .98, false),
        MARK("dusk_mark", 1.22, false),
        LAST_SUN("last_sun", 1.54, true),
        LANCE("sunset_line", 1.94, true),
        QUICK("quick", .83, false),
        FULL_DRAW("full_draw", 1.34, true),
        BURST("feather_burst", 1.06, false),
        VOLLEY("thousand_feathers", 1.18, false);

        final String model;
        final double length;
        final boolean heavy;
        private static final Shape[] WIRE_ORDER = values();

        Shape(String model, double length, boolean heavy) {
            this.model = model;
            this.length = length;
            this.heavy = heavy;
        }

        static Shape fromWire(int id) {
            return id >= 0 && id < WIRE_ORDER.length ? WIRE_ORDER[id] : BASIC;
        }
    }
    private record Contact(LivingEntity target, Vec3 point) {}
    private LuoxiaArrow() {}

    static boolean isSkillArrow(String id) {
        return "luoxia_feather_burst".equals(id) || "luoxia_last_sun".equals(id)
                || "luoxia_thousand_feathers".equals(id) || "luoxia_sunset_line".equals(id);
    }

    static Vec3 limitTurn(Vec3 origin, Vec3 raw, double limit) {
        Vec3 to = raw.normalize();
        double cos = Math.clamp(origin.dot(to), -1, 1);
        if (cos >= Math.cos(limit)) return to;
        Vec3 tangent = to.subtract(origin.scale(cos)).normalize();
        return tangent.lengthSqr() < .1 ? origin : origin.scale(Math.cos(limit)).add(tangent.scale(Math.sin(limit)));
    }

    static Vec3 muzzle(Cast c) { return c.player.getEyePosition().add(0, -.12, 0); }
    static Vec3 clip(Cast c, Vec3 from, Vec3 desired) {
        var hit = c.player.level().clip(new ClipContext(from, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
        return hit.getType() == HitResult.Type.MISS ? desired : hit.getLocation();
    }

    /** Radius checks and closest contact are computed on the wall-truncated segment. */
    static void launch(Cast c, Vec3 aim, double range, double velocity, Shape shape, boolean basic,
                       Predicate<LivingEntity> impact) {
        UUID clock = UUID.randomUUID();
        int id = c.newVisualInstance();
        Vec3 direction = aim.normalize();
        double radius = shape == Shape.LANCE ? .19 : .09;

        class Flight {
            Vec3 pos = muzzle(c);
            double remaining = range;
            long last = c.now();
            final Set<UUID> struck = new HashSet<>();

            void finish() {
                ActionTime.clearTarget(clock);
                LuoxiaVfx.finish(c, id);
            }

            boolean step(int age) {
                if (age >= 120) { finish(); return false; }
                if (c.now() <= last) return true;
                last = c.now();
                double distance = Math.min(remaining, velocity * AbilityRuntime.projectileRate(c.player, clock, pos, c.now()));
                Vec3 raw = pos.add(direction.scale(distance));
                if (!c.player.level().hasChunkAt(BlockPos.containing(raw))
                        || !c.player.level().getWorldBorder().isWithinBounds(BlockPos.containing(raw))) {
                    finish();
                    return false;
                }
                Vec3 end = clip(c, pos, raw);
                Vec3 wall = WindWallSystem.interceptProjectile(c.player, pos, end);
                if (wall != null) end = wall;
                var hits = new ArrayList<Contact>();
                for (var target : c.player.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, end).inflate(radius),
                        t -> !t.isSpectator() && !(t instanceof Player p && p.isCreative()) && !struck.contains(t.getUUID())
                                && (SkillTargets.enemy(c.player, t) || basic && t instanceof DefenseTowerEntity
                                    && t.isAlive() && !SkillTargets.ally(c.player, t)))) {
                    var bounds = target.getBoundingBox();
                    if (bounds.contains(pos)) hits.add(new Contact(target, pos));
                    else AbilityTargeting.unobstructedHit(c.player, bounds, pos, end, radius)
                            .ifPresent(contact -> hits.add(new Contact(target, contact)));
                }
                hits.sort(Comparator.comparingDouble(hit -> hit.point().distanceToSqr(pos)));
                for (Contact hit : hits) {
                    struck.add(hit.target.getUUID());
                    // A hidden building is allowed to block, but a basic hit cannot damage it through fog.
                    boolean stop = basic && hit.target instanceof DefenseTowerEntity && !VisionSystem.canSee(c.player, hit.target)
                            || impact.test(hit.target);
                    LuoxiaVfx.impact(c, hit.point, direction, shape.heavy);
                    if (stop) { LuoxiaVfx.wake(c, pos, hit.point, shape); finish(); return false; }
                }
                LuoxiaVfx.wake(c, pos, end, shape);
                remaining -= pos.distanceTo(end);
                if (end.distanceToSqr(raw) > 1E-8 || remaining <= 1E-6) { finish(); return false; }
                LuoxiaVfx.flight(c, pos, end, shape, id);
                pos = end;
                return true;
            }
        }
        var flight = new Flight();
        LuoxiaVfx.flight(c, flight.pos, flight.pos.add(direction.scale(.12)), shape, id);
        c.whileActive(120, flight::step, flight::finish);
    }
}
