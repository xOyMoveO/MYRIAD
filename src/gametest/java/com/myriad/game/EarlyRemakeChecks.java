package com.myriad.game;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

final class EarlyRemakeChecks {
    static void run(GameTestHelper helper) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        try {
            timing(suite);
            suite.install();
            suite.begin("5v5");
            var match = GameManager.get();
            var team = suite.players.stream().filter(p -> match.factionOf(p) == Faction.GUARDIAN).toList();
            var missing = team.getFirst();
            var voter = team.getLast();
            var opponent = suite.opponent(missing);
            var waiting = suite.player(22);
            suite.lobby(waiting, "join", "");
            var history = suite.world.resolve("myriad_last_match.json");
            String previous = Files.isRegularFile(history) ? Files.readString(history) : null;

            // First absence expires. A fresh disconnect at 89.95 s qualifies beyond the cutoff.
            long start = match.getMatchStartTime();
            disconnect(suite, missing);
            tick(suite, start + 400);
            suite.check(QueueLobby.snapshot(voter).remakeOfferId().isEmpty(), "no remake at exactly twenty seconds");
            tick(suite, start + 401);
            var old = QueueLobby.snapshot(voter).remakeOfferId();
            suite.check(!old.isEmpty() && QueueLobby.snapshot(voter).remakeOfferSeconds() == 20,
                    "ten-seat queue opens twenty-second request window");
            suite.check(QueueLobby.snapshot(opponent).remakeOfferId().isEmpty()
                    && QueueLobby.snapshot(waiting).remakeWaitSeconds() < 0, "only fewer-side participants receive action");
            suite.lobby(opponent, "remake", old);
            suite.lobby(waiting, "remake", old);
            suite.check(match.getPhase() == GameManager.Phase.PLAYING, "opposing and waiting players cannot remake");
            tick(suite, start + 801);
            suite.lobby(voter, "remake", old);
            suite.check(match.getPhase() == GameManager.Phase.PLAYING && QueueLobby.snapshot(voter).remakeOfferId().isEmpty(),
                    "expired window stays closed for continuous absence");
            reconnect(suite, missing);
            suite.clock(start + 1799);
            disconnect(suite, missing);
            tick(suite, start + 2200);
            var current = QueueLobby.snapshot(voter).remakeOfferId();
            suite.check(!current.isEmpty() && !current.equals(old), "last eligible tick can mature beyond opening");
            suite.lobby(voter, "remake", old);
            suite.check(match.getPhase() == GameManager.Phase.PLAYING, "reconnected seat invalidates former vote");
            suite.lobby(voter, "remake", current);
            suite.check(match.getPhase() == GameManager.Phase.WAITING && QueueLobby.snapshot(waiting).position() == 1
                    && QueueLobby.snapshot(voter).position() == 0, "one request remakes and preserves next player's queue");
            suite.check(java.util.Objects.equals(previous, Files.isRegularFile(history) ? Files.readString(history) : null),
                    "remake leaves last completed match history intact");
            reconnect(suite, missing);
            suite.lobby(waiting, "leave", "");
            suite.command("myriad arena 3v3");
            var roster = suite.players.subList(0, 4);
            for (String event : List.of("tower", "kill", "normal")) {
                suite.check(match.startQueuedMatch(suite.server, roster), "start another queued 2v2");
                while (match.queueCurrentPicker() != null) tick(suite, match.queueDeadline());
                tick(suite, match.queuePreparationDeadline());
                LaneSystem.tick(suite.server.overworld());
                var absent = roster.getFirst();
                var teammate = roster.stream().filter(p -> p != absent && match.factionOf(p) == match.factionOf(absent))
                        .findFirst().orElseThrow();
                var enemy = suite.opponent(teammate);
                disconnect(suite, absent);
                tick(suite, match.getMatchStartTime() + 401);
                var token = QueueLobby.snapshot(teammate).remakeOfferId();
                suite.check(!token.isEmpty(), "current round has an offer before " + event);
                suite.lobby(teammate, "remake", current);
                suite.check(match.getPhase() == GameManager.Phase.PLAYING, "prior round request cannot remake next round");
                switch (event) {
                    case "tower" -> LaneSystem.onDeath(suite.units("TOWER", match.factionOf(teammate)).getFirst(), enemy);
                    case "kill" -> match.recordHeroDeath(teammate, enemy);
                    case "normal" -> match.endMatch(match.factionOf(enemy));
                }
                suite.lobby(teammate, "remake", token);
                suite.check(QueueLobby.snapshot(teammate).remakeOfferId().isEmpty()
                        && match.getPhase() == (event.equals("normal") ? GameManager.Phase.ENDED : GameManager.Phase.PLAYING),
                        event + " prevents a pending remake");
                match.resetToLobby(suite.server, true);
                reconnect(suite, absent);
            }
            var duel = suite.players.subList(0, 2);
            var first = duel.getFirst();
            var second = duel.getLast();
            for (boolean bothOffline : List.of(false, true)) {
                suite.check(match.startQueuedMatch(suite.server, duel), "start queued duel");
                disconnect(suite, first);
                if (bothOffline) disconnect(suite, second);
                while (match.queueCurrentPicker() != null) tick(suite, match.queueDeadline());
                tick(suite, match.queuePreparationDeadline());
                long opening = match.getMatchStartTime();
                tick(suite, opening + 400);
                suite.check(match.getPhase() == GameManager.Phase.PLAYING, "draft absence still gets full battle grace");
                tick(suite, opening + 401);
                suite.check(match.getPhase() == GameManager.Phase.WAITING,
                        "duel returns directly to lobby with no online voter, both offline = " + bothOffline);
                reconnect(suite, first);
                if (bothOffline) reconnect(suite, second);
                suite.check(!match.isParticipant(first.getUUID()) && QueueLobby.snapshot(first).position() == 0,
                        "return after automatic remake clears seat without requeue");
            }
            suite.report("early-remake", null);
            helper.succeed();
        } catch (Throwable error) {
            suite.report("early-remake", error);
            throw new IllegalStateException(error);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
        }
    }

    private static void timing(ArenaFixtureChecks.Suite suite) {
        var round = UUID.randomUUID();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var enemy = UUID.randomUUID();
        var seats = new HashMap<>(Map.of(a, Faction.GUARDIAN, enemy, Faction.DESTRUCTION));
        var online = new HashSet<>(Set.of(enemy));
        for (boolean bothGone : List.of(false, true)) {
            if (bothGone) online.clear();
            var rule = new EarlyRemake();
            suite.check(rule.evaluate(round, 100, 100, seats, online::contains, false).readyAt() == 501,
                    "seat missing on entry starts at PLAYING, including both absent");
            suite.check(!rule.evaluate(round, 500, 100, seats, online::contains, false).automatic()
                    && rule.evaluate(round, 501, 100, seats, online::contains, false).automatic(),
                    "no online voter ends automatically only after full grace");
        }
        seats.put(b, Faction.GUARDIAN);
        var enemy2 = UUID.randomUUID();
        seats.put(enemy2, Faction.DESTRUCTION);
        online.addAll(seats.keySet());
        var rule = new EarlyRemake();
        online.remove(a);
        rule.evaluate(round, 100, 100, seats, online::contains, false);
        online.remove(b);
        rule.evaluate(round, 300, 100, seats, online::contains, false);
        online.add(a);
        suite.check(rule.evaluate(round, 501, 100, seats, online::contains, false).readyAt() == 701,
                "second player's grace never inherits first player's clock");
        var offer = rule.evaluate(round, 701, 100, seats, online::contains, false);
        online.remove(enemy);
        suite.check(!rule.evaluate(round, 702, 100, seats, online::contains, false).active(), "equal short teams cancel vote");
        online.remove(enemy2);
        suite.check(rule.evaluate(round, 703, 100, seats, online::contains, false).readyAt() == 1103,
                "newly shorter side uses its own clock");
        online.add(enemy);
        online.add(enemy2);
        suite.check(!offer.offerId().equals(rule.evaluate(round, 704, 100, seats, online::contains, false).offerId()),
                "return to same online count issues a different request id");
        online.add(b);
        rule.evaluate(round, 1899, 100, seats, online::contains, false);
        online.remove(a);
        suite.check(!rule.evaluate(round, 1900, 100, seats, online::contains, false).active()
                && !rule.evaluate(round, 2301, 100, seats, online::contains, false).active(),
                "absence starting at ninety seconds stays ineligible");
    }

    private static void tick(ArenaFixtureChecks.Suite suite, long now) {
        suite.clock(now);
        GameManager.get().tick(suite.server, now);
    }

    private static void disconnect(ArenaFixtureChecks.Suite suite, ServerPlayer player) {
        GameManager.get().onPlayerQuit(player);
        suite.server.getPlayerList().getPlayers().remove(player);
        suite.server.getPlayerList().getPlayersByUUID().remove(player.getUUID());
    }

    private static void reconnect(ArenaFixtureChecks.Suite suite, ServerPlayer player) {
        suite.server.getPlayerList().getPlayers().add(player);
        suite.server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        GameManager.get().onPlayerJoin(player);
    }
}
