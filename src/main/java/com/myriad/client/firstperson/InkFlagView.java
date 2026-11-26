package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;

/** First person only. Server cues confirm casts and the launch of a real magic bullet. */
public final class InkFlagView {
    private static final InkHandMotion MOTION = new InkHandMotion();
    private static Object owner, world;
    private static ModelPart wideArms, slimArms;
    private static long newest = Long.MIN_VALUE;

    private InkFlagView() {}

    private static boolean enabled(Minecraft mc) {
        return mc.player != null && mc.level != null && ClientDataHolder.modEnabled()
                && "xuanchen".equals(ClientDataHolder.characterId()) && mc.player.isAlive() && !mc.player.isSpectator()
                && !RespawnSpectatorView.isActive() && ClientDataHolder.syncedCounter("hero_respawn") <= 0
                && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && mc.player.getMainHandItem().is(ModItems.ZHENQI.get());
    }

    private static boolean vanillaPose(Minecraft mc) {
        return mc.gui.screen() != null || mc.player.isUsingItem() || mc.gameMode != null && mc.gameMode.isDestroying();
    }

    private static boolean busy(Minecraft mc) {
        return vanillaPose(mc) || ClientDataHolder.isChanneling() || ClientDataHolder.placementType() != 0
                || ClientDataHolder.buffTicks("g1_stun") > 0;
    }

    private static void checkOwner(Minecraft mc) {
        if (owner == mc.player && world == mc.level) return;
        reset();
        owner = mc.player;
        world = mc.level;
    }

    public static void reset() {
        MOTION.reset();
        newest = Long.MIN_VALUE;
        owner = null;
        world = null;
    }

    public static void clearOwner(int entity) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == entity) reset();
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || vanillaPose(mc)) {
            reset();
            return;
        }
        checkOwner(mc);
        if (ClientDataHolder.buffTicks("g1_stun") > 0) MOTION.abort(mc.player.tickCount);
    }

    public static void prepareBasic() {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || busy(mc) || ClientDataHolder.magicBulletCooldownNow() > 0) return;
        checkOwner(mc);
        MOTION.prepare(0, mc.player.tickCount, ClientDataHolder.magicBulletCooldownMax());
    }

    public static void prepareSkill(int slot) {
        var mc = Minecraft.getInstance();
        var data = ClientDataHolder.get();
        if (data == null || !enabled(mc) || busy(mc) || ClientDataHolder.cooldownTicks(slot) > 0) return;
        var resolved = FirstGroupSkills.resolve(CharacterType.XUANCHEN, slot, data.skillLevels());
        if (!(resolved instanceof AbilitySkill skill) || skill.index() > 3 || skill.index() < 2
                || data.mana() < skill.getManaCost() || data.skillLevels().getOrDefault(skill.getId(), 0) <= 0) return;
        checkOwner(mc);
        MOTION.prepare(skill.index(), mc.player.tickCount, ClientDataHolder.magicBulletCooldownMax());
    }

    public static void receive(AbilityVfxPayload cue) {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || cue.owner() != mc.player.getId() || vanillaPose(mc)) return;
        checkOwner(mc);
        if (cue.started() < newest) return;
        newest = cue.started();
        MOTION.cue(cue.variant(), (int)cue.angle(), mc.player.tickCount, cue.life(),
                cue.radius() / 16, ClientDataHolder.magicBulletCooldownMax());
    }

    public static boolean render(HandRenderContext event) {
        var mc = Minecraft.getInstance();
        if (event.isCanceled() || !enabled(mc) || vanillaPose(mc)) return false;
        checkOwner(mc);
        double now = mc.player.tickCount + event.getPartialTick();
        var pose = MOTION.pose(now);
        boolean mainLeft = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        boolean showSupport = pose.palm().y > -1.14 && mc.player.getOffhandItem().isEmpty();
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (showSupport) event.setCanceled(true);
            return showSupport;
        }
        if (!event.getItemStack().is(ModItems.ZHENQI.get())) return false;
        float equip = event.getEquipProgress();
        PoseStack stack = event.getPoseStack();
        drawArm(event, mc, pose, false, mainLeft, equip);
        stack.pushPose();
        try {
            stack.mulPose(HandedView.weapon(InkHandRig.flag(pose, equip),mainLeft));
            ClientItemRenderer.submit(mc.player, event.getItemStack(), ItemDisplayContext.NONE,
                    stack, event.getSubmitNodeCollector(), event.getPackedLight());
        } finally {
            stack.popPose();
        }
        if (showSupport) drawArm(event, mc, pose, true, mainLeft, equip);
        AttackTrailRenderer.submit(event, HandedView.effects(InkHandEffects.geometry(MOTION, now, equip),mainLeft));
        event.setCanceled(true);
        return true;
    }

    private static void drawArm(HandRenderContext event, Minecraft mc, InkHandMotion.Pose pose,
                                boolean off, boolean mainLeft, float equip) {
        boolean left = mainLeft != off;
        boolean slim = mc.player.getSkin().model() == PlayerModelType.SLIM;
        if (slim && slimArms == null) slimArms = NightDaggerArmModel.bake(true);
        if (!slim && wideArms == null) wideArms = NightDaggerArmModel.bake(false);
        String name = left ? "left" : "right";
        ModelPart arm = (slim ? slimArms : wideArms).getChild(name + "_arm");
        arm.getChild(name + "_sleeve").visible = mc.player.isModelPartShown(
                left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(HandedView.arm(InkHandRig.arm(pose, off, slim, equip),mainLeft));
            var texture = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
            event.getSubmitNodeCollector().submitModelPart(arm, stack, texture,
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, null);
        } finally {
            stack.popPose();
        }
    }
}
