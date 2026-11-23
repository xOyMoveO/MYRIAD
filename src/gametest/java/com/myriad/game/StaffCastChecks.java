package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.StaffHandCues;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.MagicBullet;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.StaffHandCues.*;

final class StaffCastChecks {
    private record Actor(ServerPlayer player, List<AbilityVfxPayload> visuals) {
        List<AbilityVfxPayload> cues(int skill, int signal) {
            return visuals.stream().filter(StaffHandCues::owns)
                    .filter(p -> p.variant() == skill && p.angle() == signal).toList();
        }
    }

    static void run(GameTestHelper h) {
        com.myriad.client.firstperson.StaffHandChecks.verify(h);
        boolean mod = ModState.isEnabled(), training = TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        var frost = actor(h, "hanze", 196);
        var herb = actor(h, "qingluo", 209);
        var stopped = actor(h, "hanze", 222);
        try {
            for (var a : List.of(frost, herb)) {
                boolean ice = a == frost;
                var staff = new ItemStack(ice ? ModItems.SHUANGBING.get() : ModItems.QINGLUO_STAFF.get());
                staff.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
                a.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, staff);
                MagicBullet.tryFire(a.player, staff);
                MagicBullet.tryFire(a.player, staff);
                h.assertTrue(a.cues(0, BASIC).size() == 1, "Only accepted blasts animate on the caster");
                for (int i = 2; i <= 7; i++) startSkill(a, ice ? "hanze" : "qingluo", i);
            }
            startSkill(stopped, "hanze", 4);
        } catch (Throwable e) {
            cleanup(frost, herb, stopped);
            ModState.setEnabled(mod);
            TrainingMode.setEnabled(training);
            throw e;
        }
        boolean[] finished = {false};
        for (int tick = 1; tick <= 23; tick++) {
            int elapsed = tick;
            h.runAfterDelay(tick, () -> {
                if (finished[0]) return;
                try {
                    for (var actor : List.of(frost, herb, stopped)) AbilityRuntime.tick(actor.player);
                    if (elapsed == 5) AbilityRuntime.cancel(stopped.player);
                    if (elapsed != 23) return;
                    var shots = frost.cues(2, CAST);
                    h.assertTrue(shots.size() == 3
                                    && shots.stream().map(AbilityVfxPayload::radius).toList().equals(List.of(0f, 1f, 2f))
                                    && shots.get(2).started() - shots.get(1).started() == 5,
                            "Three volley cues follow their real scheduled launches");
                    var holds = frost.cues(4, HOLD);
                    var release = frost.cues(4, CAST);
                    h.assertTrue(holds.size() > 2 && holds.getLast().radius() > holds.getFirst().radius()
                                    && release.size() == 1 && release.getFirst().started() - holds.getFirst().started() == 20,
                            "Absolute Zero holds for the real windup and releases once");
                    h.assertTrue(stopped.cues(4, STOP).size() == 1 && stopped.cues(4, CAST).isEmpty(),
                            "Cancelling the real windup never emits a release");
                    for (int skill : new int[]{3, 5, 6, 7})
                        h.assertTrue(frost.cues(skill, CAST).size() == 1, "Each other frost cast emits its own cue");
                    for (int skill = 2; skill <= 7; skill++)
                        h.assertTrue(herb.cues(skill, skill == 2 || skill == 5 ? SELF : CAST).size() == 1,
                                "Each herb cast emits its own cue, including self-targeting and no repeated hand pulse");
                    for (var actor : List.of(frost, herb, stopped))
                        h.assertTrue(actor.visuals.stream().filter(StaffHandCues::owns)
                                .allMatch(p -> p.valid() && AbilityVfxGeometry.build(p, .5f, false).isEmpty()),
                                "Private hand cues do not render duplicate world effects");
                    finished[0] = true;
                    cleanup(frost, herb, stopped);
                    ModState.setEnabled(mod);
                    TrainingMode.setEnabled(training);
                    System.out.println("[MYRIAD-STAFF-VIEW] PASS: casts, volley, windup cancellation, confirmed basics and mirrored grips");
                    h.succeed();
                } catch (Throwable e) {
                    finished[0] = true;
                    cleanup(frost, herb, stopped);
                    ModState.setEnabled(mod);
                    TrainingMode.setEnabled(training);
                    throw e;
                }
            });
        }
    }

    private static void startSkill(Actor a, String role, int skill) {
        var node = FirstGroupSkills.node(role, skill);
        var c = AbilityRuntime.context(a.player, node);
        c.point = a.player.position().add(0, 0, 7);
        node.release(c);
    }

    private static Actor actor(GameTestHelper h, String role, int y) {
        var p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        var packets = new ArrayList<AbilityVfxPayload>();
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof AbilityVfxPayload v && v.owner() == p.getId()) packets.add(v);
            }
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.setNoGravity(true);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7, y, 11))));
        p.setYRot(0);
        p.setXRot(0);
        PlayerDataProvider.get(p).setCharacterId(role);
        p.level().players().add(p);
        return new Actor(p, packets);
    }

    private static void cleanup(Actor... actors) {
        for (var actor : actors) {
            AbilityRuntime.clear(actor.player);
            MagicBullet.clearFor(actor.player.getUUID());
            actor.player.level().players().remove(actor.player);
            actor.player.discard();
        }
    }
}
