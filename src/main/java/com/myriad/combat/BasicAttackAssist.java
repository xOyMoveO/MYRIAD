package com.myriad.combat;

import com.myriad.character.CharacterType;
import com.myriad.game.DefenseTowerEntity;
import com.myriad.game.JiarenManager;
import com.myriad.game.LaneMinionEntity;
import com.myriad.game.LaneSystem;
import com.myriad.game.MonsterManager;
import com.myriad.game.TrainingGround;
import com.myriad.game.VisionSystem;
import com.myriad.player.PlayerDataProvider;
import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A temporary hitbox for basic attacks. All entity and projectile collision shapes stay unchanged. */
public final class BasicAttackAssist {
    public static final double MAX_PADDING = 0.30;
    public static final double DEFAULT_FRACTION = 0.15;
    private static final double SERVER_FRACTION = "0".equals(System.getProperty("myriad.basicAttackAssistFraction"))
            ? 0 : DEFAULT_FRACTION;

    private BasicAttackAssist() {}

    public record Contact(Vec3 point, Vec3 originalPoint, double offsetSquared, double distanceSquared) {}

    public static boolean enabled() {
        return SERVER_FRACTION > 0;
    }

    public static boolean eligibleTarget(LivingEntity target) {
        return target.isAlive() && !(target instanceof DefenseTowerEntity) && !target.isSpectator()
                && !(target instanceof Player player && player.isCreative());
    }

    public static boolean canAssist(ServerPlayer attacker, LivingEntity target) {
        return target != attacker && eligibleTarget(target) && SkillTargets.hostile(attacker, target)
                && !target.isInvisible() && !target.isInvisibleTo(attacker) && VisionSystem.canSee(attacker, target)
                && (target instanceof ServerPlayer hero && TrainingGround.canFight(hero)
                        && CharacterType.byId(PlayerDataProvider.get(hero).getCharacterId()) != null
                        || target instanceof LaneMinionEntity && LaneSystem.factionOf(target) != null
                        || MonsterManager.isMonster(target)
                        || JiarenManager.isJiaren(target.getUUID()));
    }

    public static Contact contact(Vec3 start, Vec3 end, boolean blockedAtEnd, double maxDistance,
            AABB original, double existingRadius) {
        return contact(start, end, blockedAtEnd, maxDistance, original, existingRadius, SERVER_FRACTION);
    }

    /** Call after checking the native hit. The caller verifies the path to originalPoint. */
    public static Contact contact(Vec3 start, Vec3 end, boolean blockedAtEnd, double maxDistance,
            AABB original, double existingRadius, double fraction) {
        double x = Math.min(original.getXsize() * fraction, MAX_PADDING);
        double y = Math.min(original.getYsize() * fraction, MAX_PADDING);
        double z = Math.min(original.getZsize() * fraction, MAX_PADDING);
        if (x <= existingRadius && y <= existingRadius && z <= existingRadius) return null;

        AABB expanded = original.inflate(Math.max(x, existingRadius), Math.max(y, existingRadius),
                Math.max(z, existingRadius));
        Vec3 hit = expanded.contains(start) ? start : expanded.clip(start, end).orElse(null);
        if (hit == null) return null;
        double distanceSquared = start.distanceToSqr(hit);
        if (blockedAtEnd && distanceSquared >= start.distanceToSqr(end)) return null;

        Vec3 nearest = nearestOnSegment(start, end, original);
        Vec3 originalPoint = closestInBox(nearest, original);
        if (start.distanceToSqr(originalPoint) > maxDistance * maxDistance) return null;
        return new Contact(hit, originalPoint, nearest.distanceToSqr(originalPoint), distanceSquared);
    }

    public static boolean precedes(Contact candidate, int candidateId, Contact incumbent, int incumbentId) {
        if (incumbent == null) return true;
        int offset = Double.compare(candidate.offsetSquared, incumbent.offsetSquared);
        if (offset != 0) return offset < 0;
        int distance = Double.compare(candidate.distanceSquared, incumbent.distanceSquared);
        return distance < 0 || distance == 0 && candidateId < incumbentId;
    }

    private static Vec3 nearestOnSegment(Vec3 start, Vec3 end, AABB box) {
        Vec3 direction = end.subtract(start);
        double before = slope(start, direction, box);
        if (before >= 0) return start;

        // Squared separation is quadratic between crossings of the six box planes. Find the
        // first zero of its piecewise linear derivative, which also resolves parallel faces.
        double[] times = {crossing(box.minX, start.x, direction.x), crossing(box.maxX, start.x, direction.x),
                crossing(box.minY, start.y, direction.y), crossing(box.maxY, start.y, direction.y),
                crossing(box.minZ, start.z, direction.z), crossing(box.maxZ, start.z, direction.z)};
        Arrays.sort(times);
        double previous = 0;
        for (double time : times) {
            if (time <= previous) continue;
            double after = slope(start.add(direction.scale(time)), direction, box);
            if (after >= 0) {
                double nearestTime = previous + (time - previous) * (-before / (after - before));
                return start.add(direction.scale(nearestTime));
            }
            before = after;
            previous = time;
        }
        return end;
    }

    private static double crossing(double plane, double start, double direction) {
        return direction == 0 ? 1 : Math.clamp((plane - start) / direction, 0, 1);
    }

    private static double slope(Vec3 point, Vec3 direction, AABB box) {
        return direction.dot(point.subtract(closestInBox(point, box)));
    }

    private static Vec3 closestInBox(Vec3 point, AABB box) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }
}
