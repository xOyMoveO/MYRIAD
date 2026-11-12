package com.myriad.game;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 灵源汲取与运输流程（策划案2.1节）。
 * <p>
 * 旧版玄霄宗灵源运输流程；对称占点模式下不再启用；
 * 成功后获得"携带灵源"状态（减速 + 发光），运回己方传送阵交付计分。
 */
public final class SpiritFlow {
    private SpiritFlow() {}

    /** 玩家右键灵源核心 */
    public static void startChannel(ServerPlayer player) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            return;
        }
        if (data.getCarrying() > 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.channel_already_carrying"));
            return;
        }
        if (data.isChanneling()) {
            player.sendSystemMessage(Component.translatable("message.myriad.channel_stop"));
            data.setChanneling(false);
            return;
        }
        data.setChanneling(true);
        data.setChannelTicks(0);
        data.setChannelStart(player.position());
        // 缓存灵源核心位置（墨尘被动/技能定位用；站在核心旁汲取=核心就在这里）
        com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.cacheCorePosition(
                (net.minecraft.server.level.ServerLevel) player.level(), player.position());
        player.sendSystemMessage(Component.translatable("message.myriad.channel_start"));
        PlayerDataProvider.syncToClient(player);
    }

    /** 每 tick 推进汲取/维护携带状态 */
    public static void tick(ServerPlayer player) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            return;
        }

        if (data.isChanneling()) {
            // 受击打断（0.5秒内受击）
            long gameTime = player.level().getGameTime();
            if (data.getLastHurtGameTime() >= 0 && gameTime - data.getLastHurtGameTime() <= 10) {
                cancelChannel(player, data, "message.myriad.channel_interrupt_damage");
                return;
            }
            // 移动打断
            if (player.distanceToSqr(data.getChannelStart()) > GameRules.CHANNEL_MOVE_TOLERANCE) {
                cancelChannel(player, data, "message.myriad.channel_interrupt_move");
                return;
            }
            data.setChannelTicks(data.getChannelTicks() + 1);
            if (data.getChannelTicks() % 10 == 0 && player.level() instanceof ServerLevel level) {
                Vec3 pos = player.position();
                level.sendParticles(ParticleTypes.ENCHANT, pos.x(), pos.y() + 1.2, pos.z(),
                        5, 0.3, 0.3, 0.3, 0.02);
            }
            // 进度刷新同步（0.25 秒一次，驱动客户端读条）
            if (data.getChannelTicks() % 5 == 0) {
                PlayerDataProvider.syncToClient(player);
            }
            if (data.getChannelTicks() >= com.myriad.game.GameRules.CHANNEL_TICKS) {
                data.setChanneling(false);
                data.setCarrying(GameRules.SPIRIT_PER_EXTRACT);
                player.sendSystemMessage(Component.translatable("message.myriad.channel_done", GameRules.SPIRIT_PER_EXTRACT));
                PlayerDataProvider.syncToClient(player);
            }
        }

        // 携带状态维护：减速 + 发光
        if (data.getCarrying() > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GameRules.CARRYING_EFFECT_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    GameRules.CARRYING_EFFECT_TICKS, GameRules.CARRYING_SLOWNESS_LEVEL));
        }
    }

    /** 受击事件：记录受击并立即打断汲取 */
    public static void onDamage(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            return;
        }
        data.setLastHurtGameTime(player.level().getGameTime());
        if (data.isChanneling()) {
            cancelChannel(player, data, "message.myriad.channel_interrupt_damage");
        }
    }

    /** 玩家在己方传送阵交付灵源 */
    public static void deliver(ServerPlayer player) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null || data.getCarrying() <= 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.deliver_nothing"));
            return;
        }
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING) {
            player.sendSystemMessage(Component.translatable("message.myriad.match_not_playing"));
            return;
        }
        if (manager.factionOf(player) != Faction.XUANXIAO) {
            player.sendSystemMessage(Component.translatable("message.myriad.deliver_wrong_team"));
            return;
        }
        int amount = data.getCarrying();
        data.setCarrying(0);
        player.removeEffect(MobEffects.GLOWING);
        player.removeEffect(MobEffects.SLOWNESS);
        // 缓存己方基地传送门位置（墨尘 W 传送阵的传送目标点；交付点=传送门）
        if (player.level() instanceof ServerLevel level) {
            com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.cachePortalPosition(level, player.position());
        }
        // 对称夺点模式中，灵源核心不再提供阵营分数；保留该流程仅为旧地图兼容。
        // 交付特效：金色光柱 + 音效
        if (player.level() instanceof ServerLevel level) {
            Vec3 pos = player.position();
            level.sendParticles(ParticleTypes.END_ROD, pos.x(), pos.y() + 1.5, pos.z(),
                    40, 0.5, 1.5, 0.5, 0.1);
            level.playSound(null, pos.x(), pos.y(), pos.z(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        PlayerDataProvider.syncToClient(player);
    }

    private static void cancelChannel(ServerPlayer player, PlayerData data, String messageKey) {
        data.setChanneling(false);
        data.setChannelTicks(0);
        player.sendSystemMessage(Component.translatable(messageKey));
        PlayerDataProvider.syncToClient(player);
    }
}
