package com.myriad.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.client.platform.KeyInput;
import com.myriad.network.LuoxiaBowInputPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;

/** Attack and use share a single server gesture. The first legal press owns its release. */
public final class LuoxiaInput {
    private static final int ATTACK = 1, USE = 2;
    private static int down, owner, blocked, gesture;
    private static Object connection, world;
    private static boolean consumeUse;
    private LuoxiaInput() {}

    private static boolean available(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.isWindowActive() && mc.gui.screen() == null && !RespawnSpectatorView.isActive()
                && ClientDataHolder.canFight() && ClientDataHolder.modEnabled() && "luoxia".equals(ClientDataHolder.characterId())
                && ClientDataHolder.placementType() == 0 && !ClientDataHolder.isChanneling()
                && ClientDataHolder.buffTicks("g1_stun") == 0 && !LuoxiaPoseClient.exclusive(mc.player)
                && mc.player.getMainHandItem().is(ModItems.GUIXIA_BOW.get());
    }

    private static boolean usableTarget(Minecraft mc) {
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            var target = hit.getEntity();
            if (target instanceof net.minecraft.world.item.trading.Merchant) return true;
            return target instanceof net.minecraft.world.entity.decoration.ArmorStand && target.getTeam() != null
                    && com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.ARRAY_TEAM.equals(target.getTeam().getName());
        }
        if (mc.level != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var state = mc.level.getBlockState(pos);
            return state.getMenuProvider(mc.level, pos) != null
                    || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock;
        }
        return false;
    }

    public static void binding(Minecraft mc, KeyInput input) {
        int keys = (input.matches(mc.options.keyAttack) ? ATTACK : 0) | (input.matches(mc.options.keyUse) ? USE : 0);
        if (keys == 0 || connection != mc.getConnection() || connection == null) return;
        if (input.getAction() == InputConstants.PRESS) {
            blocked &= ~keys;
            press(mc, keys);
        } else if (input.getAction() == InputConstants.RELEASE) {
            release(mc, keys);
            blocked &= ~keys;
        }
    }

    private static void press(Minecraft mc, int keys) {
        int fresh = keys & ~down & ~blocked;
        down |= keys;
        if (fresh == 0 || !available(mc)) return;
        if (owner == 0 && (fresh & USE) != 0 && usableTarget(mc)) {
            consumeUse = false;
            return;
        }
        if ((fresh & USE) != 0) consumeUse = true;
        if (owner != 0) return;
        owner = (fresh & ATTACK) != 0 ? ATTACK : USE;
        gesture++;
        LuoxiaPoseClient.begin(gesture, true);
        send(LuoxiaBow.PRESS);
        mc.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static void release(Minecraft mc, int keys) {
        down &= ~keys;
        if ((owner & keys) != 0) finish(mc, !available(mc) || mc.level != world);
    }

    public static void menuOpened(Minecraft mc) {
        blocked |= down;
        if (owner != 0 && connection == mc.getConnection()) finish(mc, true);
    }

    public static boolean consumesUse() { return consumeUse; }
    public static void afterKeybinds() { if ((down & USE) == 0) consumeUse = false; }

    public static void tick(Minecraft mc) {
        LuoxiaPoseClient.tick(mc);
        int held = (mc.options.keyAttack.isDown() ? ATTACK : 0) | (mc.options.keyUse.isDown() ? USE : 0);
        if (com.myriad.client.platform.KeyInput.sameBinding(mc.options.keyAttack, mc.options.keyUse) && held != 0)
            held = ATTACK | USE;
        if (connection != mc.getConnection() || world != mc.level) {
            if (owner != 0 && connection == mc.getConnection()) finish(mc, true);
            if (connection != mc.getConnection()) gesture = 0;
            connection = mc.getConnection();
            world = mc.level;
            owner = 0;
            blocked = down = held;
            consumeUse = false;
        }
        if (connection == null) return;
        if (owner != 0 && !available(mc)) {
            blocked |= down;
            finish(mc, true);
        }
        release(mc, down & ~held);
        if (!available(mc)) { down = held; return; }
        press(mc, held & ~down);
    }

    private static void finish(Minecraft mc, boolean cancel) {
        owner = 0;
        send(cancel ? LuoxiaBow.CANCEL : LuoxiaBow.RELEASE);
        LuoxiaPoseClient.letGo(cancel);
        if (mc.player != null && mc.player.getUseItem().is(ModItems.GUIXIA_BOW.get())) mc.player.stopUsingItem();
    }

    private static void send(int action) { Packets.sendToServer(new LuoxiaBowInputPayload(action, gesture)); }
}
