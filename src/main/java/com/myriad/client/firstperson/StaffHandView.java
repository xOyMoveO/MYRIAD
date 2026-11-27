package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import static com.myriad.character.ability.StaffHandCues.*;

public final class StaffHandView {
    private static final StaffHandMotion FROST_MOTION = new StaffHandMotion(true);
    private static final StaffHandMotion HERB_MOTION = new StaffHandMotion(false);
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static int theme;
    private static long newest = Long.MIN_VALUE;
    private static ModelPart slimArms, wideArms;

    private StaffHandView() {}

    private static int available(Minecraft mc) {
        if (mc.player == null || mc.level == null || !ClientDataHolder.modEnabled()
                || !mc.player.isAlive() || mc.player.isSpectator() || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson() || RespawnSpectatorView.isActive()
                || ClientDataHolder.syncedCounter("hero_respawn") > 0
                || mc.gui.screen() != null || mc.player.isUsingItem()) return 0;
        int value = switch (ClientDataHolder.characterId()) {
            case "hanze" -> FROST;
            case "qingluo" -> HERB;
            case null, default -> 0;
        };
        return matches(mc.player.getMainHandItem(), value) ? value : 0;
    }

    private static boolean matches(ItemStack item, int value) {
        return value == FROST && item.is(ModItems.SHUANGBING.get())
                || value == HERB && item.is(ModItems.QINGLUO_STAFF.get());
    }

    private static StaffHandMotion motion() { return theme == FROST ? FROST_MOTION : HERB_MOTION; }

    private static void attach(Minecraft mc, int value) {
        if (owner != mc.player || world != mc.level || theme != value) {
            reset();
            owner = mc.player;
            world = mc.level;
            theme = value;
        }
    }

    public static void reset() {
        FROST_MOTION.reset();
        HERB_MOTION.reset();
        owner = null;
        world = null;
        theme = 0;
        newest = Long.MIN_VALUE;
    }

    public static void clearOwner(int entity) {
        if (owner != null && owner.getId() == entity) reset();
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        int value = available(mc);
        if (value == 0) { if (owner != null) reset(); return; }
        attach(mc, value);
        if (mc.gameMode != null && mc.gameMode.isDestroying() || ClientDataHolder.buffTicks("g1_stun") > 0)
            motion().abort(mc.player.tickCount);
    }

    public static void receive(AbilityVfxPayload cue) {
        var mc = Minecraft.getInstance();
        int value = available(mc);
        if (value == 0 || value != cue.theme() || mc.player.getId() != cue.owner()) return;
        attach(mc, value);
        if (cue.started() < newest) return;
        newest = cue.started();
        motion().cue(cue.variant(), (int) cue.angle(), mc.player.tickCount, cue.life(),
                cue.radius(), ClientDataHolder.magicBulletCooldownMax());
    }

    public static boolean render(HandRenderContext event) {
        var mc = Minecraft.getInstance();
        int value = available(mc);
        if (event.isCanceled() || value == 0 || mc.gameMode != null && mc.gameMode.isDestroying()) return false;
        attach(mc, value);
        var pose = motion().pose(mc.player.tickCount + event.getPartialTick());
        boolean offHand = pose.palm().y > -1.20 && mc.player.getOffhandItem().isEmpty();
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (offHand) event.setCanceled(true);
            return offHand;
        }
        if (!matches(event.getItemStack(), theme)) return false;
        boolean mirrored = mc.player.getMainArm() == HumanoidArm.LEFT;
        float equip = event.getEquipProgress();
        drawArm(event, mc.player, pose, mirrored, false, equip);
        var stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(StaffHandRig.item(pose, theme == FROST, mirrored, equip));
            ClientItemRenderer.submit(mc.player, event.getItemStack(), ItemDisplayContext.NONE,
                    stack, event.getSubmitNodeCollector(), event.getPackedLight());
        } finally {
            stack.popPose();
        }
        if (offHand) drawArm(event, mc.player, pose, mirrored, true, equip);
        event.setCanceled(true);
        return true;
    }

    private static void drawArm(HandRenderContext event, LocalPlayer player, StaffHandMotion.Pose pose,
                                boolean mirrored, boolean offHand, float equip) {
        if (FirstPersonArmVisibility.hidePlayerArms()) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        if (slim && slimArms == null) slimArms = NightDaggerArmModel.bake(true);
        if (!slim && wideArms == null) wideArms = NightDaggerArmModel.bake(false);
        boolean left = mirrored != offHand;
        String side = left ? "left" : "right";
        var arm = (slim ? slimArms : wideArms).getChild(side + "_arm");
        arm.getChild(side + "_sleeve").visible = player.isModelPartShown(
                left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        var stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(StaffHandRig.arm(pose, mirrored, offHand, slim, equip));
            var texture = RenderTypes.entityTranslucent(player.getSkin().body().texturePath());
            event.getSubmitNodeCollector().submitModelPart(arm, stack, texture,
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, null);
        } finally {
            stack.popPose();
        }
    }
}
