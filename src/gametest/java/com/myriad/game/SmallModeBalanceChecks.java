package com.myriad.game;

import com.google.gson.JsonObject;
import com.myriad.Myriad;
import com.myriad.character.CharacterType;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.combat.DamageCalc;
import com.myriad.network.ProgressAction;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Server rule checks for small rounds entered through the public lobby queue. */
final class SmallModeBalanceChecks {
    private static final List<String> ROLES = List.of("shuo", "tianxing", "qingluo", "chixiao");
    private static final List<String> MINION_KINDS = List.of("MELEE", "RANGED", "MAGIC", "SIEGE");
    private static final Vec3 FIGHT = new Vec3(336, 75, 64);
    private long purchaseSequence;

    static void run(GameTestHelper helper) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        var checks = new SmallModeBalanceChecks();
        var rounds = new ArrayList<Map<String, Object>>();
        var result = new JsonObject();
        result.addProperty("source", "Fabric dedicated GameTest with synthetic ServerPlayer seats");
        result.addProperty("dedicated", suite.server.isDedicatedServer());
        result.addProperty("tempo", MatchTempo.CHERRY_V3.name());
        result.addProperty("independentClients", 0);
        result.addProperty("humanPlayers", 0);
        try {
            suite.install();
            Files.writeString(MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json"),
                    "{\"profile\":\"cherry_v3\"}");
            suite.check(suite.command("myriad arena 3v3") == 1, "select the synthetic single-lane arena");
            suite.command("myriad mod on");
            var players = IntStream.range(0, 4).mapToObj(suite::player).toList();
            var waiting = suite.player(4);
            // Apply any saved world-reset generation at first login, before reserving match seats.
            for (var player : suite.players) ServerPlayerEvents.JOIN.invoker().onJoin(player);
            for (int seats : List.of(2, 4)) {
                var roster = players.subList(0, seats);
                rounds.add(checks.combat(suite, roster));
                GameManager.get().resetToLobby(suite.server, true);
                rounds.add(remake(suite, roster, waiting));
            }
            result.addProperty("passed", true);
            helper.succeed();
        } catch (Throwable error) {
            result.addProperty("passed", false);
            result.addProperty("failure", error.toString());
            throw new IllegalStateException(error);
        } finally {
            result.add("rounds", MatchProgress.JSON.toJsonTree(rounds));
            result.add("checks", MatchProgress.JSON.toJsonTree(suite.passed));
            var path = suite.world.resolve("balance-small-modes-events.json");
            try {
                Files.writeString(path, MatchProgress.JSON.toJson(result));
                Myriad.LOGGER.info("MYRIAD_SMALL_MODES passed={} assertions={} events={}",
                        result.get("passed"), suite.passed.size(), path);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            } finally {
                GameManager.get().resetToLobby(suite.server, false);
                suite.removePlayers();
            }
        }
    }

    private static long open(ArenaFixtureChecks.Suite suite, List<ServerPlayer> roster) {
        var match = GameManager.get();
        suite.check(match.getPhase() == GameManager.Phase.WAITING, "small round starts at the lobby");
        for (var player : roster) suite.lobby(player, "join", "");
        var first = roster.getFirst();
        suite.check(QueueLobby.snapshot(first).count() == roster.size(), "lobby holds exactly the requested seats");
        suite.lobby(first, "propose", "");
        long proposed = suite.server.overworld().getGameTime();
        suite.check(QueueLobby.snapshot(first).voteSeconds() == 15, "one request starts a 15-second proposal");
        suite.clock(proposed + 299);
        QueueLobby.tick(suite.server);
        suite.check(match.getPhase() == GameManager.Phase.WAITING, "299 ticks leave the queue unlocked");
        suite.clock(proposed + 300);
        QueueLobby.tick(suite.server);
        var picks = match.queuePickOrder();
        suite.check(match.isQueuedMatch() && match.getPhase() == GameManager.Phase.PICKING
                && picks.size() == roster.size() && roster.stream().allMatch(p -> picks.contains(p.getUUID())),
                "300 ticks lock precisely the proposed seats");
        for (var faction : Faction.values())
            suite.check(roster.stream().filter(p -> match.factionOf(p) == faction).count() == roster.size() / 2,
                    "locked seats are split evenly for " + faction);
        for (int i = 0; i < picks.size(); i++) {
            var player = suite.server.getPlayerList().getPlayer(picks.get(i));
            suite.lobby(player, "lock", ROLES.get(i));
            suite.check(ROLES.get(i).equals(data(player).getCharacterId()), "server locks the requested unique role");
        }
        long start = match.queuePreparationDeadline();
        tick(suite, start - 1);
        suite.check(match.getPhase() == GameManager.Phase.PICKING
                && roster.stream().allMatch(p -> data(p).getGold() == 0), "preparation grants no opening gold");
        tick(suite, start);
        suite.check(match.getPhase() == GameManager.Phase.PLAYING && GameManager.combatTempo() == MatchTempo.CHERRY_V3
                && roster.stream().allMatch(p -> data(p).getGold() == 500 && data(p).getMatchGoldEarned() == 500),
                "combat uses the Cherry profile and deposits 500 gold per locked participant");
        return start;
    }

    private Map<String, Object> combat(ArenaFixtureChecks.Suite suite, List<ServerPlayer> roster) throws Exception {
        long start = open(suite, roster);
        var match = GameManager.get();
        var buyer = suite.server.getPlayerList().getPlayer(match.queuePickOrder().getFirst());
        var leader = suite.opponent(buyer);
        var buyerData = data(buyer);
        var faction = match.factionOf(buyer);
        var leaderFaction = match.factionOf(leader);
        String mode = mode(roster);
        var results = new ArrayList<Map<String, Object>>();
        var roles = roster.stream().map(p -> Map.of("role", data(p).getCharacterId(),
                "faction", match.factionOf(p).getName())).toList();

        buy(suite, buyer, "strength", 0, 360);
        suite.check(buyerData.getGold() == 140 && buyerData.firstGroupAttribute("strength") == 1,
                "first strength point costs 360, leaving 140");
        buy(suite, buyer, "strength", 1, 432);
        buy(suite, buyer, "wisdom", 0, 360);
        suite.check(buyerData.getGold() == 140 && buyerData.firstGroupAttribute("strength") == 1
                && buyerData.firstGroupAttribute("wisdom") == 0, "the opening purse cannot pay for another point");
        disconnect(suite, buyer);
        reconnect(suite, buyer);
        disconnect(suite, buyer);
        reconnect(suite, buyer);
        suite.check(data(buyer) == buyerData && buyerData.getGold() == 140 && buyerData.getMatchGoldEarned() == 500
                && buyerData.firstGroupAttribute("strength") == 1 && faction == match.factionOf(buyer),
                "two same-round returns retain the seat and purchase without another opening deposit");
        results.add(Map.of("event", "opening-and-reconnect", "purse", 500, "firstPrice", 360,
                "walletAfterFirstPurchase", buyerData.getGold(), "successfulReturnsWithoutDeposit", 2));

        roster.forEach(p -> p.setPos(FIGHT.add(0, 0, 32)));
        leader.setPos(FIGHT);
        long firstWave = start + GameManager.combatTempo().firstWaveTicks();
        for (int i = 0; i < 2; i++) {
            wave(suite, firstWave + i * LaneSystem.WAVE_INTERVAL, faction, leader);
            clearOpposingWave(suite, leaderFaction);
        }
        int[] active = catchUp(buyer);
        var leaderData = data(leader);
        int leaderGold = leaderData.getGold(), leaderIncome = leaderData.getMatchGoldEarned();
        int leaderLevel = leaderData.getLevel(), leaderXp = leaderData.getXp();
        suite.check(active[0] > 100 && active[0] <= 145 && active[1] > 100 && active[1] <= 145,
                "actual minion rewards create positive bounded gold and XP catch-up");
        long dropout = start + 1801;
        tick(suite, dropout);
        disconnect(suite, leader);
        int[] offline = catchUp(buyer);
        suite.check(match.isParticipant(leader.getUUID()) && match.factionOf(leader) == leaderFaction
                && java.util.Arrays.equals(active, offline), "the departing lead retains its exact share of both faction averages");
        int onlineBefore = buyerData.getMatchGoldEarned();
        tick(suite, start + 1900);
        int wage = buyerData.getMatchGoldEarned() - onlineBefore;
        suite.check(wage > 0 && leaderData.getGold() == leaderGold && leaderData.getMatchGoldEarned() == leaderIncome
                && leaderData.getLevel() == leaderLevel && leaderData.getXp() == leaderXp,
                "a genuine wage tick pays the online seat while the offline wallet, income and XP stay frozen");
        tick(suite, dropout + 401);
        suite.check(match.getPhase() == GameManager.Phase.PLAYING && QueueLobby.snapshot(buyer).remakeOfferId().isEmpty(),
                "a departure after the opening window does not remake the game");
        int[] beforeReconnect = catchUp(buyer);
        reconnect(suite, leader);
        int[] returned = catchUp(buyer);
        suite.check(java.util.Arrays.equals(beforeReconnect, returned)
                && data(leader).getGold() == leaderGold && data(leader).getMatchGoldEarned() == leaderIncome
                && data(leader).getLevel() == leaderLevel && data(leader).getXp() == leaderXp
                && match.factionOf(leader) == leaderFaction,
                "rejoining restores the locked seat with no back pay or catch-up discontinuity");
        results.add(Map.of("event", "late-dropout", "dropoutTick", dropout - start,
                "catchUpBefore", active, "catchUpWhileOffline", offline, "catchUpAfterWageTick", beforeReconnect,
                "catchUpOnReturn", returned, "onlineWage", wage, "offlineWage", 0, "offlineLevel", leaderLevel,
                "offlineIncome", leaderIncome));

        buyer.setPos(FIGHT);
        leader.setPos(FIGHT.add(2, 0, 0));
        int beforeGold = buyerData.getGold(), beforeXp = buyerData.getXp();
        int gap = leaderLevel - buyerData.getLevel();
        long elapsed = suite.server.overworld().getGameTime() - start;
        int soloGold = GlobalBalance.goldIncome(MatchBalance.killReward(false, elapsed, 0, 0, gap));
        int soloXp = MatchBalance.killReward(true, elapsed, 0, 0, gap);
        var thirdParties = roster.stream().filter(p -> p != buyer && p != leader).toList();
        var otherGold = thirdParties.stream().map(p -> data(p).getGold()).toList();
        var otherXp = thirdParties.stream().map(p -> data(p).getXp()).toList();
        DamageCalc.dealBasicDamage(buyer, leader, leader.getMaxHealth() * 2, DamageCalc.Kind.TRUE);
        suite.check(HeroRespawnSystem.isWaiting(leader) && match.killsOf(buyer) == 1 && match.deathsOf(leader) == 1,
                "the damage pipeline settles one solo death and schedules respawn");
        suite.check(buyerData.getGold() - beforeGold == soloGold && buyerData.getXp() - beforeXp == soloXp
                && roster.stream().allMatch(p -> match.assists().total(p.getUUID()) == 0)
                && thirdParties.stream().map(p -> data(p).getGold()).toList().equals(otherGold)
                && thirdParties.stream().map(p -> data(p).getXp()).toList().equals(otherXp),
                "zero assists pay only normal solo gold and XP, with neither pool passed to any seat");
        results.add(Map.of("event", "solo-hero-kill", "elapsedTick", elapsed, "assistCount", 0,
                "killerGold", buyerData.getGold() - beforeGold, "killerXp", buyerData.getXp() - beforeXp));

        int waves = 2;
        while (buyerData.getLevel() < 20 && waves < 140) {
            wave(suite, firstWave + waves * LaneSystem.WAVE_INTERVAL, leaderFaction, buyer);
            clearOpposingWave(suite, faction);
            waves++;
        }
        suite.check(buyerData.getLevel() == 20, "awarded minion XP reaches the shared cap at level 20");
        int cappedXp = buyerData.getXp(), cappedSkillPoints = buyerData.getSkillPoints();
        wave(suite, firstWave + waves * LaneSystem.WAVE_INTERVAL, leaderFaction, buyer);
        clearOpposingWave(suite, faction);
        suite.check(buyerData.getLevel() == 20 && buyerData.getXp() > cappedXp && buyerData.getXp() >= 0
                && buyerData.getSkillPoints() == cappedSkillPoints && buyerData.getGold() >= 0,
                "an additional real wave awards XP while level and skill points remain capped");
        results.add(Map.of("event", "level-cap", "level", buyerData.getLevel(), "wavesClearedByBuyer", waves - 1,
                "xpAtCap", cappedXp, "xpAfterAdditionalWave", buyerData.getXp(),
                "skillPointsAtCap", cappedSkillPoints, "skillPointsAfterAdditionalWave", buyerData.getSkillPoints()));

        int funded = buyerData.getGold();
        buy(suite, buyer, "strength", 1, FirstGroupGrowth.PRICE_OVERFLOW);
        suite.check(buyerData.getGold() == funded && buyerData.firstGroupAttribute("strength") == 1,
                "a bogus overflow quote cannot change gold or rank");
        buy(suite, buyer, "strength", 1, 432);
        buy(suite, buyer, "wisdom", 0, 360);
        suite.check(buyerData.getGold() == funded - 432 - 360 && buyerData.getGold() >= 0
                && buyerData.firstGroupAttribute("strength") == 2 && buyerData.firstGroupAttribute("wisdom") == 1,
                "earned gold buys strength at 432 and the first independent wisdom point at 360");
        int overflow = IntStream.range(1, 512).filter(rank -> FirstGroupGrowth.price(rank) == FirstGroupGrowth.PRICE_OVERFLOW)
                .findFirst().orElseThrow();
        suite.check(FirstGroupGrowth.price(overflow - 1) > Integer.MAX_VALUE
                && "message.myriad.equip_no_gold".equals(FirstGroupGrowth.purchaseError(CharacterType.SHUO,
                        "cooldown", overflow - 1, Integer.MAX_VALUE, false))
                && "message.myriad.g1.attribute_price_overflow".equals(FirstGroupGrowth.purchaseError(CharacterType.SHUO,
                        "cooldown", overflow, Integer.MAX_VALUE, false)),
                "the quote guard rejects unaffordable prices before the signed-long overflow sentinel");
        results.add(Map.of("event", "later-purchases", "walletBefore", funded, "secondStrengthPrice", 432,
                "firstWisdomPrice", 360, "walletAfter", buyerData.getGold(), "firstOverflowRank", overflow));
        return Map.of("kind", "combat", "mode", mode, "seats", roster.size(), "roles", roles, "events", results);
    }

    private static Map<String, Object> remake(ArenaFixtureChecks.Suite suite, List<ServerPlayer> roster,
                                              ServerPlayer waiting) throws Exception {
        long start = open(suite, roster);
        var match = GameManager.get();
        var roles = roster.stream().map(p -> data(p).getCharacterId()).toList();
        var history = suite.world.resolve("myriad_last_match.json");
        String previous = Files.isRegularFile(history) ? Files.readString(history) : null;
        suite.lobby(waiting, "join", "");
        var absentee = roster.getFirst();
        var side = match.factionOf(absentee);
        var opponent = suite.opponent(absentee);
        disconnect(suite, absentee);
        tick(suite, start + 400);
        suite.check(match.getPhase() == GameManager.Phase.PLAYING && match.remakeView().offerId().isEmpty(),
                "an early departure has not qualified at exactly 20 seconds");
        tick(suite, start + 401);
        boolean auto = roster.size() == 2;
        if (!auto) {
            var voter = roster.stream().filter(p -> p != absentee && match.factionOf(p) == side).findFirst().orElseThrow();
            var offer = QueueLobby.snapshot(voter).remakeOfferId();
            suite.check(!offer.isEmpty() && QueueLobby.snapshot(opponent).remakeOfferId().isEmpty(),
                    "the 2v2 offer is available only to the shorter side");
            suite.lobby(opponent, "remake", offer);
            suite.check(match.getPhase() == GameManager.Phase.PLAYING, "the full side cannot submit the remake");
            suite.lobby(voter, "remake", offer);
        }
        suite.check(match.getPhase() == GameManager.Phase.WAITING && match.getWinner() == null
                && roster.stream().noneMatch(p -> match.isParticipant(p.getUUID()))
                && QueueLobby.snapshot(waiting).position() == 1
                && Objects.equals(previous, Files.isRegularFile(history) ? Files.readString(history) : null),
                "the remake clears the seats without a result, preserving the next player's queue position");
        reconnect(suite, absentee);
        suite.check(QueueLobby.snapshot(absentee).position() == 0 && data(absentee).getCharacterId() == null
                && data(absentee).getGold() == 0, "return after the remake does not revive the old seat or queue ticket");
        suite.lobby(absentee, "join", "");
        int queuePosition = QueueLobby.snapshot(absentee).position();
        suite.check(queuePosition == 2 && QueueLobby.snapshot(waiting).position() == 1
                && QueueLobby.snapshot(waiting).canPropose(),
                "a returning participant can requeue behind the waiting player and propose another 1v1");
        suite.lobby(absentee, "leave", "");
        suite.lobby(waiting, "leave", "");
        return Map.of("kind", "no-fault-remake", "mode", mode(roster), "seats", roster.size(), "roles", roles,
                "automatic", auto, "eligibleAfterTicks", 401, "completedMatchHistoryPreserved", true,
                "waitingPosition", 1, "returningSeatQueuePosition", queuePosition);
    }

    private static void wave(ArenaFixtureChecks.Suite suite, long at, Faction opponent, ServerPlayer farmer)
            throws Exception {
        suite.clock(at);
        LaneSystem.tick(suite.server.overworld());
        var minions = minions(suite, opponent);
        suite.check(minions.size() == 4, "a scheduled wave creates exactly four enemy minions");
        for (var minion : minions) {
            minion.setPos(FIGHT.add(2, 0, 0));
            DamageCalc.dealBasicDamage(farmer, minion, 100_000, DamageCalc.Kind.TRUE);
            suite.check(!minion.isAlive(), "actual damage kills the lane minion");
        }
    }

    private static void clearOpposingWave(ArenaFixtureChecks.Suite suite, Faction unused) throws Exception {
        for (var minion : minions(suite, unused)) {
            LaneSystem.onDeath(minion, null, true);
            minion.discard();
        }
    }

    private static List<Mob> minions(ArenaFixtureChecks.Suite suite, Faction faction) throws Exception {
        var result = new ArrayList<Mob>();
        for (String kind : MINION_KINDS) result.addAll(suite.units(kind, faction));
        return result;
    }

    private void buy(ArenaFixtureChecks.Suite suite, ServerPlayer player, String attribute, int rank, long quote) {
        suite.receive(player, new ProgressAction(++purchaseSequence, data(player).getCharacterId(),
                ProgressAction.ATTRIBUTE + attribute, rank, quote));
    }

    private static int[] catchUp(ServerPlayer player) {
        return new int[]{MatchBalance.farmReward(player, 100, false), MatchBalance.farmReward(player, 100, true)};
    }

    private static void tick(ArenaFixtureChecks.Suite suite, long now) {
        suite.clock(now);
        GameManager.get().tick(suite.server, now);
    }

    private static void disconnect(ArenaFixtureChecks.Suite suite, ServerPlayer player) {
        ServerPlayerEvents.LEAVE.invoker().onLeave(player);
        suite.server.getPlayerList().getPlayers().remove(player);
        suite.server.getPlayerList().getPlayersByUUID().remove(player.getUUID());
    }

    private static void reconnect(ArenaFixtureChecks.Suite suite, ServerPlayer player) {
        suite.server.getPlayerList().getPlayers().add(player);
        suite.server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        ServerPlayerEvents.JOIN.invoker().onJoin(player);
    }

    private static PlayerData data(ServerPlayer player) {
        return PlayerDataProvider.get(player);
    }

    private static String mode(List<ServerPlayer> roster) {
        return roster.size() == 2 ? "1v1" : "2v2";
    }
}
