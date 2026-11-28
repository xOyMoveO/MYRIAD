package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.client.gui.ShopPurchaseRules;
import com.myriad.network.ProgressAction;
import com.myriad.network.UiFeedbackPayload;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModItems;
import com.myriad.player.PlayerDataProvider;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;
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
        var respawns = HeroRespawnSystem.snapshot();
        var clock = (ServerLevelData) server.overworld().getLevelData();
        long oldTime = server.overworld().getGameTime();
        var byId = server.getPlayerList().getPlayersByUUID();
        var access = new ArrayList<UiFeedbackPayload.ShopAccess>();
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
                var buyer = player(helper, base, "shuo", access);
                var rival = player(helper, base, "rivet", new ArrayList<>());
                var guest = player(helper, base, "keheng", new ArrayList<>());
                created.addAll(List.of(buyer, rival, guest));
                online.addAll(created);
                for (var fixture : created) byId.put(fixture.getUUID(), fixture);
                factions.put(buyer.getUUID(), Faction.GUARDIAN);
                var own = PlayerDataProvider.get(buyer);
                var enemy = PlayerDataProvider.get(rival);
                own.setFaction(Faction.GUARDIAN);
                enemy.setFaction(Faction.DESTRUCTION);
                helper.assertTrue(FirstGroupGrowth.buy(buyer, own, "strength"), "Formal base purchase succeeds");
                helper.assertTrue(own.getGold() == 140 && !FirstGroupGrowth.buy(buyer, own, "strength")
                        && !FirstGroupGrowth.buy(buyer, own, "wisdom") && own.getGold() == 140,
                        "500 opening gold buys one point for 360 and cannot buy another in any attribute");
                verifyAccess(helper, buyer, access, true, true, true);
                helper.assertTrue(TacticalSupplySystem.buy(buyer, "blood_pill"), "Formal supply purchase succeeds");
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 100
                        && MatchBalance.farmReward(buyer, 100, true) == 100, "Empty opposing roster returns both base rewards");
                factions.put(rival.getUUID(), Faction.DESTRUCTION);
                // Restore a running legacy checkpoint with its complete opening roster.
                var startingCheckpoint = GameManager.snapshot().getAsJsonObject();
                startingCheckpoint.remove("legacyEconomySeats");
                GameManager.restore(startingCheckpoint, server);
                manager = GameManager.get();
                helper.assertTrue(own.getGold() < enemy.getGold() && own.getMatchGoldEarned() == enemy.getMatchGoldEarned()
                        && MatchBalance.farmReward(buyer, 100, false) == 100, "Spending wallet gold does not create false catch-up");
                enemy.addGold(1000);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145, "Real income gap grants up to 45 percent");
                helper.assertTrue(MatchBalance.farmReward(rival, 100, false) == 100, "Leading side gains no catch-up");
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, true) == 100, "Gold gap alone does not boost XP");
                enemy.setLevel(2);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, true) == 115, "Personal level gap still boosts XP");
                manager.onPlayerQuit(rival);
                byId.remove(rival.getUUID());
                online.remove(rival);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145
                        && MatchBalance.farmReward(buyer, 100, true) == 115, "Disconnected enemy remains in the locked income and level averages");
                GameManager.restore(GameManager.snapshot(), server);
                manager = GameManager.get();
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145
                        && MatchBalance.farmReward(buyer, 100, true) == 115, "The frozen seat survives a checkpoint reload");

                verifyShoppingAndRespawn(helper, manager, clock, buyer, guest, base, access);
                byId.put(rival.getUUID(), rival);
                online.add(rival);
                manager.onPlayerJoin(rival);
                int actualWage = own.getMatchGoldEarned() - 500;
                helper.assertTrue(enemy.getGold() == 1500 && enemy.getMatchGoldEarned() == 1500 && enemy.getLevel() == 2,
                        "Reconnect restores the locked seat without paying offline wages");
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145,
                        "Rejoining does not change either team's catch-up denominator");
                own.addWalletGold(200);
                helper.assertTrue(own.getMatchGoldEarned() == 500 + actualWage,
                        "Wallet-only credits preserve formal catch-up");
                // The administrator sandbox is available while the server is waiting for a match.
                phase.set(manager, GameManager.Phase.WAITING);
                TrainingMode.setEnabled(true);
                TrainingMode.setNoGoldCost(true);
                verifyAccess(helper, buyer, access, true, true, true);
                int trainingWallet = own.getGold();
                int trainingRank = own.firstGroupAttribute("defense");
                helper.assertTrue(FirstGroupGrowth.buy(buyer, own, "defense")
                        && own.firstGroupAttribute("defense") == trainingRank + 1
                        && TacticalSupplySystem.buy(buyer, "spirit_snare") && own.getGold() == trainingWallet,
                        "Training keeps both permissions and advances a free attribute rank");
                int beforeTraining = own.getGold();
                helper.assertTrue(TrainingMode.isEnabled(buyer), "Buyer is in the waiting-world training sandbox");
                TrainingActions.apply(buyer, "gold", true);
                own.addGold(100);
                helper.assertTrue(own.getGold() == beforeTraining + 10100 && own.getMatchGoldEarned() == 500 + actualWage
                        && MatchBalance.farmReward(buyer, 100, false) == 100,
                        "Wallet credits, training grants and training farm rewards cannot alter formal income");
                TrainingMode.setEnabled(false);
                phase.set(manager, GameManager.Phase.PLAYING);
                helper.assertTrue(MatchBalance.farmReward(buyer, 100, false) == 145,
                        "Leaving the training sandbox preserves formal catch-up");
                var copied = new PlayerData();
                copied.copyStateFrom(own);
                helper.assertTrue(copied.getMatchGoldEarned() == 500 + actualWage && copied.getGold() == own.getGold(), "Player clone preserves both values");
                verifyGuide(helper, buyer);
            } finally {
                if (previous == null) fountains.remove(Faction.GUARDIAN); else fountains.put(Faction.GUARDIAN, previous);
            }
            System.out.println("[MYRIAD-ECONOMY] PASS: shop wire codec and screen decisions, actual purchases, training ledger isolation, catch-up, and guide refresh");
            helper.succeed();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            if (online != null) online.removeAll(created);
            for (var fixture : created) {
                byId.remove(fixture.getUUID(), fixture);
                BaseRules.logout(fixture);
                com.myriad.combat.DeathRecaps.forget(fixture.getUUID());
                fixture.discard();
            }
            clock.setGameTime(oldTime);
            HeroRespawnSystem.restore(respawns);
            GameManager.restore(saved, server);
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        }
    }

    private static void verifyAccess(GameTestHelper helper, ServerPlayer player,
            List<UiFeedbackPayload.ShopAccess> sent, boolean attributes, boolean supplies, boolean equipment) {
        BaseRules.tick(player);
        var expected = new UiFeedbackPayload.ShopAccess(attributes, supplies, equipment);
        helper.assertTrue(!sent.isEmpty() && sent.getLast().equals(expected),
                "Clientbound shop access matches server eligibility for every category");
        var received = sent.getLast();
        int gold = 500;
        String attributeError = ShopPurchaseRules.attributeError(received, CharacterType.SHUO, "strength", 0, gold, false);
        String supplyError = ShopPurchaseRules.supplyError(received, "blood_pill", gold, false);
        helper.assertTrue(attributes == (attributeError == null) && supplies == (supplyError == null),
                "Personal and shop screen purchase checks consume the correct independent permissions");
        if (supplies) {
            helper.assertTrue("message.myriad.equip_no_gold".equals(ShopPurchaseRules.supplyError(received, "blood_pill", 59, false))
                    && ShopPurchaseRules.supplyError(received, "blood_pill", 60, false) == null,
                    "Both purchase screens require 60 gold when the supply permission is present");
        }
    }

    private static void verifyShoppingAndRespawn(GameTestHelper helper, GameManager manager, ServerLevelData clock,
            ServerPlayer buyer, ServerPlayer guest, BlockPos base, List<UiFeedbackPayload.ShopAccess> access)
            throws ReflectiveOperationException {
        var phase = field(GameManager.class, "phase");
        var wallet = PlayerDataProvider.get(buyer);
        var visitor = PlayerDataProvider.get(guest);
        var center = Vec3.atBottomCenterOf(base);
        var server = helper.getLevel().getServer();
        var rivalBase = center.add(20, 0, 0);
        var fountains = CombatRegressionTests.laneFixtureLayout().fountains;
        var savedRivalBase = fountains.put(Faction.DESTRUCTION, BlockPos.containing(rivalBase));
        try {
            phase.set(manager, GameManager.Phase.PICKING);
            helper.assertTrue(!BaseRules.canBuyAttributes(buyer) && !TacticalSupplySystem.buy(buyer, "mana_pill"),
                    "Preparation phase blocks regular economy even at the fountain");
            phase.set(manager, GameManager.Phase.ENDED);
            helper.assertTrue(!BaseRules.canBuyAttributes(buyer) && !BaseRules.canBuySupplies(buyer),
                    "Ended phase blocks regular economy");
            phase.set(manager, GameManager.Phase.PLAYING);
            helper.assertTrue(!FirstGroupGrowth.buy(guest, visitor, "strength")
                    && !TacticalSupplySystem.buy(guest, "scout_totem") && visitor.getGold() == 500,
                    "A nonparticipant cannot buy either category");
            buyer.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(!BaseRules.canBuyAttributes(buyer) && !BaseRules.canBuySupplies(buyer),
                    "Ordinary spectator mode does not confer the death-waiting shop");
            buyer.setGameMode(GameType.ADVENTURE);

            wallet.addWalletGold(1000);
            buyer.setPos(center.add(10, 0, 0));
            verifyAccess(helper, buyer, access, true, true, true);
            int before = wallet.getGold();
            PlayerDataProvider.buySupply(buyer, "mana_pill");
            helper.assertTrue(wallet.getGold() == before - 60 && buyer.getInventory().countItem(ModItems.MANA_PILL.get()) == 1
                    && buyer.getInventory().countItem(ModItems.BLOOD_PILL.get()) == 1,
                    "Exactly ten blocks allows a 60-gold mana pill and leaves the blood pill in inventory");
            buyer.setPos(center.add(10.01, 0, 0));
            verifyAccess(helper, buyer, access, true, false, false);
            before = wallet.getGold();
            PlayerDataProvider.buySupply(buyer, "scout_totem");
            helper.assertTrue(wallet.getGold() == before && buyer.getInventory().countItem(ModItems.SCOUT_TOTEM.get()) == 0,
                    "Outside the boundary no supply is paid or granted");
            buyer.setPos(rivalBase);
            helper.assertTrue(!TacticalSupplySystem.buy(buyer, "spirit_snare"), "Enemy fountain does not allow supplies");
            new ProgressAction(1, "shuo", "g1_attribute_wisdom", 0, 360).handle(buyer);
            helper.assertTrue(wallet.getGold() == before - 360 && wallet.firstGroupAttribute("wisdom") == 1,
                    "Actual quoted attribute request works on the field");
            System.out.printf("[MYRIAD-ECONOMY] shop before=%d fieldWallet=%d twoPills=%d/%d%n", before,
                    wallet.getGold(), buyer.getInventory().countItem(ModItems.BLOOD_PILL.get()),
                    buyer.getInventory().countItem(ModItems.MANA_PILL.get()));

            wallet.setLevel(20);
            buyer.setPos(center);
            PlayerDataProvider.onLivingDeath(buyer, buyer.damageSources().generic());
            helper.assertTrue(HeroRespawnSystem.isWaiting(buyer) && wallet.getBuff("hero_respawn") == 900
                    && buyer.isSpectator(), "Level-20 death starts a 45-second spectator wait");
            verifyAccess(helper, buyer, access, true, false, true);
            before = wallet.getGold();
            PlayerDataProvider.buySupply(buyer, "blood_pill");
            helper.assertTrue(wallet.getGold() == before && buyer.getInventory().countItem(ModItems.BLOOD_PILL.get()) == 1,
                    "Death-waiting supply is rejected even in the player's own fountain");
            new ProgressAction(2, "shuo", "g1_attribute_cooldown", 0, 360).handle(buyer);
            helper.assertTrue(wallet.getGold() == before - 360 && wallet.firstGroupAttribute("cooldown") == 1,
                    "Actual quoted attribute request works during respawn");
            before = wallet.getGold();
            long deathTick = server.overworld().getGameTime();
            manager.tick(server, deathTick + GameManager.PASSIVE_GOLD_INTERVAL_TICKS);
            helper.assertTrue(wallet.getGold() == before + 21 && wallet.getMatchGoldEarned() == 521,
                    "Spectator waiting receives the 21-gold passive wage, without active farm");
            System.out.printf("[MYRIAD-ECONOMY] waiting wallet=%d wage=%d income=%d respawnLv15=%d Lv18=%d Lv20=%d%n",
                    wallet.getGold(), wallet.getGold() - before, wallet.getMatchGoldEarned(),
                    HeroRespawnSystem.waitTicks(15, false), HeroRespawnSystem.waitTicks(18, false), wallet.getBuff("hero_respawn"));
            helper.assertTrue(HeroRespawnSystem.waitTicks(15, false) == 740 && HeroRespawnSystem.waitTicks(18, false) == 836,
                    "The post-level-15 extension preserves the old boundary and supplies the level-18 reference");
            clock.setGameTime(deathTick + 899);
            HeroRespawnSystem.tick(buyer);
            helper.assertTrue(HeroRespawnSystem.isWaiting(buyer) && wallet.getBuff("hero_respawn") == 1,
                    "The last real tick is still a death wait");
            clock.setGameTime(deathTick + 900);
            HeroRespawnSystem.tick(buyer);
            helper.assertTrue(!HeroRespawnSystem.isWaiting(buyer) && !buyer.isSpectator() && wallet.getBuff("hero_respawn") == 0,
                    "The precise respawn deadline restores the hero to play");
            verifyAccess(helper, buyer, access, true, true, true);
        } finally {
            if (savedRivalBase == null) fountains.remove(Faction.DESTRUCTION);
            else fountains.put(Faction.DESTRUCTION, savedRivalBase);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos base, String role,
            List<UiFeedbackPayload.ShopAccess> access) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), role),
                ClientInformation.createDefault()) {
            @Override public boolean isClientAuthoritative() { return false; }
        };
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof UiFeedbackPayload feedback) {
                    var wire = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        UiFeedbackPayload.CODEC.encode(wire, feedback);
                        var decoded = UiFeedbackPayload.CODEC.decode(wire);
                        helper.assertTrue(feedback.equals(decoded) && !wire.isReadable(),
                                "Actual shop eligibility packet survives the clientbound wire codec");
                        var shop = UiFeedbackPayload.ShopAccess.fromKind(decoded.kind());
                        if (shop != null) access.add(shop);
                    } finally {
                        wire.release();
                    }
                }
            }
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
