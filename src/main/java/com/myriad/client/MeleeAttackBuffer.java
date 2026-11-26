package com.myriad.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.character.CharacterType;
import com.myriad.client.platform.KeyInput;
import com.myriad.combat.InputFailure;
import com.myriad.combat.MeleeInputWindow;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Only a physical press may reserve an attack. The reservation never contains a target. */
public final class MeleeAttackBuffer {
    private static final int WINDOW_TICKS = Math.clamp(Integer.getInteger("myriad.meleeBufferTicks", 2), 0, 2);
    private static final MeleeInputWindow window = new MeleeInputWindow();
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static ItemStack weapon;
    private static int slot;
    private static String role;
    private static boolean freshPress;
    private static boolean suppressMining;

    private MeleeAttackBuffer() {}

    public static void binding(Minecraft mc, KeyInput input) {
        if (!input.matches(mc.options.keyAttack)) return;
        if (input.getAction() == InputConstants.PRESS) {
            freshPress = mc.gui.screen() == null;
            suppressMining = false;
        } else if (input.getAction() == InputConstants.RELEASE) suppressMining = false;
    }

    public static boolean consumePress() {
        boolean result = freshPress;
        freshPress = false;
        return result;
    }

    private static InputFailure blocked() {
        if (ClientDataHolder.buffTicks("g1_stun") > 0) return InputFailure.STUN;
        if (ClientDataHolder.isChanneling() || SkillChargeClient.active()) return InputFailure.CHANNEL;
        if (ClientDataHolder.placementType() != 0 || ClientDataHolder.buffTicks("g1_shuo_sheathing") > 0)
            return InputFailure.LINK;
        return null;
    }

    private static boolean eligible(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
                || mc.getCameraEntity() != player || !ClientDataHolder.canFight()
                || !VanillaMeleeAttack.usesVanilla(CharacterType.byId(ClientDataHolder.characterId()))
                || !player.isAlive() || player.isSpectator() || player.isHandsBusy() || player.isUsingItem()
                || mc.gameMode.isDestroying()) return false;
        var held = player.getMainHandItem();
        return PlayerDataProvider.isInitialWeapon(held) && held.isItemEnabled(mc.level.enabledFeatures())
                && !held.has(DataComponents.PIERCING_WEAPON) && !player.cannotAttackWithItem(held, 0);
    }

    /** The caller is the native melee path. Let a genuine block press keep its mining behavior. */
    public static boolean intercept(Minecraft mc, boolean nativeReady, boolean physicalPress) {
        InputFailure reason = blocked();
        if (reason != null) {
            clearPending();
            if (ClientDataHolder.canFight()) InputFeedback.show(reason);
            return true;
        }
        if (mc.player.getAttackStrengthScale(.5f) >= 1) {
            clearPending();
            return false;
        }
        boolean inCombat = ClientDataHolder.canFight();
        boolean target = inCombat && nativeReady && eligible(mc) && MeleeAssistInput.hasAttackTarget(mc);
        if (inCombat && !target && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK)
            return false;
        if (target && physicalPress) {
            for (int remaining = 1; remaining <= WINDOW_TICKS; remaining++) {
                if (mc.player.getAttackStrengthScale(.5f + remaining) < 1) continue;
                if (window.offer(mc.player.tickCount, remaining, WINDOW_TICKS)) {
                    owner = mc.player;
                    world = mc.level;
                    slot = owner.getInventory().getSelectedSlot();
                    weapon = owner.getMainHandItem();
                    role = ClientDataHolder.characterId();
                }
                if (window.pending()) suppressMining = mc.options.keyAttack.isDown();
                break;
            }
        }
        if (inCombat && !window.pending()) InputFeedback.show(InputFailure.RECOVERING);
        return true;
    }

    /** Called every client tick, including screens where vanilla skips its key handler. */
    public static void validate(Minecraft mc) {
        if (window.pending() && (!eligible(mc) || owner != mc.player || world != mc.level
                || owner.getInventory().getSelectedSlot() != slot || owner.getMainHandItem() != weapon
                || !role.equals(ClientDataHolder.characterId()) || blocked() != null)) clearPending();
        if (!mc.options.keyAttack.isDown()) suppressMining = false;
        if (mc.gui.screen() != null || mc.player == null) freshPress = false;
    }

    public static boolean suppressMining() { return suppressMining; }

    public static boolean ready(Minecraft mc) {
        freshPress = false;
        validate(mc);
        if (!window.pending()) return false;
        boolean fire = window.consume(owner.tickCount, owner.getAttackStrengthScale(.5f) >= 1);
        if (!window.pending()) clearPending();
        return fire;
    }

    private static void clearPending() {
        window.clear(); owner = null; world = null; weapon = null; role = null;
    }

    public static void reset() {
        clearPending(); freshPress = false; suppressMining = false;
    }
}
