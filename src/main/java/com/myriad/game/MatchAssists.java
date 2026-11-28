package com.myriad.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** One ledger per queued match. Pending evidence is scoped to the enemy's current life. */
public final class MatchAssists {
    static final int CONTACT_TICKS = 200;
    static final int RESCUE_TICKS = 120;
    private record Damage(UUID owner, long at, float health) {}
    private record Rescue(UUID owner, UUID protectedAlly, long at) {}
    private static final class Life {
        final List<Damage> damage = new ArrayList<>();
        final Map<UUID, Long> controls = new HashMap<>();
        final Map<UUID, Long> opponents = new HashMap<>();
        final List<Rescue> rescues = new ArrayList<>();

        void prune(long now) {
            damage.removeIf(d -> !recent(d.at(), now, CONTACT_TICKS));
            controls.values().removeIf(t -> !recent(t, now, CONTACT_TICKS));
            opponents.values().removeIf(t -> !recent(t, now, CONTACT_TICKS));
            rescues.removeIf(r -> !recent(r.at(), now, RESCUE_TICKS));
        }
    }

    private final Map<UUID, Life> lives = new HashMap<>();
    private final Map<UUID, Integer> totals = new HashMap<>();

    private static boolean recent(long at, long now, int ticks) {
        return at <= now && now - at <= ticks;
    }

    private static GameManager match(ServerPlayer player) {
        GameManager manager = GameManager.get();
        return manager != null && manager.isQueuedMatch() && manager.getPhase() == GameManager.Phase.PLAYING
                && ModState.isEnabled() && !TrainingMode.isEnabled(player) && manager.isParticipant(player.getUUID())
                && !player.isSpectator() && !HeroRespawnSystem.isWaiting(player) ? manager : null;
    }

    private static GameManager opponents(Entity owner, LivingEntity target) {
        if (!(owner instanceof ServerPlayer actor) || !(target instanceof ServerPlayer hero)
                || actor.level() != hero.level()) return null;
        GameManager manager = match(actor);
        return manager != null && manager == match(hero) && manager.factionOf(actor) != manager.factionOf(hero)
                ? manager : null;
    }

    public static void healthLost(Entity owner, LivingEntity target, float health) {
        GameManager manager = opponents(owner, target);
        if (manager != null && Float.isFinite(health) && health > 0)
            manager.assists().hit(owner.getUUID(), target.getUUID(), target.level().getGameTime(), health, false);
    }

    public static void controlled(ServerPlayer owner, LivingEntity target) {
        GameManager manager = opponents(owner, target);
        if (manager != null && owner.isAlive() && target.isAlive())
            manager.assists().hit(owner.getUUID(), target.getUUID(), target.level().getGameTime(), 0, true);
    }

    public static void healed(ServerPlayer owner, LivingEntity target, float gained) {
        if (!(target instanceof ServerPlayer ally) || owner == ally || owner.level() != ally.level() || !(gained > 0)) return;
        GameManager manager = match(ally);
        if (manager != null && manager == match(owner) && manager.factionOf(owner) == manager.factionOf(ally))
            manager.assists().protect(owner.getUUID(), ally.getUUID(), ally.level().getGameTime());
    }

    /** A consumed shield proves both the enemy interaction and protection of this particular ally. */
    public static void absorbed(UUID shieldOwner, Entity attacker, LivingEntity target) {
        GameManager manager = opponents(attacker, target);
        if (manager == null) return;
        var ledger = manager.assists();
        long now = target.level().getGameTime();
        ledger.hit(attacker.getUUID(), target.getUUID(), now, 0, false);
        if (shieldOwner != null && !shieldOwner.equals(target.getUUID())
                && manager.queueFaction(shieldOwner) == manager.queueFaction(target.getUUID()))
            ledger.rescue(attacker.getUUID(), shieldOwner, target.getUUID(), now);
    }

    void hit(UUID owner, UUID target, long now, float health, boolean control) {
        Life life = lives.computeIfAbsent(target, ignored -> new Life());
        life.prune(now);
        life.opponents.put(owner, now);
        lives.computeIfAbsent(owner, ignored -> new Life()).opponents.put(target, now);
        if (health > 0) life.damage.add(new Damage(owner, now, health));
        if (control) life.controls.put(owner, now);
    }

    void protect(UUID owner, UUID ally, long now) {
        Life life = lives.get(ally);
        if (life == null) return;
        life.prune(now);
        for (UUID enemy : life.opponents.keySet()) rescue(enemy, owner, ally, now);
    }

    private void rescue(UUID enemy, UUID owner, UUID ally, long now) {
        Life life = lives.computeIfAbsent(enemy, ignored -> new Life());
        life.prune(now);
        life.rescues.removeIf(r -> r.owner().equals(owner) && r.protectedAlly().equals(ally));
        life.rescues.add(new Rescue(owner, ally, now));
    }

    List<UUID> death(UUID victim, UUID killer, float maxHealth, long now, List<UUID> killerTeam) {
        List<UUID> credited = List.of();
        Life life = lives.get(victim);
        if (life != null && killer != null) {
            life.prune(now);
            Set<UUID> eligible = new HashSet<>(life.controls.keySet());
            Map<UUID, Double> damage = new HashMap<>();
            for (Damage hit : life.damage) damage.merge(hit.owner(), (double) hit.health(), Double::sum);
            damage.forEach((owner, hp) -> { if (hp >= maxHealth * .05) eligible.add(owner); });
            for (Rescue rescue : life.rescues)
                if (rescue.protectedAlly().equals(killer)) eligible.add(rescue.owner());
            eligible.remove(killer);
            credited = killerTeam.stream().filter(eligible::contains).toList();
            for (UUID owner : credited) totals.merge(owner, 1, Integer::sum);
        }
        newLife(victim);
        return credited;
    }

    void newLife(UUID player) {
        lives.remove(player);
        for (Life life : lives.values()) {
            life.opponents.remove(player);
            life.rescues.removeIf(r -> r.protectedAlly().equals(player));
        }
    }

    public int total(UUID id) { return totals.getOrDefault(id, 0); }
}
