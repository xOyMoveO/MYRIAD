package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.*;
import com.myriad.network.*;
import com.myriad.player.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class CombatFeedbackChecks {
    private record Viewer(ServerPlayer player, List<CustomPacketPayload> packets) {
        <T> T latest(Class<T> type) {
            return packets.reversed().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
        }
    }
    private static java.lang.reflect.Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    @SuppressWarnings("unchecked")
    private static Viewer viewer(GameTestHelper helper, Faction faction, String role) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var player = (ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
        var packets = new ArrayList<CustomPacketPayload>();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom) packets.add(custom.payload());
            }
        };
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        var addon = net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(player.connection);
        var channels = (Set<net.minecraft.resources.Identifier>)field(addon.getClass().getSuperclass(), "sendableChannels").get(addon);
        channels.addAll(List.of(TeamSignalsPayload.TYPE.id(), DeathRecapPayload.TYPE.id(), ControlStatusPayload.TYPE.id()));
        var data = PlayerDataProvider.get(player);
        data.setFaction(faction);
        data.setCharacterId(role);
        ((Map<UUID, Faction>)field(GameManager.class, "factions").get(GameManager.get())).put(player.getUUID(), faction);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
        PlayerHealthSystem.fill(player);
        return new Viewer(player, packets);
    }
    @SuppressWarnings("unchecked")
    static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var online = server.getPlayerList();
        var savedById = new HashMap<>(online.getPlayersByUUID());
        var saved = GameManager.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        List<ServerPlayer> players = null, previous = null;
        var fixtures = new ArrayList<Viewer>();
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(true);
            GameManager.create();
            GameManager.get().setServer(server);
            var phase = field(GameManager.class, "phase");
            phase.set(GameManager.get(), GameManager.Phase.WAITING);
            players = (List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class, "players").get(online);
            previous = List.copyOf(players);
            for (var entry : List.of(Map.entry(Faction.GUARDIAN, "tianxing"), Map.entry(Faction.GUARDIAN, "shuo"),
                    Map.entry(Faction.DESTRUCTION, "fenjue"))) {
                var item = viewer(helper, entry.getKey(), entry.getValue());
                fixtures.add(item);
                players.add(item.player);
                online.getPlayersByUUID().put(item.player.getUUID(), item.player);
            }
            var sender = fixtures.get(0); var ally = fixtures.get(1); var rival = fixtures.get(2);
            var origin = sender.player.getEyePosition();
            var point = TeamSignalTargeting.end(origin, new Vec3(0, 0, 1));
            helper.assertTrue(SignalWheelGeometry.sector(0,0) == -1, "Center cancels the signal wheel");
            for (int sector = 0; sector < 7; sector++) {
                double angle = sector * Math.PI * 2 / 7 - Math.PI / 2;
                helper.assertTrue(SignalWheelGeometry.sector(60 * Math.cos(angle),60 * Math.sin(angle)) == sector, "All seven wheel sectors are selectable");
            }
            helper.getLevel().getChunk(BlockPos.containing(point));
            helper.assertTrue(!TeamSignals.request(sender.player, new TeamSignalRequest(0, point.add(0, 0, .01))), "Reject beyond 18 blocks");
            var wall = BlockPos.containing(origin.add(0, 0, 3));
            var savedBlock = helper.getLevel().getBlockState(wall);
            try {
                helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                helper.assertTrue(!TeamSignals.request(sender.player, new TeamSignalRequest(0, point)), "Reject a signal through a wall");
            } finally { helper.getLevel().setBlockAndUpdate(wall, savedBlock); }
            helper.assertTrue(TeamSignals.request(sender.player, new TeamSignalRequest(0, point)), "Waiting-room training accepts a loaded point at 18 blocks");
            var signals = ally.latest(TeamSignalsPayload.class);
            helper.assertTrue(signals != null && signals.entries().size() == 1 && signals.entries().getFirst().position().equals(point)
                    && rival.latest(TeamSignalsPayload.class) == null, "Only the same faction receives the fixed position");
            wire(helper, signals, TeamSignalsPayload.CODEC);
            helper.assertTrue(!TeamSignals.request(sender.player, new TeamSignalRequest(1, point)), "Same-tick signal spam is rejected");
            TrainingMode.setEnabled(false);
            TeamSignals.tick(server);
            helper.assertTrue(ally.latest(TeamSignalsPayload.class).entries().isEmpty(), "Leaving training clears the snapshot");

            phase.set(GameManager.get(), GameManager.Phase.PLAYING);
            var victim = rival.player;
            var data = PlayerDataProvider.get(victim);
            var cast = AbilityRuntime.context(sender.player, FirstGroupSkills.node("tianxing", 2));
            cast.root(victim, 20, true);
            SilenceSystem.apply(victim, 15);
            ActionTime.apply(victim.getUUID(), sender.player.getUUID(), "test", .5, victim.level().getGameTime()+10);
            SkillChargeSystem.start(victim, "g1_fenjue_q", 20);
            var status = ControlStatusSystem.snapshot(victim);
            helper.assertTrue(status.stun() > 0 && status.silence() == 15 && status.charge() == 20 && status.actionRate() == .5F,
                    "Status reads authoritative state for stun, silence, windup and action rate");
            var publicControls = ControlStatusSystem.controls(victim);
            helper.assertTrue(publicControls.stun() == status.stun() && publicControls.silence() == status.silence()
                    && publicControls.root() == 0, "The owner and the observers use the same settled control readings");
            ControlStatusSystem.tick(server);
            helper.assertTrue(status.equals(rival.latest(ControlStatusPayload.class)), "Status reaches its owner");
            helper.assertTrue(!status.elapsed(13).active(), "Stale status expires");
            SkillChargeSystem.clear(victim);
            AbilityRuntime.cleanseControls(victim);
            ActionTime.clearTarget(victim.getUUID());
            data.setBuff("g1_stun",0);
            ControlStatusSystem.tick(server);
            helper.assertTrue(!rival.latest(ControlStatusPayload.class).active(), "Cleanse and resolved casting clear the owner HUD");

            ModShieldSystem.grant(victim, "feedback-test", 50, 200);
            helper.assertTrue(ModShieldSystem.displayValue(victim) == 50 && EnemyHealthPayload.shieldPixels(.1F, 2000, 60) == 1
                    && EnemyHealthPayload.shieldPixels(5000, 2000, 60) == 60, "Remaining shield is readable and sized safely");
            wire(helper, new EnemyHealthPayload(victim.level().dimension().identifier(), 7,
                    List.of(new EnemyHealthPayload.Entry(victim.getId(), victim.getUUID(), victim.getHealth(),
                            victim.getMaxHealth(), false, 50F, new EnemyHealthPayload.Controls(3, 17, 42)))), EnemyHealthPayload.CODEC);
            float start = victim.getHealth();
            cast.damage(victim, 20, DamageCalc.Kind.TRUE);
            helper.assertTrue(victim.getHealth() == start && ModShieldSystem.displayValue(victim) == 30, "Fully absorbed hit consumes only shield");
            victim.damageCooldownTime = 0;
            cast.damage(victim, 100000, DamageCalc.Kind.TRUE);
            DeathRecaps.tick(server);
            var recap = rival.latest(DeathRecapPayload.class);
            helper.assertTrue(recap != null && recap.present() && HeroRespawnSystem.isWaiting(victim), "Real lethal Fabric hit emits a formal recap");
            helper.assertTrue(Math.abs(recap.hits().stream().mapToDouble(DeathRecapPayload.Hit::health).sum()-start) < .01
                    && Math.abs(recap.hits().stream().mapToDouble(DeathRecapPayload.Hit::shield).sum()-50) < .01,
                    "Final HP loss is capped at pre-hit HP and all shield absorption is counted once: " + recap);
            helper.assertTrue(recap.hits().stream().filter(DeathRecapPayload.Hit::fatal).count() == 1
                    && sender.latest(DeathRecapPayload.class) == null && ally.latest(DeathRecapPayload.class) == null,
                    "Final blow is unique and the recap is private");
            wire(helper, recap, DeathRecapPayload.CODEC);
            System.out.println("[MYRIAD-COMBAT-FEEDBACK] PASS: training signals, shield/health receipts, final blow, and control packets");
            helper.succeed();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            HeroRespawnSystem.clear(); TeamSignals.clear(); DeathRecaps.clearAll(); ControlStatusSystem.clearAll();
            for (var item : fixtures) { AbilityRuntime.clear(item.player); ModShieldSystem.clearFor(item.player); item.player.discard(); }
            if (players != null && previous != null) { players.clear(); players.addAll(previous); }
            online.getPlayersByUUID().clear(); online.getPlayersByUUID().putAll(savedById);
            GameManager.restore(saved, server);
            TrainingMode.setEnabled(training); ModState.setEnabled(enabled);
        }
    }
    private static <T> void wire(GameTestHelper helper, T value, net.minecraft.network.codec.StreamCodec<FriendlyByteBuf,T> codec) {
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            helper.assertTrue(value.equals(codec.decode(buffer)) && !buffer.isReadable(), "Packet round-trip must preserve all fields");
        } finally { buffer.release(); }
    }
}
