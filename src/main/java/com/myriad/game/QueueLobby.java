package com.myriad.game;

import com.myriad.network.LobbyNetworking;
import com.myriad.network.Packets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread queue. A player keeps their original ticket when the host switches arenas. */
public final class QueueLobby {
    private static final int PROPOSAL_TICKS = 15 * 20;
    private static final int MAP_POLL_TICKS = 5 * 20;
    private static final EnumMap<MatchArena.Mode, Line> lines = new EnumMap<>(MatchArena.Mode.class);
    private static final Map<UUID, Ticket> lockedTickets = new HashMap<>();
    private static final Map<UUID, Long> actionTokens = new HashMap<>();
    private static final Map<UUID, LobbyNetworking.Snapshot> lastSent = new HashMap<>();
    private static MatchArena.Mode selected;
    private static boolean ready;
    private static List<MatchArena.ConfigStamp> mapStamp = List.of();
    private static long lastMapPoll;
    private static boolean lobbyChanged;
    private static GameManager.Phase publishedPhase;
    private static long publishedTurn;
    private static long publishedPreparation;
    private static long publishedSecond = -1;
    private static long seatsToken;
    private static List<LobbyNetworking.Seat> draftSeats = List.of();
    private static long nextToken;

    private QueueLobby() {}

    private record Ticket(UUID player, MatchArena.Mode arena, long joinedAt, long sequence) {}

    private static final class Line {
        final Map<UUID, Ticket> players = new LinkedHashMap<>();
        long proposalToken;
        long deadline = -1;
        long failedStartToken = -1;
        Map<UUID, Integer> positions;

        int position(UUID id) {
            if (positions == null) {
                positions = new HashMap<>();
                for (UUID player : players.keySet()) positions.put(player, positions.size() + 1);
            }
            return positions.getOrDefault(id, 0);
        }

        void invalidateProposal() {
            deadline = -1;
            proposalToken = ++nextToken;
            positions = null;
            lobbyChanged = true;
        }
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(QueueLobby::tick);
        ServerPlayerEvents.JOIN.register(QueueLobby::sync);
        ServerPlayerEvents.LEAVE.register(QueueLobby::onDisconnect);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    private static void clear() {
        lines.clear();
        actionTokens.clear();
        lastSent.clear();
        lockedTickets.clear();
        selected = null;
        ready = false;
        mapStamp = List.of();
        draftSeats = List.of();
        publishedPhase = null;
        publishedSecond = -1;
        lobbyChanged = true;
    }

    /** Explicit administrative reset invalidates all outstanding menu actions before returning. */
    public static void reset(MinecraftServer server) {
        clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    private static Line line(MatchArena.Mode arena) {
        return lines.computeIfAbsent(arena, key -> new Line());
    }

    private static Ticket ticket(UUID player) {
        for (Line queue : lines.values()) {
            Ticket ticket = queue.players.get(player);
            if (ticket != null) return ticket;
        }
        return null;
    }

    private static void onDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        leave(id);
        actionTokens.remove(id);
        lastSent.remove(id);
    }

    private static void leave(UUID id) {
        Ticket previous = ticket(id);
        if (previous == null) return;
        Line queue = line(previous.arena);
        queue.players.remove(id);
        queue.invalidateProposal();
        actionTokens.remove(id);
    }

    private static void selectMode(MatchArena.Mode mode) {
        if (selected == mode) return;
        for (Line queue : lines.values()) queue.invalidateProposal();
        // A map change invalidates old join buttons; an existing ticket still authorizes leaving.
        actionTokens.keySet().removeIf(id -> ticket(id) == null);
        selected = mode;
        lobbyChanged = true;
    }

    /** The selector has just validated this exact map. */
    static void arenaSelected(MinecraftServer server) {
        selectMode(MatchArena.active());
        recordReadiness(server, true);
    }

    private static void ensureMode(MinecraftServer server) {
        if (selected == MatchArena.active()) return;
        selectMode(MatchArena.active());
        validateArena(server);
    }

    private static boolean validateArena(MinecraftServer server) {
        boolean available = MatchArena.refreshReadiness(server);
        recordReadiness(server, available);
        return available;
    }

    private static void recordReadiness(MinecraftServer server, boolean available) {
        mapStamp = MatchArena.configurationStamp(server);
        lastMapPoll = server.overworld().getGameTime();
        if (ready != available) {
            ready = available;
            line(selected).invalidateProposal();
        }
    }

    private static void checkMapChanges(MinecraftServer server, long now) {
        if (selected != MatchArena.Mode.DUEL || line(selected).players.isEmpty()) return;
        if (now >= lastMapPoll && now - lastMapPoll < MAP_POLL_TICKS) return;
        lastMapPoll = now;
        if (!mapStamp.equals(MatchArena.configurationStamp(server))) validateArena(server);
    }

    public static void tick(MinecraftServer server) {
        if (!ModState.isEnabled()) {
            if (selected != null) reset(server);
            return;
        }
        ensureMode(server);
        long now = server.overworld().getGameTime();
        GameManager manager = GameManager.get();
        if (manager == null) return;
        if (manager.getPhase() == GameManager.Phase.WAITING) {
            checkMapChanges(server, now);
            tryStart(server, manager, now);
        } else if (line(selected).deadline >= 0) {
            line(selected).invalidateProposal();
        }

        var phase = manager.getPhase();
        long turn = manager.queuePickToken();
        long preparation = manager.queuePreparationDeadline();
        long second = now / 20;
        boolean clockChanged = second != publishedSecond;
        boolean draftChanged = turn != publishedTurn || preparation != publishedPreparation;
        if (lobbyChanged || phase != publishedPhase) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) sendChanged(player);
        } else {
            if (clockChanged) {
                for (Line queue : lines.values()) {
                    for (UUID id : queue.players.keySet()) sendOnline(server, id);
                }
            }
            if (clockChanged && manager.remakeView().active()) {
                for (UUID id : manager.queuePickOrder()) sendOnline(server, id);
            }
            if ((clockChanged || draftChanged) && phase == GameManager.Phase.PICKING && manager.isQueuedMatch()) {
                for (var seat : draftSeats(manager)) sendOnline(server, seat.id());
            }
        }
        lobbyChanged = false;
        publishedPhase = phase;
        publishedTurn = turn;
        publishedPreparation = preparation;
        publishedSecond = second;
    }

    static void remakeChanged() {
        lobbyChanged = true;
    }

    private static void sendOnline(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) sendChanged(player);
    }

    private static void sendChanged(ServerPlayer player) {
        var state = snapshot(player);
        if (!state.equals(lastSent.get(player.getUUID()))) send(player, state);
    }

    private static void tryStart(MinecraftServer server, GameManager manager, long now) {
        Line queue = line(selected);
        int count = queue.players.size();
        int capacity = selected.teamSize * 2;
        boolean full = count >= capacity;
        if (!ready || queue.failedStartToken == queue.proposalToken
                || (!full && (queue.deadline < 0 || now < queue.deadline))) return;
        List<Ticket> candidates = queue.players.values().stream().limit(full ? capacity : count).toList();
        List<ServerPlayer> roster = new ArrayList<>(candidates.size());
        for (Ticket candidate : candidates) {
            ServerPlayer player = server.getPlayerList().getPlayer(candidate.player);
            if (player == null) {
                leave(candidate.player);
                return;
            }
            roster.add(player);
        }
        // GameManager validates the active arena again immediately before locking this roster.
        if (!manager.startQueuedMatch(server, roster)) {
            if (!validateArena(server)) return;
            // A rejected full roster must not retry and notify the same players every tick.
            queue.invalidateProposal();
            queue.failedStartToken = queue.proposalToken;
            return;
        }
        lockedTickets.clear();
        for (Ticket candidate : candidates) {
            lockedTickets.put(candidate.player, candidate);
            queue.players.remove(candidate.player);
            actionTokens.remove(candidate.player);
        }
        queue.invalidateProposal();
    }

    /** Restore online seats in their original FIFO order if the match aborts during draft. */
    public static void restoreRoster(MinecraftServer server, List<UUID> ids) {
        MatchArena.Mode mode = MatchArena.active();
        Line queue = line(mode);
        long now = server.overworld().getGameTime();
        for (UUID id : ids) {
            if (server.getPlayerList().getPlayer(id) == null || ticket(id) != null) continue;
            Ticket previous = lockedTickets.get(id);
            queue.players.put(id, previous != null && previous.arena == mode ? previous
                    : new Ticket(id, mode, now, ++nextToken));
            actionTokens.remove(id);
        }
        var ordered = queue.players.values().stream().sorted(Comparator.comparingLong(Ticket::sequence)).toList();
        queue.players.clear();
        for (Ticket entry : ordered) queue.players.put(entry.player, entry);
        queue.invalidateProposal();
        lockedTickets.clear();
    }

    private static boolean eligibleCount(Line queue) {
        return queue.players.size() == 2 || queue.players.size() == 4;
    }

    private static void join(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        if (ticket(id) != null) return;
        Line queue = line(selected);
        queue.players.put(id, new Ticket(id, selected, now, ++nextToken));
        queue.invalidateProposal();
        actionTokens.remove(id);
    }

    public static void handle(ServerPlayer player, LobbyNetworking.Action request) {
        if ("refresh".equals(request.action())) {
            sync(player);
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (!ModState.isEnabled() || GameManager.get() == null) {
            sync(player);
            return;
        }
        ensureMode(server);
        GameManager manager = GameManager.get();
        long now = server.overworld().getGameTime();
        long deadline = line(selected).deadline;
        if (deadline >= 0 && now >= deadline) tick(server);
        var before = snapshot(player);
        switch (request.action()) {
            case "preselect", "lock" -> {
                if (before.queuedMatch() && before.participant() && request.token() == before.pickToken()
                        && manager.getPhase() == GameManager.Phase.PICKING) {
                    if ("preselect".equals(request.action())) manager.queuePreselect(player, request.value());
                    else manager.queueLock(player, request.value());
                }
            }
            case "remake" -> manager.requestRemake(player, request.value(), request.token());
            case "propose" -> {
                if (before.canPropose() && request.token() == before.proposalToken() && validateArena(server)) {
                    Line queue = line(selected);
                    queue.invalidateProposal();
                    queue.deadline = now + PROPOSAL_TICKS;
                }
            }
            case "join", "requeue", "leave", "train", "return" -> {
                if (before.participant() || request.token() != before.queueToken()) break;
                switch (request.action()) {
                    case "join", "requeue" -> join(player, now);
                    case "leave" -> leave(player.getUUID());
                    case "train" -> {
                        if (!before.training() && TrainingGround.enter(player)) actionTokens.remove(player.getUUID());
                    }
                    case "return" -> {
                        if (before.training()) TrainingGround.leave(player);
                        else LobbySystem.teleportToLobby(player);
                        actionTokens.remove(player.getUUID());
                    }
                }
            }
            default -> { }
        }
        // Consume a full line before any subsequent request can remove or replace a selected seat.
        var previousDelivery = lastSent.get(player.getUUID());
        tick(server);
        if (previousDelivery == lastSent.get(player.getUUID())) sync(player);
    }

    private static int secondsRemaining(long deadline, long now) {
        return deadline < 0 ? -1 : (int) Math.max(0, (deadline - now + 19) / 20);
    }

    public static LobbyNetworking.Snapshot snapshot(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        boolean enabled = ModState.isEnabled();
        if (enabled) ensureMode(server);
        MatchArena.Mode active = MatchArena.active();
        UUID id = player.getUUID();
        long now = server.overworld().getGameTime();
        Ticket member = ticket(id);
        MatchArena.Mode mode = member == null ? active : member.arena;
        Line queue = line(mode);
        GameManager manager = GameManager.get();
        boolean occupied = manager == null || manager.getPhase() != GameManager.Phase.WAITING;
        boolean participant = manager != null && manager.isParticipant(id) && occupied;
        String reason;
        if (!enabled) reason = "disabled";
        else if (participant) reason = "participant";
        else if (mode != active) reason = "inactive";
        else if (!ready || queue.failedStartToken == queue.proposalToken) reason = "unready";
        else if (occupied) reason = "occupied";
        else reason = "";
        int position = member == null ? 0 : queue.position(id);
        boolean proposal = member != null && queue.deadline >= 0;
        boolean canPropose = member != null && mode == MatchArena.Mode.DUEL && reason.isEmpty()
                && queue.deadline < 0 && eligibleCount(queue);
        boolean queuedMatch = participant && manager.isQueuedMatch();
        String preselected = queuedMatch ? manager.queuePreselected(id) : "";
        boolean draft = queuedMatch && manager.getPhase() == GameManager.Phase.PICKING;
        List<LobbyNetworking.Seat> seats = draft ? draftSeats(manager) : List.of();
        UUID picker = draft ? manager.queueCurrentPicker() : null;
        var remake = queuedMatch ? manager.remakeView() : EarlyRemake.View.NONE;
        boolean canRemake = remake.side() != null && remake.side() == manager.queueFaction(id);
        return new LobbyNetworking.Snapshot(active.id, member == null ? "" : mode.id,
                queue.players.size(), mode.teamSize * 2, position,
                member == null ? 0 : (int) Math.max(0, (now - member.joinedAt) / 20),
                proposal ? secondsRemaining(queue.deadline, now) : -1,
                reason, canPropose, participant, TrainingGround.isTraining(player),
                actionTokens.computeIfAbsent(id, key -> ++nextToken), queuedMatch ? manager.queuePickToken() : 0,
                preselected == null ? "" : preselected, queuedMatch, TrainingGround.isSparring(player),
                canPropose ? queue.proposalToken : 0, seats, picker == null ? "" : picker.toString(),
                draft ? secondsRemaining(manager.queueDeadline(), now) : -1,
                draft ? secondsRemaining(manager.queuePreparationDeadline(), now) : -1,
                Math.min(20, secondsRemaining(remake.readyAt(), now)), secondsRemaining(remake.offerUntil(), now),
                canRemake ? remake.offerId() : "");
    }

    private static List<LobbyNetworking.Seat> draftSeats(GameManager manager) {
        long token = manager.queuePickToken();
        if (draftSeats.isEmpty() || token != seatsToken) {
            draftSeats = manager.queuePickOrder().stream().map(seat -> {
                Faction faction = manager.queueFaction(seat);
                return new LobbyNetworking.Seat(seat, manager.queueName(seat),
                        faction == null ? "" : faction.getName(), manager.queuePickedRole(seat));
            }).toList();
            seatsToken = token;
        }
        return draftSeats;
    }

    public static void sync(ServerPlayer player) {
        send(player, snapshot(player));
    }

    private static void send(ServerPlayer player, LobbyNetworking.Snapshot state) {
        lastSent.put(player.getUUID(), state);
        Packets.sendToPlayer(player, state);
    }
}
