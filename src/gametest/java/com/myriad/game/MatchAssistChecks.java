package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.MatchTabPayload;
import com.myriad.network.NetworkSetup;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

final class MatchAssistChecks {
    static void run(GameTestHelper test) {
        windows(test);
        packets(test);
        match(test);
        rewardShares(test);
        test.succeed();
    }

    private static void windows(GameTestHelper test) {
        UUID victim = UUID.randomUUID(), killer = UUID.randomUUID(), dealer = UUID.randomUUID();
        UUID helper = UUID.randomUUID(), controller = UUID.randomUUID(), small = UUID.randomUUID();
        UUID late = UUID.randomUUID(), other = UUID.randomUUID();
        MatchAssists ledger = new MatchAssists();
        ledger.hit(dealer, victim, 0, 3, false);
        ledger.hit(controller, victim, 0, 0, true);
        ledger.hit(controller, victim, 1, 20, true);
        ledger.hit(small, victim, 50, 4.9f, false);
        ledger.hit(small, victim, 51, 0, false); // Fully shielded contact establishes combat only.
        ledger.hit(victim, killer, 65, 1, false);
        ledger.protect(late, killer, 79);
        ledger.protect(helper, killer, 80);
        ledger.protect(other, dealer, 90);
        ledger.hit(dealer, victim, 150, 2, false);
        ledger.hit(killer, victim, 200, 99, false);
        ledger = MatchProgress.JSON.fromJson(MatchProgress.JSON.toJson(ledger), MatchAssists.class);
        var team = List.of(controller, dealer, helper, late, small, other, killer);
        test.assertTrue(ledger.death(victim, killer, 100, 200, team).equals(List.of(controller, dealer, helper)),
                "Assists use locked team order and deduplicate contributions");
        test.assertTrue(ledger.total(dealer) == 1 && ledger.total(helper) == 1 && ledger.total(controller) == 1,
                "Inclusive damage, control and rescue boundaries survive a checkpoint and deduplicate");
        test.assertTrue(ledger.total(small) + ledger.total(other) + ledger.total(late) + ledger.total(killer) == 0,
                "Subthreshold, another ally, expired rescue and final killer receive no assist");
        ledger.death(victim, killer, 100, 201, team);
        ledger.hit(dealer, victim, 201, 10, true);
        ledger.death(victim, killer, 100, 402, team);
        ledger.hit(dealer, victim, 405, 10, false);
        ledger.death(victim, null, 100, 405, team);
        ledger.death(victim, killer, 100, 406, team);
        test.assertTrue(ledger.total(dealer) == 1 && ledger.total(controller) == 1,
                "New life, expired damage/control and unattributed environmental death clear pending credit");
    }

    private static void packets(GameTestHelper test) {
        var entry = new MatchTabPayload.Entry(UUID.randomUUID(), "hero", "hanze", 1, 7, 2, 4, 3, 700);
        var tab = new MatchTabPayload(200, List.of(entry));
        var row = new NetworkSetup.MatchResultEntry("hero", "guardian", "hanze", 7, 2, 4, 3, 700, 14, 15, 16);
        var results = new NetworkSetup.MatchResultsPayload("guardian", 120, List.of(row));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MatchTabPayload.CODEC.encode(buf, tab);
            test.assertTrue(MatchTabPayload.CODEC.decode(buf).equals(tab) && !buf.isReadable(), "Tab K/D/A round trip");
            NetworkSetup.MatchResultsPayload.STREAM_CODEC.encode(buf, results);
            test.assertTrue(NetworkSetup.MatchResultsPayload.STREAM_CODEC.decode(buf).equals(results) && !buf.isReadable(),
                    "Result K/D/A round trip preserves economy, damage and healing");
        } finally {
            buf.release();
        }
    }

    private static ServerPlayer player(GameTestHelper test, List<ServerPlayer> players, String role) {
        var player = (ServerPlayer)test.makeMockServerPlayer(GameType.SURVIVAL);
        players.add(player);
        player.connection = new ServerGamePacketListenerImpl(test.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        PlayerDataProvider.get(player).setCharacterId(role);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void match(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var previous = GameManager.snapshot();
        var respawns = HeroRespawnSystem.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var players = new ArrayList<ServerPlayer>();
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            var victim = player(test, players, "shuo");
            var killer = player(test, players, "rivet");
            var healer = player(test, players, "qingluo");
            var support = player(test, players, "yunsheng");
            var control = player(test, players, "xuanchen");
            var dealer = player(test, players, "tianxing");
            var checkpoint = MatchProgress.JSON.toJsonTree(new GameManager()).getAsJsonObject();
            checkpoint.remove("assists"); // A checkpoint from the previous protocol.
            checkpoint.addProperty("phase", "PLAYING");
            for (var player : players) {
                var team = player == victim ? Faction.DESTRUCTION : Faction.GUARDIAN;
                PlayerDataProvider.get(player).setFaction(team);
                checkpoint.getAsJsonObject("factions").addProperty(player.getUUID().toString(), team.getName());
                checkpoint.getAsJsonArray("queueRoster").add(player.getUUID().toString());
            }
            GameManager.restore(checkpoint, server);
            var heal = AbilityRuntime.context(healer, FirstGroupSkills.node("qingluo", 2));
            var shield = AbilityRuntime.context(support, FirstGroupSkills.node("yunsheng", 2));
            var cc = AbilityRuntime.context(control, FirstGroupSkills.node("xuanchen", 2));
            MatchAssists.healthLost(victim, killer, 1);
            heal.heal(killer, 100);
            shield.shield(killer, 9, 40);
            GameManager.get().recordHeroDeath(victim, killer);
            test.assertTrue(GameManager.get().assists().total(healer.getUUID()) == 0
                    && GameManager.get().assists().total(support.getUUID()) == 0, "Overheal and unused shield grant no assist");

            ModShieldSystem.consume(killer, 3, killer.damageSources().playerAttack(victim));
            killer.setHealth(killer.getMaxHealth() - 5);
            heal.heal(killer, 5);
            cc.root(victim, 10, true);
            float before = victim.getHealth();
            com.myriad.combat.DamageCalc.dealUncriticalSkillDamage(dealer, victim,
                    victim.getMaxHealth() * .15f, com.myriad.combat.DamageCalc.Kind.TRUE, 2);
            test.assertTrue(before - victim.getHealth() >= victim.getMaxHealth() * .05,
                    "Real hero damage reaches the health-loss hook");
            int gold = PlayerDataProvider.get(support).getGold();
            int xp = PlayerDataProvider.get(support).getXp();
            // Only the last attacker is online at settlement. The other contributors live in the checkpoint.
            server.getPlayerList().getPlayersByUUID().put(killer.getUUID(), killer);
            victim.damageCooldownTime = 0;
            com.myriad.combat.DamageCalc.dealUncriticalSkillDamage(killer, victim,
                    1, com.myriad.combat.DamageCalc.Kind.TRUE, 2);
            GameManager.restore(GameManager.snapshot(), server);
            var tower = new DefenseTowerEntity(com.myriad.registry.ModEntities.DEFENSE_TOWER.get(), test.getLevel());
            PlayerDataProvider.onLivingDeath(victim, victim.damageSources().mobAttack(tower));
            test.assertTrue(GameManager.get().killsOf(killer) == 2 && HeroRespawnSystem.isWaiting(victim),
                    "A tower finishing a recently damaged hero preserves last-enemy kill credit");
            tower.discard();
            for (var contributor : List.of(healer, support, control, dealer))
                test.assertTrue(GameManager.get().assists().total(contributor.getUUID()) == 1,
                        "Offline roster retains actual ally heal, shield absorption and applied control");
            test.assertTrue(PlayerDataProvider.get(support).getGold() == gold && PlayerDataProvider.get(support).getXp() == xp,
                    "Offline assist shares leave the wallet and XP frozen until rejoin");
        } finally {
            for (var p : players) {
                server.getPlayerList().getPlayersByUUID().remove(p.getUUID(), p);
                com.myriad.combat.DeathRecaps.forget(p.getUUID());
                AbilityRuntime.clear(p);
                ModShieldSystem.clearFor(p.getUUID());
                p.discard();
            }
            HeroRespawnSystem.restore(respawns);
            GameManager.restore(previous, server);
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        }
    }
    private static void rewardShares(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var byId = server.getPlayerList().getPlayersByUUID();
        var previous = GameManager.snapshot();
        var respawns = HeroRespawnSystem.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var players = new ArrayList<ServerPlayer>();
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            var killer = player(test, players, "rivet");
            var first = player(test, players, "yunsheng");
            var second = player(test, players, "qingluo");
            var soloVictim = player(test, players, "shuo");
            var oneVictim = player(test, players, "xuanchen");
            var twoVictim = player(test, players, "tianxing");
            var checkpoint = MatchProgress.JSON.toJsonTree(new GameManager()).getAsJsonObject();
            UUID round = UUID.randomUUID();
            checkpoint.addProperty("queueRound", round.toString());
            checkpoint.addProperty("phase", "PLAYING");
            checkpoint.addProperty("matchStartTime", test.getLevel().getGameTime());
            for (var player : players) {
                var team = List.of(killer, first, second).contains(player) ? Faction.GUARDIAN : Faction.DESTRUCTION;
                var data = PlayerDataProvider.get(player);
                data.setFaction(team);
                data.setLevel(5);
                data.beginQueueRound(round);
                data.grantOpeningPurse();
                checkpoint.getAsJsonObject("factions").addProperty(player.getUUID().toString(), team.getName());
                checkpoint.getAsJsonObject("charPicks").addProperty(data.getCharacterId(), player.getUUID().toString());
                checkpoint.getAsJsonArray("queueRoster").add(player.getUUID().toString());
                byId.put(player.getUUID(), player);
            }
            GameManager.restore(checkpoint, server);
            var firstWallet = PlayerDataProvider.get(first);
            var secondWallet = PlayerDataProvider.get(second);
            var killerWallet = PlayerDataProvider.get(killer);

            heroDeath(soloVictim, killer);
            test.assertTrue(killerWallet.getGold() == 598 && killerWallet.getXp() == 30 && firstWallet.getGold() == 500
                    && firstWallet.getXp() == 0 && secondWallet.getGold() == 500 && secondWallet.getXp() == 0,
                    "Zero assists retain both pools and pay the killer the normal 98 gold and 30 XP");
            System.out.printf("[MYRIAD-ECONOMY] assists=0 killer=%d/%d recipients=%d/%d,%d/%d%n",
                    killerWallet.getGold(), killerWallet.getXp(), firstWallet.getGold(), firstWallet.getXp(),
                    secondWallet.getGold(), secondWallet.getXp());

            MatchAssists.healthLost(oneVictim, killer, 1);
            killer.setHealth(killer.getMaxHealth() - 2);
            AbilityRuntime.context(first, FirstGroupSkills.node("yunsheng", 2)).heal(killer, 2);
            heroDeath(oneVictim, killer);
            test.assertTrue(killerWallet.getGold() == 696 && killerWallet.getXp() == 60 && firstWallet.getGold() == 601
                    && firstWallet.getXp() == 51 && secondWallet.getGold() == 500 && secondWallet.getXp() == 0,
                    "One effective rescue earns the entire assist pool and does not change the killer's reward");
            System.out.printf("[MYRIAD-ECONOMY] assists=1 killer=%d/%d recipient=%d/%d%n",
                    killerWallet.getGold(), killerWallet.getXp(), firstWallet.getGold(), firstWallet.getXp());

            MatchAssists.controlled(second, twoVictim);
            MatchAssists.controlled(first, twoVictim);
            MatchAssists.healthLost(first, twoVictim, twoVictim.getMaxHealth() * .10f);
            GameManager.get().onPlayerQuit(first);
            byId.remove(first.getUUID(), first);
            heroDeath(twoVictim, killer);
            test.assertTrue(firstWallet.getGold() == 601 && firstWallet.getXp() == 51 && secondWallet.getGold() == 550
                    && secondWallet.getXp() == 25 && killerWallet.getGold() == 794 && killerWallet.getXp() == 90,
                    "Two eligible contributors split both pools, and a disconnected contributor remains frozen");
            test.assertTrue(GameManager.get().assists().total(first.getUUID()) == 2
                    && GameManager.get().assists().total(second.getUUID()) == 1,
                    "Wallet shares and KDA reuse one deduplicated eligibility list");
            var pendingCheckpoint = GameManager.snapshot();
            GameManager.restore(pendingCheckpoint, server);
            byId.put(first.getUUID(), first);
            GameManager.get().onPlayerJoin(first);
            test.assertTrue(firstWallet.getGold() == 652 && firstWallet.getXp() == 77,
                    "The first locked seat receives the extra one gold and one XP on same-round reconnect");
            // Reload the earlier match checkpoint with a saved player receipt to exercise adjacent saves.
            var savedWallet = com.mojang.serialization.JsonOps.INSTANCE;
            var encoded = PlayerData.CODEC.encodeStart(savedWallet, firstWallet).getOrThrow();
            var restoredWallet = PlayerData.CODEC.parse(savedWallet, encoded).getOrThrow();
            PlayerDataProvider.set(first, restoredWallet);
            GameManager.restore(pendingCheckpoint, server);
            GameManager.get().onPlayerJoin(first);
            test.assertTrue(restoredWallet.getGold() == 652 && restoredWallet.getXp() == 77,
                    "The persisted player receipt rejects double payment after a checkpoint and repeat join");
            System.out.printf("[MYRIAD-ECONOMY] assists=2 killer=%d/%d lockedFirst=%d/%d lockedSecond=%d/%d%n",
                    killerWallet.getGold(), killerWallet.getXp(), restoredWallet.getGold(), restoredWallet.getXp(),
                    secondWallet.getGold(), secondWallet.getXp());

            restoredWallet.addGold(1000);
            restoredWallet.setLevel(8);
            int goldWithThree = MatchBalance.farmReward(soloVictim, 100, false);
            int xpWithThree = MatchBalance.farmReward(soloVictim, 100, true);
            GameManager.get().onPlayerQuit(first);
            byId.remove(first.getUUID(), first);
            test.assertTrue(goldWithThree > 100 && xpWithThree == 115
                    && MatchBalance.farmReward(soloVictim, 100, false) == goldWithThree
                    && MatchBalance.farmReward(soloVictim, 100, true) == xpWithThree,
                    "Three locked seats retain the same averages when the richest and highest-level hero leaves");
            byId.put(first.getUUID(), first);
            GameManager.get().onPlayerJoin(first);
            test.assertTrue(MatchBalance.farmReward(soloVictim, 100, false) == goldWithThree
                    && MatchBalance.farmReward(soloVictim, 100, true) == xpWithThree,
                    "Rejoining the three-seat roster retains the frozen income and level");
            System.out.printf("[MYRIAD-ECONOMY] 3v3 catch-up base100 before/after/rejoin gold=%d xp=%d%n", goldWithThree, xpWithThree);

            GameManager.get().resetToLobby(server, false);
            var reset = GameManager.snapshot().getAsJsonObject();
            test.assertTrue(reset.getAsJsonObject("pendingAssistRewards").isEmpty()
                    && reset.getAsJsonArray("queueRoster").isEmpty()
                    && reset.getAsJsonObject("offlineStats").isEmpty(),
                    "Round reset clears pending shares, locked order, and frozen seat state");
        } finally {
            for (var fixture : players) {
                byId.remove(fixture.getUUID(), fixture);
                com.myriad.combat.DeathRecaps.forget(fixture.getUUID());
                fixture.discard();
            }
            HeroRespawnSystem.restore(respawns);
            GameManager.restore(previous, server);
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        }
    }

    private static void heroDeath(ServerPlayer victim, ServerPlayer killer) {
        PlayerDataProvider.onLivingDeath(victim, victim.damageSources().playerAttack(killer));
    }

}
