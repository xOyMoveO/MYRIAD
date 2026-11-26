package com.myriad.game;

import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class MatchEconomyChecks {
    private static java.lang.reflect.Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var saved = GameManager.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var created = new ArrayList<ServerPlayer>();
        List<ServerPlayer> online = null;
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            GameManager.create();
            var manager = GameManager.get();
            manager.setServer(server);
            var phase = field(GameManager.class, "phase");
            phase.set(manager, GameManager.Phase.PLAYING);
            field(GameManager.class, "matchStartTime").setLong(manager, level.getGameTime());
            var factions = (Map<UUID, Faction>)field(GameManager.class, "factions").get(manager);
            online = (List<ServerPlayer>)field(PlayerList.class, "players").get(server.getPlayerList());
            var base = helper.absolutePos(new BlockPos(3, 2, 3));
            var fountains = CombatRegressionTests.laneFixtureLayout().fountains;
            var previous = fountains.put(Faction.GUARDIAN, base);
            try {
                var buyer = player(helper, base, "shuo");
                var rival = player(helper, base, "rivet");
                created.addAll(List.of(buyer, rival));
                online.addAll(created);
                factions.put(buyer.getUUID(), Faction.GUARDIAN);
                factions.put(rival.getUUID(), Faction.DESTRUCTION);
                var own = PlayerDataProvider.get(buyer);
                var enemy = PlayerDataProvider.get(rival);
                own.setFaction(Faction.GUARDIAN);
                enemy.setFaction(Faction.DESTRUCTION);
                helper.assertTrue(FirstGroupGrowth.buy(buyer, own, "strength"), "Formal base purchase succeeds");
                helper.assertTrue(own.getGold() == 140 && !FirstGroupGrowth.buy(buyer, own, "strength")
                        && !FirstGroupGrowth.buy(buyer, own, "wisdom") && own.getGold() == 140,
                        "500 opening gold buys one point for 360 and cannot buy another in any attribute");
                helper.assertTrue(TacticalSupplySystem.buy(buyer, "blood_pill"), "Formal supply purchase succeeds");
                helper.assertTrue(own.getGold() < enemy.getGold() && own.getMatchGoldEarned() == enemy.getMatchGoldEarned()
                        && MatchBalance.farmReward(buyer, 100, false) == 100, "Spending wallet gold does not create false catch-up");
                enemy.addGold(1000);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145, "Real income gap grants up to 45 percent");
                helper.assertTrue(MatchBalance.farmReward(rival, 100, false) == 100, "Leading side gains no catch-up");
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, true) == 100, "Gold gap alone does not boost XP");
                enemy.setLevel(2);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, true) == 115, "Personal level gap still boosts XP");
                own.addWalletGold(200);
                helper.assertTrue(own.getMatchGoldEarned() == 500 && MatchBalance.farmReward(buyer, 100, false) == 145,
                        "Wallet-only credits preserve formal catch-up");
                // The administrator sandbox is available while the server is waiting for a match.
                phase.set(manager, GameManager.Phase.WAITING);
                TrainingMode.setEnabled(true);
                int beforeTraining = own.getGold();
                helper.assertTrue(TrainingMode.isEnabled(buyer), "Buyer is in the waiting-world training sandbox");
                TrainingActions.apply(buyer, "gold", true);
                own.addGold(100);
                helper.assertTrue(own.getGold() == beforeTraining + 10100 && own.getMatchGoldEarned() == 500
                        && MatchBalance.farmReward(buyer, 100, false) == 100,
                        "Wallet credits, training grants and training farm rewards cannot alter formal income");
                TrainingMode.setEnabled(false);
                phase.set(manager, GameManager.Phase.PLAYING);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145,
                        "Leaving the training sandbox preserves formal catch-up");
                var copied = new PlayerData();
                copied.copyStateFrom(own);
                helper.assertTrue(copied.getMatchGoldEarned() == 500 && copied.getGold() == own.getGold(), "Player clone preserves both values");
                verifyGuide(helper, buyer);
            } finally {
                if (previous == null) fountains.remove(Faction.GUARDIAN); else fountains.put(Faction.GUARDIAN, previous);
            }
            System.out.println("[MYRIAD-ECONOMY] PASS: actual purchases, training ledger isolation, catch-up, and existing guide refresh");
            helper.succeed();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            if (online != null) online.removeAll(created);
            created.forEach(ServerPlayer::discard);
            GameManager.restore(saved, server);
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos base, String role) {
        var player = (ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setPos(Vec3.atBottomCenterOf(base));
        var data = PlayerDataProvider.get(player);
        data.setCharacterId(role);
        data.setLevel(1);
        data.setGold(500);
        data.resetMatchGoldEarned(500);
        return player;
    }

    private static void verifyGuide(GameTestHelper helper, ServerPlayer player) {
        var book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("guide.myriad.title"));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("MYRIAD Guide"),
                "MYRIAD", 0, List.of(Filterable.passThrough(Component.literal("Old guide"))), true));
        int slot = 12;
        player.getInventory().setItem(slot, book);
        GuideBook.give(player);
        GuideBook.give(player);
        var refreshed = player.getInventory().getItem(slot);
        var content = refreshed.get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(refreshed == book && content != null && content.pages().size() == 10
                && content.getPages(false).get(9).equals(Component.translatable("guide.myriad.page10")),
                "The existing guide is refreshed in place with all ten translated pages");
        int copies = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
            if (player.getInventory().getItem(index).is(Items.WRITTEN_BOOK)) copies++;
        helper.assertTrue(copies == 1, "Repeated guide grants do not duplicate the manual");
    }
}
