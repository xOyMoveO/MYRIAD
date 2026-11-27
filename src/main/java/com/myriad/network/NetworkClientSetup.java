package com.myriad.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import com.myriad.Myriad;
import com.myriad.client.ClientDataHolder;
import com.myriad.network.NetworkSetup.*;

/** Client receivers are kept separate so common registration is safe on a dedicated server. */
@Environment(EnvType.CLIENT)
final class NetworkClientSetup {
    private static final Set<ClientConfigurationPacketListenerImpl> MATCHED = ConcurrentHashMap.newKeySet();

    private NetworkClientSetup() {}

    static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(NetworkProtocol.VersionPayload.TYPE, (payload, context) -> {
            if (payload.version() != NetworkProtocol.VERSION) {
                context.responseSender().disconnect(NetworkProtocol.mismatch());
                return;
            }
            MATCHED.add(context.packetListener());
            context.responseSender().sendPacket(new NetworkProtocol.VersionPayload(NetworkProtocol.VERSION));
        });
        ClientConfigurationConnectionEvents.COMPLETE.register((listener, client) -> {
            if (!MATCHED.remove(listener)) ClientConfigurationNetworking.getSender().disconnect(NetworkProtocol.mismatch());
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> MATCHED.remove(listener));

        ClientPlayNetworking.registerGlobalReceiver(WeaponPosePayload.TYPE,
                (p,ctx) -> com.myriad.client.firstperson.WeaponBodyPose.receive(p));
        ClientPlayNetworking.registerGlobalReceiver(YikeStatePayload.TYPE,
                (payload,context)->com.myriad.client.YikeClientState.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(LuoxiaBowPosePayload.TYPE,
                (payload,context) -> com.myriad.client.LuoxiaPoseClient.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ShuoViewPayload.TYPE,
                (payload,context)->com.myriad.client.firstperson.ShuoView.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(FlameSkillViewPayload.TYPE,
                (payload,context)->com.myriad.client.firstperson.FlameDaoView.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ThunderSkillViewPayload.TYPE,
                (payload,context)->com.myriad.client.firstperson.ThunderSwordView.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(NightWindupPayload.TYPE,
                (payload, context) -> com.myriad.client.NightWindupSound.receive(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> com.myriad.client.NightWindupSound.reset());
        ClientPlayNetworking.registerGlobalReceiver(NightSkillViewPayload.TYPE,
                (payload,context)->com.myriad.client.firstperson.NightDaggerView.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(SkillChargePayload.TYPE,
                (payload, context) -> com.myriad.client.SkillChargeClient.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(SpectatorTargetPayload.TYPE,
                (payload, context) -> com.myriad.client.RespawnSpectatorView.update(
                payload.active(), payload.targetName()));
        ClientPlayNetworking.registerGlobalReceiver(KillAnnouncementPayload.TYPE,
                (payload, context) -> com.myriad.client.KillAnnouncementOverlay.show(
                payload.killerId(), payload.killerName(), payload.victimId(),
                payload.victimName(), payload.multiKill()));
        ClientPlayNetworking.registerGlobalReceiver(MatchTabPayload.TYPE,
                (payload, context) -> com.myriad.client.MatchTabOverlay.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(TeamSignalsPayload.TYPE,
                (payload, context) -> com.myriad.client.TeamSignalClient.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(DeathRecapPayload.TYPE,
                (payload, context) -> com.myriad.client.DeathRecapStore.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ControlStatusPayload.TYPE,
                (payload, context) -> com.myriad.client.ControlStatusOverlay.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(EnemyHealthPayload.TYPE,
                (payload, context) -> com.myriad.client.EnemyHealthBars.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(RewardFloatPayload.TYPE,
                (payload, context) -> com.myriad.client.RewardFloater.add(payload));
        ClientPlayNetworking.registerGlobalReceiver(TowerLockPayload.TYPE,
                (payload, context) -> com.myriad.client.ControlStatusOverlay.towerLock(payload));
        ClientPlayNetworking.registerGlobalReceiver(TowerAlertPayload.TYPE,
                (payload, context) -> com.myriad.client.TowerAlertOverlay.show(
                payload.laneId(), payload.tier()));
        ClientPlayNetworking.registerGlobalReceiver(PlayerDataPayload.TYPE,
                (payload, context) -> {
            ClientDataHolder.update(payload);
            Myriad.LOGGER.debug("[sync] client received: lv={} char={} faction={} phase={}",
                    payload.level(), payload.characterId(), payload.factionId(), payload.phaseId());
        });
        ClientPlayNetworking.registerGlobalReceiver(VeinStatusPayload.TYPE,
                (payload, context) -> com.myriad.client.HudOverlay.setVeinStatus(payload));
        ClientPlayNetworking.registerGlobalReceiver(FogVisibilityPayload.TYPE,
                (payload, context) -> com.myriad.client.FogOfWarClient.update(
                payload.enabled(), payload.visiblePlayerIds()));
        ClientPlayNetworking.registerGlobalReceiver(DamageFloatPayload.TYPE,
                (payload, context) -> com.myriad.client.DamageFloater.add(
                payload.dmgType(), payload.amount(), payload.crit(),
                payload.x(), payload.y(), payload.z()));
        ClientPlayNetworking.registerGlobalReceiver(HurtFeedbackPayload.TYPE,
                (payload, context) -> com.myriad.client.HudOverlay.showHurtFeedback(
                payload.healthDamage(), payload.maxHealth(), payload.playSound()));
        ClientPlayNetworking.registerGlobalReceiver(TargetHealthPayload.TYPE,
                (payload, context) -> com.myriad.client.HudOverlay.setTarget(
                payload.entityId(), payload.physicalDefense(), payload.magicDefense()));
        ClientPlayNetworking.registerGlobalReceiver(JiarenHpPayload.TYPE,
                (payload, context) -> com.myriad.client.HudOverlay.setJiarenHp(
                payload.entityId(), payload.hp(), payload.maxHp()));
        ClientPlayNetworking.registerGlobalReceiver(MatchStartedPayload.TYPE,
                (payload, context) -> {
            com.myriad.client.CombatStatsPanel.reset();
            com.myriad.client.MatchResultsStore.clear();
            com.myriad.client.TowerAlertOverlay.clear();
            com.myriad.client.KillAnnouncementOverlay.clear();
            com.myriad.client.RespawnSpectatorView.reset();
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatTelemetryPayload.TYPE,
                (payload, context) -> com.myriad.client.CombatStatsPanel.record(
                payload.kind(), payload.amount(), payload.source()));
        ClientPlayNetworking.registerGlobalReceiver(MatchResultsPayload.TYPE,
                (payload, context) -> com.myriad.client.MatchResultsStore.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(OpenPickScreenPayload.TYPE,
                (payload, context) -> {
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                        new com.myriad.client.gui.PickScreen());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenJiarenSettingsPayload.TYPE,
                (payload, context) -> net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                new com.myriad.client.gui.JiarenSettingsScreen(payload.entityId(),
                        payload.physicalDefense(), payload.magicDefense())));
        ClientPlayNetworking.registerGlobalReceiver(PickStatePayload.TYPE,
                (payload, context) -> {
            ClientDataHolder.updatePickState(payload);
            Myriad.LOGGER.info("[pick-state] recv st={} myF={} cur={} curF={} dl={} cd={} taken={}",
                    payload.state(), payload.myFaction(), payload.currentName(),
                    payload.currentFaction(), payload.deadlineTick(),
                    payload.countdownTick(), payload.taken().keySet());
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (payload.state() == 1 && mc.gui.screen() instanceof com.myriad.client.gui.PickScreen) {
                mc.setScreenAndShow(null);
            }
            if (payload.state() == 0 && mc.player != null && mc.gui.screen() == null) {
                mc.setScreenAndShow(new com.myriad.client.gui.PickScreen());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(HealFloatPayload.TYPE,
                (payload, context) -> com.myriad.client.HealFloater.add(payload.amount()));
        ClientPlayNetworking.registerGlobalReceiver(DashPayload.TYPE,
                (payload, context) -> com.myriad.client.DashController.start(
                payload.dirX(), payload.dirY(), payload.dirZ(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(AbilityVfxPayload.TYPE,
                (payload, context) -> {
            com.myriad.client.firstperson.FlameDaoView.receive(payload);
            com.myriad.client.particle.AbilityMeshParticle.receive(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(AbilityMotionPayload.TYPE,
                (payload, context) -> com.myriad.client.DashController.startBounded(payload));
        ClientPlayNetworking.registerGlobalReceiver(ParabolicMotionPayload.TYPE,
                (payload, context) -> com.myriad.client.DashController.startParabola(
                payload.deltaX(), payload.deltaY(), payload.deltaZ(), payload.arcHeight(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(StarPullMotionPayload.TYPE,
                (payload, context) -> com.myriad.client.StarPullController.start(
                payload.centerX(), payload.centerZ(), payload.strength(),
                payload.velocityBlend(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(MagicBulletCooldownPayload.TYPE,
                (payload, context) -> com.myriad.client.ClientDataHolder.startMagicBulletCooldown(
                payload.cooldownTicks(), payload.totalTicks()));
        ClientPlayNetworking.registerGlobalReceiver(ZhouTianChannelPayload.TYPE,
                (payload, context) -> com.myriad.client.ZhoutianChannelView.start(
                payload.durationTicks()));
        ClientPlayNetworking.registerGlobalReceiver(FrostViewPayload.TYPE,
                (payload, context) -> com.myriad.client.HanzeFrostView.start(
                payload.durationTicks()));
        ClientPlayNetworking.registerGlobalReceiver(ProgressAction.Receipt.TYPE, (receipt, context) -> {
            var gate = com.myriad.client.gui.FirstGroupScreen.progressGate();
            if (gate.receive(receipt.sequence()) && receipt.applied())
                com.myriad.client.UiFeedback.receive(1);
        });
        ClientPlayNetworking.registerGlobalReceiver(UiFeedbackPayload.TYPE,
                (payload, context) -> com.myriad.client.UiFeedback.receive(payload.kind()));
        ClientPlayNetworking.registerGlobalReceiver(TeamListPayload.TYPE,
                (payload, context) -> com.myriad.client.gui.TeamManageScreen.receiveTeamList(payload.teams(), payload.arenaId(), payload.duelAvailable()));
        ClientPlayNetworking.registerGlobalReceiver(GameSettingsPayload.TYPE,
                (payload, context) -> com.myriad.client.gui.SettingsScreen.receiveSettings(payload.settings()));
        ClientPlayNetworking.registerGlobalReceiver(MapFacilitySnapshotPayload.TYPE,
                (payload, context) -> com.myriad.client.gui.MapFacilityEditorScreen.receive(
                payload.entries()));
        ClientPlayNetworking.registerGlobalReceiver(MinimapSnapshotPayload.TYPE,
                (payload, context) -> com.myriad.client.MinimapClientState.update(payload));
    }
}
