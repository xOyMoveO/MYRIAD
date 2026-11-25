package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityTargeting;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.network.AbilityMotionPayload;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.NetworkSetup;
import com.myriad.network.ShuoViewPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Production play codecs and registered Fabric receivers, with collision-stepped mock motion.
 * This is server objective coverage; native keyboard input and real-client transport remain separate acceptance.
 */
final class ShuoPilotChecks {
    private ShuoPilotChecks() { }

    static void run(GameTestHelper h) {
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        boolean noMana = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        var packets = new ArrayList<CustomPacketPayload>();
        var sounds = new ArrayList<GameTestSounds.PlayedSound>();
        Consumer<GameTestSounds.PlayedSound> listener = sounds::add;
        GameTestSounds.addListener(listener);
        var player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom) {
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                    try {
                        ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(buf, custom);
                        packets.add(ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(buf).payload());
                        h.assertTrue(!buf.isReadable(), "Production S2C codec consumes the complete payload");
                    } finally { buf.release(); }
                }
            }
        };
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setNoGravity(true);
        player.setYRot(0);
        player.setXRot(0);
        var floor = h.absolutePos(new BlockPos(5, 3, 5));
        var origin = Vec3.atBottomCenterOf(floor.above());
        var saved = new HashMap<BlockPos, BlockState>();
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 9; z++) for (int y = 0; y <= 3; y++) {
            var pos = floor.offset(x, y, z);
            saved.put(pos, h.getLevel().getBlockState(pos));
            h.getLevel().setBlockAndUpdate(pos, (y == 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        player.setPos(origin);
        h.getLevel().players().add(player);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        var target = h.spawn(EntityTypes.PIG, new BlockPos(5, 4, 10));
        target.setPos(origin.add(0, 0, 5));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        Runnable cleanup = () -> {
            AbilityRuntime.clear(player);
            h.getLevel().players().remove(player);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID());
            player.discard();
            target.discard();
            channel.finishAndReleaseAll();
            GameTestSounds.removeListener(listener);
            saved.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            TrainingMode.setEnabled(training);
            TrainingMode.setNoMana(noMana);
            TrainingMode.setNoCooldown(noCooldown);
            ModState.setEnabled(enabled);
        };
        try {
            receive(h, player, new NetworkSetup.ChooseCharacterPayload("shuo"));
            var data = PlayerDataProvider.get(player);
            var skill = FirstGroupSkills.node("shuo", 4);
            h.assertTrue("shuo".equals(data.getCharacterId()) && data.getSkillPoints() == 1,
                    "Registered choose-character receiver selects Shuo and grants exactly one initial point");
            receive(h, player, new NetworkSetup.UpgradeSkillPayload(skill.getId()));
            h.assertTrue(data.skillLevel(skill.getId()) == 1 && data.getSkillPoints() == 0,
                    "Registered upgrade receiver learns rank one and spends the initial point");
            receive(h, player, new NetworkSetup.UpgradeSkillPayload(skill.getId()));
            receive(h, player, new NetworkSetup.UpgradeSkillPayload("shuo_duanliu"));
            h.assertTrue(data.skillLevel(skill.getId()) == 1 && data.skillLevel("shuo_duanliu") == 0,
                    "Repeat and opposite-branch requests cannot bypass points or branch rules");
            player.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);
            data.setMana(700);
            for (String reason : List.of("root", "stun", "mana", "disabled")) {
                if (reason.equals("root")) data.setBuff("g1_root", 20);
                if (reason.equals("stun")) data.setBuff("g1_stun", 20);
                if (reason.equals("mana")) data.setMana(0);
                if (reason.equals("disabled")) ModState.setEnabled(false);
                double mana = data.getMana();
                int count = packets.size(), audio = sounds.size();
                receive(h, player, new NetworkSetup.CastSkillPayload(2, 0, 0));
                h.assertTrue(data.getMana() == mana && data.getCooldown(skill.getId()) == 0
                        && player.position().equals(origin) && !AbilityRuntime.channeling(player),
                        "Rejected " + reason + " request cannot pay, move or start a cast");
                h.assertTrue(packets.size() == count && sounds.size() == audio,
                        "Rejected " + reason + " request cannot emit a skill cue");
                data.setBuff("g1_root", 0);
                data.setBuff("g1_stun", 0);
                data.setMana(700);
                ModState.setEnabled(true);
            }
            receive(h, player, new NetworkSetup.CastSkillPayload(2, 0, 0));
            h.assertTrue(data.getMana() == 620 && data.getCooldown(skill.getId()) == 220,
                    "Actual registered cast receiver pays exactly 80 mana and 220 cooldown ticks");
            h.assertTrue(player.position().equals(origin) && AbilityRuntime.channeling(player),
                    "Sheathed YiShan starts its ten-action-tick windup without teleporting");
            int cues = packets.size();
            receive(h, player, new NetworkSetup.CastSkillPayload(2, 0, 0));
            h.assertTrue(data.getMana() == 620 && data.getCooldown(skill.getId()) == 220 && packets.size() == cues,
                    "Duplicate input during cooldown cannot repeat payment or presentation");
            boolean[] done = {false};
            for (int n = 1; n <= 25; n++) {
                int age = n;
                h.runAfterDelay(n, () -> {
                    if (done[0]) return;
                    try {
                        if (AbilityRuntime.moving(player)) player.move(MoverType.SELF,
                                AbilityTargeting.clampPrediction(player.position(), origin.add(0, 0, 7), player.getDeltaMovement()));
                        AbilityRuntime.tick(player);
                        if (age == 9) h.assertTrue(player.position().equals(origin) && target.getHealth() == 1000,
                                "Nine completed action ticks cannot move or deal early damage");
                        if (age == 20) h.assertTrue(player.position().distanceTo(origin.add(0, 0, 7)) < .001
                                && Math.abs(target.getHealth() - 928.6) < .05,
                                "Seven-block collision-stepped dash deals the original 60 percent split of the 119 zero-purchase total once");
                        if (age == 24) h.assertTrue(Math.abs(target.getHealth() - 881) < .05,
                                "Delayed wound deals the remaining 40 percent exactly once");
                        if (age == 25) {
                            h.assertTrue(packets.stream().anyMatch(ShuoViewPayload.class::isInstance)
                                    && packets.stream().anyMatch(AbilityMotionPayload.class::isInstance)
                                    && packets.stream().anyMatch(AbilityVfxPayload.class::isInstance),
                                    "Production S2C codecs carry weapon, authoritative motion and world VFX cues");
                            h.assertTrue(sounds.stream().filter(e -> e.getSound().value().location().getPath().equals("shuo_charge")).count() == 1
                                    && sounds.stream().filter(e -> e.getSound().value().location().getPath().equals("shuo_draw")).count() == 1,
                                    "Actual native sound broadcast emits one charge and one draw at the original stages");
                            verifyPersistence(h, player);
                            done[0] = true;
                            cleanup.run();
                            h.succeed();
                        }
                    } catch (Throwable failure) { done[0] = true; cleanup.run(); throw failure; }
                });
            }
        } catch (Throwable failure) { cleanup.run(); throw failure; }
    }

    private static void verifyPersistence(GameTestHelper h, ServerPlayer player) {
        var problems = new net.minecraft.util.ProblemReporter.Collector();
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(problems, h.getLevel().registryAccess());
        player.saveWithoutId(output);
        h.assertTrue(problems.isEmpty(), "Native entity save encodes the Fabric player attachment without problems: " + problems.getReport());
        var restored = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        var clone = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        clone.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), clone,
                CommonListenerCookie.createInitial(clone.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) { }
        };
        java.nio.file.Path file = null;
        try {
            file = java.nio.file.Files.createTempFile(h.getLevel().getServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT), "myriad-shuo-player-", ".nbt");
            net.minecraft.nbt.NbtIo.writeCompressed(output.buildResult(), file);
            var saved = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(problems, h.getLevel().registryAccess(), saved));
            var data = PlayerDataProvider.get(restored);
            h.assertTrue(problems.isEmpty() && "shuo".equals(data.getCharacterId())
                    && data.skillLevel("shuo_yishan") == 1 && data.getMana() == 620
                    && data.getCooldown("shuo_yishan") == 220 && data.getSkillPoints() == 0,
                    "Native entity save, disk NBT read and entity load restore new Fabric player progression");
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(player, clone, false);
            var copied = PlayerDataProvider.get(clone);
            h.assertTrue(copied != PlayerDataProvider.get(player) && "shuo".equals(copied.getCharacterId())
                    && copied.skillLevel("shuo_yishan") == 1 && copied.getSkillPoints() == 0,
                    "Registered Fabric death-clone callback preserves learned progression in a separate attachment");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        finally {
            restored.discard(); clone.discard();
            if (file != null) try { java.nio.file.Files.deleteIfExists(file); }
                catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }

    static void receive(GameTestHelper h, ServerPlayer player, CustomPacketPayload payload) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buf, new ServerboundCustomPayloadPacket(payload));
            var decoded = ServerboundCustomPayloadPacket.STREAM_CODEC.decode(buf);
            h.assertTrue(!buf.isReadable() && decoded.payload().getClass() == payload.getClass(),
                    "Production C2S play codec decodes the registered payload completely");
            player.connection.handleCustomPayload(decoded);
        } finally { buf.release(); }
    }
}
