package com.myriad.combat;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.game.ModState;
import com.myriad.game.VisionSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import java.util.UUID;
import java.util.WeakHashMap;

/** Vanilla handles picking, swing and immediate attack. Only validate game rules
 * and retain pre-reset strength for the existing mod-attribute damage pipeline. */
public final class VanillaMeleeAttack {
    private record Snapshot(UUID target,long tick,float strength){}
    private static final WeakHashMap<ServerPlayer,Snapshot> STRENGTH=new WeakHashMap<>();
    private record Pending(int dueTick, ResourceKey<Level> dimension, int slot, ItemStack weapon,
                           PlayerData ownerData, String role) {}
    private static final WeakHashMap<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private VanillaMeleeAttack(){}
    /** Gameplay attack routing, independent of any custom animation system. */
    public static boolean usesVanilla(CharacterType role){
        return role != null && switch(role){
            case XUANCHEN, HANZE, QINGLUO, YUNSHENG, YIKE, LUOXIA -> false;
            default -> true;
        };
    }

    private static boolean canAct(ServerPlayer player, PlayerData data) {
        if (!ModState.isEnabled() || !usesVanilla(CharacterType.byId(data.getCharacterId()))
                || !com.myriad.game.TrainingGround.canFight(player) || !player.isAlive() || player.isSpectator()
                || !PlayerDataProvider.isInitialWeapon(player.getMainHandItem())) return false;
        InputFailure reason = null;
        if (data.getBuff("g1_stun") > 0) reason = InputFailure.STUN;
        else if (AbilityRuntime.channeling(player) || com.myriad.character.ability.SkillChargeSystem.active(player))
            reason = InputFailure.CHANNEL;
        else if (!BasicAttackSystem.canAttackNow(player, data)) reason = InputFailure.LINK;
        if (reason == null) return true;
        reason.send(player);
        return false;
    }

    private static boolean fullyRecovered(ServerPlayer player) {
        if (player.getAttackStrengthScale(.5f) >= 1) return true;
        InputFailure.RECOVERING.send(player);
        return false;
    }

    private static boolean canSubmitIntent(ServerPlayer player) {
        var weapon = player.getMainHandItem();
        return player.connection.hasClientLoaded() && !player.hasDisconnected() && !player.isRemoved()
                && player.getCamera() == player && player.containerMenu == player.inventoryMenu
                && !player.isUsingItem() && weapon.isItemEnabled(player.level().enabledFeatures())
                && !weapon.has(DataComponents.PIERCING_WEAPON) && !player.cannotAttackWithItem(weapon, 5)
                && canAct(player, PlayerDataProvider.get(player));
    }

    /** Intent only: recompute the selection from authoritative player state. */
    public static void tryAssist(ServerPlayer player) {
        if (!BasicAttackAssist.enabled() || !canSubmitIntent(player) || !fullyRecovered(player)) return;
        LivingEntity target = MeleeAssistTargeting.find(player, living -> BasicAttackAssist.canAssist(player, living));
        strike(player, target);
    }

    /** Only the dedicated buffered intent receives grace. The target is never reserved. */
    public static void tryBuffered(ServerPlayer player) {
        if (PENDING.containsKey(player) || !canSubmitIntent(player)) return;
        if (player.getAttackStrengthScale(1.5f) < 1) {
            InputFailure.RECOVERING.send(player);
            return;
        }
        LivingEntity target = MeleeAssistTargeting.buffered(player);
        if (target == null || player.getAttackStrengthScale(.5f) >= 1) {
            strike(player, target);
            return;
        }
        var data = PlayerDataProvider.get(player);
        PENDING.put(player, new Pending(player.tickCount + 1, player.level().dimension(),
                player.getInventory().getSelectedSlot(), player.getMainHandItem(), data, data.getCharacterId()));
    }

    /** Observe native recovery only after this player's next simulation step and accepted position. */
    public static void tickBuffered(ServerPlayer player) {
        var pending = PENDING.get(player);
        if (pending == null || player.tickCount == pending.dueTick() - 1) return;
        PENDING.remove(player);
        var data = PlayerDataProvider.get(player);
        if (player.tickCount != pending.dueTick() || !player.level().dimension().equals(pending.dimension())
                || player.getInventory().getSelectedSlot() != pending.slot() || player.getMainHandItem() != pending.weapon()
                || data != pending.ownerData() || !pending.role().equals(data.getCharacterId())
                || !canSubmitIntent(player) || !fullyRecovered(player)) return;
        strike(player, MeleeAssistTargeting.buffered(player));
    }

    private static void strike(ServerPlayer player, LivingEntity target) {
        var weapon = player.getMainHandItem();
        // Fabric invokes AttackEntityCallback at Player.attack, including for this entry point.
        if (target == null) { InputFailure.ATTACK_TARGET.send(player); return; }
        player.resetLastActionTime();
        player.attack(target);
        if (player.getAttackStrengthScale(0) == 0) {
            player.swing(InteractionHand.MAIN_HAND, weapon.getAttackAnimation(), false);
        }
    }

    static boolean validTarget(ServerPlayer player, LivingEntity target) {
        return target.isAlive() && target != player && SkillTargets.hostile(player, target)
                && !(target instanceof net.minecraft.world.entity.player.Player victim && (victim.isCreative() || victim.isSpectator()))
                && player.isWithinEntityInteractionRange(target, 0) && player.hasLineOfSight(target)
                && VisionSystem.canSee(player, target) && !target.isInvisibleTo(player);
    }

    public static boolean prepare(ServerPlayer player,Entity raw){
        var data=PlayerDataProvider.get(player);
        if(!canAct(player,data) || !fullyRecovered(player))return false;
        if(!(raw instanceof LivingEntity target)||!validTarget(player, target)){
            InputFailure.ATTACK_TARGET.send(player);
            return false;
        }
        PENDING.remove(player);
        // Player.onAttack() resets strength BEFORE hurtOrSimulate in Minecraft 26.2.
        STRENGTH.put(player,new Snapshot(target.getUUID(),player.level().getGameTime(),player.getAttackStrengthScale(.5f)));
        com.myriad.character.skills.shuo.ShuoCombat.basic(player);
        com.myriad.character.skills.keheng.KehengBasic.swing(player);
        com.myriad.character.skills.yingxiao.rework.NightBasicVisuals.swing(player);
        com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.basic(player,data.getCharacterId());
        com.myriad.character.ability.WarSpiritHandCues.basic(player,data.getCharacterId(),
                (int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
        return true;
    }
    public static float consumeStrength(ServerPlayer player,LivingEntity target){
        Snapshot snapshot=STRENGTH.remove(player);
        return snapshot!=null&&snapshot.tick==player.level().getGameTime()&&snapshot.target.equals(target.getUUID())
                ?snapshot.strength:player.getAttackStrengthScale(.5f);
    }
    /** Only fully charged clicks are accepted; successful attacks never use a weak multiplier. */
    public static float damageScale(float strength){
        return 1;
    }
    public static void clear(UUID player){STRENGTH.keySet().removeIf(p->p.getUUID().equals(player));
        PENDING.keySet().removeIf(p -> p.getUUID().equals(player));
        com.myriad.character.skills.yingxiao.rework.NightBasicVisuals.clear(player);}

    public static void cancelBuffered(ServerPlayer player) { PENDING.remove(player); }
}
