package com.myriad.item;

import com.myriad.character.CharacterType;
import com.myriad.character.skills.lige.LiGeSkills;
import com.myriad.game.ModState;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 离歌专属弓。操作与原版弓一致：右键按住拉弓，松开射击；不消耗原版箭矢。
 * 模组攻速是倍率（1.0 = 100%），因此有效蓄力时间为实际蓄力时间乘攻速。
 */
public final class LiGeBowItem extends BowItem {
    public LiGeBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!canUse(player)) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || !canUse(player)) return false;
        int heldTicks = Math.max(0, getUseDuration(stack, living) - timeLeft);
        double attackSpeed = Math.max(0.1D, player.getAttributeValue(ModAttributes.ATTACK_SPEED));
        int effectiveTicks = Math.max(0, (int)Math.floor(heldTicks * attackSpeed));
        // Only full draw can fire; the retired 15..19 tick weak release no longer shoots.
        if (effectiveTicks < 20) return false;
        float power = BowItem.getPowerForTime(effectiveTicks);
        if (!(player instanceof ServerPlayer serverPlayer)) return true;

        if (!LiGeSkills.fireBasicArrow(serverPlayer, power, 1.0F)) return false;
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.85F, 1.0F / (serverPlayer.getRandom().nextFloat() * 0.35F + 1.0F) + 0.25F);
        PlayerData data = PlayerDataProvider.get(serverPlayer);
        if (data != null) data.setLastCombatGameTime(level.getGameTime());
        PlayerDataProvider.syncToClient(serverPlayer);
        return true;
    }

    private static boolean canUse(Player player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        // 客户端附件不是角色状态的权威来源；先允许本地进入原版拉弓动画，服务端会再次完整校验。
        if (player.level().isClientSide()) return true;
        if (!ModState.isEnabled()) return false;
        PlayerData data = PlayerDataProvider.get(player);
        return data != null && CharacterType.byId(data.getCharacterId()) == CharacterType.LIGE;
    }
}
