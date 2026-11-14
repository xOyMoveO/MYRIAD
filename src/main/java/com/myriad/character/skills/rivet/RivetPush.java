package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.KnockbackBuffer;
import com.myriad.combat.SkillTargets;
import com.myriad.network.AbilityMotionPayload;
import com.myriad.network.NetworkSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** A short horizontal impulse with actual collision-shape contact and vanilla-sized step clearance. */
public final class RivetPush {
    private record Root(AbilityRuntime.Cast cast, LivingEntity target, long until, String key) { }
    private static final List<Root> ROOTS = new ArrayList<>();
    private record Push(AbilityRuntime.Cast cast, LivingEntity target, long token) { }
    private record LastWall(net.minecraft.world.level.Level level, long time) { }
    private record Step(Vec3 velocity, boolean wall, boolean valid) { }
    private static final Map<UUID, Push> PUSHES = new HashMap<>();
    private static final Map<UUID, LastWall> LAST_WALL = new HashMap<>();
    private RivetPush() { }

    static void start(AbilityRuntime.Cast c, LivingEntity target, Vec3 direction, double distance,
                      float wallDamage, int rootTicks, int anchorRootTicks, boolean hammer) {
        if (!SkillTargets.enemy(c.player, target) || target.level() != c.player.level()) return;
        Vec3 flat = direction.multiply(1, 0, 1).normalize();
        if (flat.lengthSqr() < .01) return;
        double reduction = EmergencyStrut.knockbackMultiplier(target);
        double budget = distance * reduction;
        if (budget <= .01) return;
        // Rivet pushes intentionally replace another Rivet push, bypassing the ordinary 20-tick immunity.
        // The independent impulse token still interrupts channels and locally owned movement.
        long token = KnockbackBuffer.beginIndependent(target);
        var push = new Push(c, target, token);
        PUSHES.put(target.getUUID(), push);
        Vec3 origin = target.position();
        long start = c.now();
        var progress = new ActionTime.Progress(start);
        final boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (PUSHES.remove(target.getUUID(), push)) stop(target);
        };
        java.util.function.Predicate<Integer> update = age -> {
            if (finished[0] || PUSHES.get(target.getUUID()) != push) return false;
            if (!target.isAlive() || target.isRemoved() || target.level() != c.player.level()
                    || !SkillTargets.enemy(c.player, target) || KnockbackBuffer.impulseToken(target) != token) {
                finish.run(); return false;
            }
            double traveled = Math.max(0, target.position().subtract(origin).multiply(1, 0, 1).dot(flat));
            double left = budget - traveled;
            // The unfinished push uses local action time; its collision-reward window remains real time.
            if (age >= 2400) { finish.run(); return false; }
            var monster = com.myriad.game.MonsterManager.typeOf(target);
            double rate = monster != null && monster.isLargeBoss() ? 1
                    : Math.min(ActionTime.multiplier(target.getUUID(), target.level().getGameTime()),
                            target instanceof ServerPlayer ? 1 : com.myriad.character.skills.keheng.KehengTimeFields
                                    .managedNpcRate(target, target.level().getGameTime()));
            if (progress.advance(c.now(), rate, 10)) { finish.run(); return false; }
            boolean completed = left <= .015;
            // A final contact probe awards a wall reached on the exact distance boundary,
            // without moving the body beyond its two-block budget.
            double stepLength = completed ? .02 : Math.min(left, budget / 6 * rate);
            Step step = sweep(target, flat.scale(stepLength));
            if (!step.valid()) { finish.run(); return false; }
            boolean touchingSide = Math.abs(flat.x * stepLength) > .005 && Math.abs(step.velocity().x) < .005
                    || Math.abs(flat.z * stepLength) > .005 && Math.abs(step.velocity().z) < .005;
            // A clipped partial step reaches the surface first. A side blocked at zero is real contact,
            // including diagonal walls where the other axis could otherwise keep sliding along the wall.
            if (step.wall() && touchingSide) {
                if (c.now() - start <= 8) {
                    Vec3 contact = wallPoint(target, flat);
                    boolean anchored = TacticalAnchor.consumeStrike(c.player, contact);
                    c.damage(target, wallDamage, DamageCalc.Kind.PHYSICAL, false);
                    LastWall last = LAST_WALL.get(target.getUUID());
                    int duration = anchored ? anchorRootTicks : rootTicks;
                    if (last != null && last.level() == target.level() && c.now() - last.time() < 60) duration = Math.max(1, duration / 2);
                    LAST_WALL.put(target.getUUID(), new LastWall(target.level(), c.now()));
                    root(c, target, duration);
                    if (hammer) BreachRhythm.wallHit(c.player);
                    RivetVfx.wall(c, contact, anchored);
                }
                finish.run(); return false;
            }
            if (completed) { finish.run(); return false; }
            Vec3 velocity = step.velocity();
            if (Math.abs(velocity.y) < .00001) velocity = new Vec3(velocity.x, target.getDeltaMovement().y, velocity.z);
            send(target, velocity, target.position().add(velocity));
            return true;
        };
        // Check and own the first step before any speed reaches a client.
        if (!update.test(0)) return;
        c.whileActive(2401, age -> {
            if (c.now() <= start) return true;
            return update.test(age);
        }, finish);
    }

    private static Vec3 wallPoint(LivingEntity target, Vec3 direction) {
        Vec3 center = target.getBoundingBox().getCenter();
        double edge = Math.min(Math.abs(direction.x) < 1E-6 ? Double.POSITIVE_INFINITY : target.getBbWidth() * .5 / Math.abs(direction.x),
                Math.abs(direction.z) < 1E-6 ? Double.POSITIVE_INFINITY : target.getBbWidth() * .5 / Math.abs(direction.z));
        return center.add(direction.scale(edge + .015));
    }

    private static Step sweep(LivingEntity target, Vec3 desired) {
        Vec3 endpoint = target.position().add(desired);
        if (!target.level().hasChunkAt(BlockPos.containing(endpoint))
                || !target.level().getWorldBorder().isWithinBounds(BlockPos.containing(endpoint))) return new Step(Vec3.ZERO, false, false);
        var box = target.getBoundingBox();
        Vec3 clipped = Entity.collideBoundingBox(target, desired, box, target.level(), List.of());
        boolean blocked = clipped.horizontalDistanceSqr() + 1E-7 < desired.horizontalDistanceSqr();
        if (!blocked) return new Step(clipped, false, true);
        boolean grounded = target.onGround() || !target.level().noCollision(target, box.move(0, -.04, 0));
        if (grounded && target.maxUpStep() > 0) {
            var heights = new TreeSet<Double>();
            var region = box.expandTowards(desired).expandTowards(0, target.maxUpStep() + .001, 0);
            for (var shape : target.level().getBlockCollisions(target, region)) for (var obstacle : shape.toAabbs()) {
                double rise = obstacle.maxY - box.minY;
                if (rise > .001 && rise <= target.maxUpStep() + .001) heights.add(rise);
            }
            for (double rise : heights) {
                Vec3 up = Entity.collideBoundingBox(target, new Vec3(0, rise, 0), box, target.level(), List.of());
                if (up.y + 1E-5 < rise) continue;
                Vec3 across = Entity.collideBoundingBox(target, desired, box.move(up), target.level(), List.of());
                if (across.horizontalDistanceSqr() + 1E-7 < desired.horizontalDistanceSqr()) continue;
                Vec3 down = Entity.collideBoundingBox(target, new Vec3(0, -rise, 0), box.move(up).move(across), target.level(), List.of());
                return new Step(new Vec3(across.x, rise + down.y, across.z), false, true);
            }
        }
        return new Step(clipped, true, true);
    }

    private static void send(LivingEntity target, Vec3 velocity, Vec3 destination) {
        if (target instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi()) {
            // NoAI camps, lane units and training targets do not run LivingEntity.travel.
            // Move their body through the ordinary collision resolver exactly once here.
            target.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            target.setDeltaMovement(Vec3.ZERO);
        } else target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.resetFallDistance();
        if (target instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player,
                new AbilityMotionPayload((float) velocity.x, (float) velocity.y, (float) velocity.z,
                        destination.x, destination.y, destination.z, 2));
    }
    private static void stop(LivingEntity target) {
        Vec3 v = new Vec3(0, target.getDeltaMovement().y, 0);
        target.setDeltaMovement(v);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player,
                new NetworkSetup.DashPayload(0, (float) v.y, 0, -1));
    }
    private static void root(AbilityRuntime.Cast c, LivingEntity target, int ticks) {
        double tenacity = target instanceof ServerPlayer
                ? Math.clamp(target.getAttributeValue(com.myriad.registry.ModAttributes.TENACITY), 0, 60) / 100 : 0;
        int duration = Math.max(1, (int) Math.ceil(ticks * (1 - tenacity)));
        String key = "g1_root/" + c.skill.getId() + "/" + c.player.getUUID();
        ROOTS.removeIf(root -> root.target() == target && root.key().equals(key));
        ROOTS.add(new Root(c, target, c.now() + duration, key));
        c.slow(target, 1, ticks);
        if (target instanceof ServerPlayer player) {
            com.myriad.player.PlayerDataProvider.get(player).setBuff(key, duration);
            com.myriad.player.PlayerDataProvider.syncToClient(player);
        }
    }
    /** Authoritative source-aggregated remaining root; also used by managed NPC navigation. */
    public static int rootRemaining(LivingEntity target) {
        return ROOTS.stream().filter(root -> root.target() == target)
                .mapToInt(root -> (int) Math.max(0, root.until() - target.level().getGameTime())).max().orElse(0);
    }
    /** Managed NoAI navigation respects forced motion and roots while attacks keep their own clock. */
    public static boolean autonomousMovementBlocked(LivingEntity target) {
        ROOTS.removeIf(root -> !root.target().isAlive() || root.target().isRemoved()
                || root.target().level().getGameTime() >= root.until());
        return PUSHES.containsKey(target.getUUID()) || rootRemaining(target) > 0;
    }
    private static void removeRoot(Root root) {
        if (root.target() instanceof ServerPlayer target) {
            com.myriad.player.PlayerDataProvider.get(target).setBuff(root.key(), 0);
            com.myriad.player.PlayerDataProvider.syncToClient(target);
        }
        var speed = root.target().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("myriad",
                "ability_slow_" + root.cast().skill.getId() + "_" + root.cast().player.getUUID()));
        ROOTS.remove(root);
    }
    /** Cleanse received controls without touching this actor's own anchor, zones or protection. */
    public static void cleanse(LivingEntity target) {
        var push = PUSHES.remove(target.getUUID());
        if (push != null) stop(target);
        for (var root : new ArrayList<>(ROOTS)) if (root.target() == target) removeRoot(root);
    }
    public static void clearAll() {
        for (var push : new ArrayList<>(PUSHES.values())) stop(push.target());
        PUSHES.clear();
        for (var root : new ArrayList<>(ROOTS)) removeRoot(root);
        LAST_WALL.clear();
    }
    static void clear(ServerPlayer owner) {
        for (var push : new ArrayList<>(PUSHES.values())) if (push.cast().player == owner || push.target() == owner) {
            if (PUSHES.remove(push.target().getUUID(), push)) stop(push.target());
        }
        for (var root : new ArrayList<>(ROOTS)) if (root.cast().player == owner || root.target() == owner) {
            removeRoot(root);
        }
        LAST_WALL.entrySet().removeIf(entry -> entry.getKey().equals(owner.getUUID())
                || owner.level().getGameTime() - entry.getValue().time() >= 60);
    }
}
