package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.client.platform.AttackInput;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

/** The local Sulan hand pass, with vanilla skin meshes and the actual held item. */
public final class WindBladeView {
    private static final WindHandMotion MOTION = new WindHandMotion();
    private static Object owner, world;
    private static ModelPart wideArms, slimArms;
    private static long latestCue = Long.MIN_VALUE;

    private WindBladeView() {}

    private static boolean enabled(Minecraft mc) {
        return mc.player != null && mc.level != null && ClientDataHolder.modEnabled()
                && "sulan".equals(ClientDataHolder.characterId()) && mc.player.isAlive() && !mc.player.isSpectator()
                && !RespawnSpectatorView.isActive() && ClientDataHolder.syncedCounter("hero_respawn") <= 0
                && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && mc.player.getMainHandItem().is(ModItems.FENGREN.get());
    }

    private static boolean useVanillaPose(Minecraft mc) {
        return mc.gui.screen() != null || mc.player.isUsingItem()
                || mc.gameMode != null && mc.gameMode.isDestroying();
    }

    private static boolean busy(Minecraft mc) {
        return useVanillaPose(mc) || ClientDataHolder.isChanneling() || ClientDataHolder.placementType() != 0
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
        latestCue = Long.MIN_VALUE;
        owner = null;
        world = null;
    }

    public static void clearOwner(int entity) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == entity) reset();
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || useVanillaPose(mc)) {
            reset();
            return;
        }
        checkOwner(mc);
        if (ClientDataHolder.buffTicks("g1_stun") > 0) MOTION.interrupt(mc.player.tickCount);
    }

    public static void onAttack(AttackInput event) {
        var mc = Minecraft.getInstance();
        if (event.isCanceled() || !event.shouldSwingHand() || !enabled(mc) || busy(mc)
                || mc.player.getAttackStrengthScale(.5f) < 1
                || !event.isMeleeAssist() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        checkOwner(mc);
        MOTION.attack(mc.player.tickCount, mc.player.getCurrentItemAttackStrengthDelay());
    }

    /** Predict only a small hand windup while the existing cast request makes its round trip. */
    public static void prepare(int slot) {
        var mc = Minecraft.getInstance();
        var data = ClientDataHolder.get();
        if (data == null || !enabled(mc) || busy(mc) || ClientDataHolder.cooldownTicks(slot) > 0) return;
        var resolved = FirstGroupSkills.resolve(CharacterType.SULAN, slot, data.skillLevels());
        if (!(resolved instanceof AbilitySkill skill) || data.mana() < skill.getManaCost()
                || data.skillLevels().getOrDefault(skill.getId(), 0) <= 0) return;
        if (skill.index() == 3 && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY)) return;
        checkOwner(mc);
        MOTION.prepare(skill.index(), mc.player.tickCount);
    }

    public static void receive(AbilityVfxPayload cue) {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || cue.owner() != mc.player.getId() || useVanillaPose(mc)) return;
        checkOwner(mc);
        if (cue.started() < latestCue) return;
        latestCue = cue.started();
        Vec3 aim = cue.variant() == 3 ? markOffset(mc, cue.to().subtract(cue.from())) : Vec3.ZERO;
        MOTION.cue(cue.variant(), (int)cue.angle(), mc.player.tickCount, cue.life(), cue.radius() / 16, aim);
    }

    public static boolean render(HandRenderContext event) {
        var mc = Minecraft.getInstance();
        if (event.isCanceled() || !enabled(mc) || useVanillaPose(mc)) return false;
        checkOwner(mc);
        double now = mc.player.tickCount + event.getPartialTick();
        var hands = MOTION.poseAt(now);
        boolean left = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        boolean off = hands.free().y > -1.15 && mc.player.getOffhandItem().isEmpty();
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (off) event.setCanceled(true);
            return off;
        }
        if (!event.getItemStack().is(ModItems.FENGREN.get())) return false;

        PoseStack stack = event.getPoseStack();
        if (!MOTION.hasSkill(now) && MOTION.combo()>=0 && now<MOTION.attackStart()+MOTION.recovery())
            event.holdEquipped();
        float equip = event.getEquipProgress();
        Matrix4f weapon = WindHandRig.weapon(MOTION, now, equip);
        submitArm(event, mc, hands, false, left, equip, weapon);
        stack.pushPose();
        try {
            stack.mulPose(HandedView.weapon(weapon,left));
            ClientItemRenderer.submit(mc.player, event.getItemStack(), ItemDisplayContext.NONE,
                    stack, event.getSubmitNodeCollector(), event.getPackedLight());
        } finally {
            stack.popPose();
        }
        if (off) submitArm(event, mc, hands, true, left, equip, weapon);
        AttackTrailRenderer.submit(event, HandedView.effects(WindHandVfx.geometry(MOTION, now, equip),left));
        event.setCanceled(true);
        return true;
    }

    private static void submitArm(HandRenderContext event, Minecraft mc, WindHandMotion.Pose hands,
                                  boolean off, boolean mainLeft, float equip, Matrix4f weapon) {
        if (FirstPersonArmVisibility.hidePlayerArms()) return;
        boolean left=mainLeft!=off;
        boolean slim = mc.player.getSkin().model() == PlayerModelType.SLIM;
        if (slim && slimArms == null) slimArms = NightDaggerArmModel.bake(true);
        if (!slim && wideArms == null) wideArms = NightDaggerArmModel.bake(false);
        String side = left ? "left" : "right";
        ModelPart arm = (slim ? slimArms : wideArms).getChild(side + "_arm");
        arm.getChild(side + "_sleeve").visible = mc.player.isModelPartShown(
                left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        var type = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(HandedView.arm(WindHandRig.arm(hands, off, slim, equip, weapon),mainLeft));
            event.getSubmitNodeCollector().submitModelPart(arm, stack, type, event.getPackedLight(), OverlayTexture.NO_OVERLAY, null);
            if (off && hands.point() > .36) {
                int which=left?0:1;
                if (fingerModels[which] == null) fingerModels[which] = bakeFingers(left);
                stack.translate((left?1:-1)*(slim ? -.035f : .005f), 10f / 16, 0);
                stack.scale(1, (float)((hands.point() - .36) / .64), 1);
                event.getSubmitNodeCollector().submitModelPart(fingerModels[which], stack, type, event.getPackedLight(), OverlayTexture.NO_OVERLAY, null);
            }
        } finally {
            stack.popPose();
        }
    }

    private static final ModelPart[] fingerModels=new ModelPart[2];
    private static ModelPart bakeFingers(boolean left) {
        var faces = EnumSet.allOf(Direction.class);
        // Sample the free hand's skin; the two short extensions only appear in the mark pose.
        var index = new ModelPart.Cube(left?39:47, left?50:18, left?.12f:-.91f, -.14f, -.78f, .79f, 1.8f, 1.28f, 0, 0, 0, !left, 64, 64, faces);
        var middle = new ModelPart.Cube(left?40:48, left?50:18, left?1.04f:-1.83f, -.14f, -.78f, .79f, 1.6f, 1.28f, 0, 0, 0, !left, 64, 64, faces);
        return new ModelPart(List.of(index, middle), Map.of());
    }

    private static Vec3 markOffset(Minecraft mc, Vec3 aim) {
        Vec3 forward = mc.player.getLookAngle();
        double yaw = Math.toRadians(mc.player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(forward);
        return new Vec3(Math.clamp((mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?-1:1) * aim.dot(right) * .68, -.34, .34),
                Math.clamp(aim.dot(up) * .62, -.24, .22), 0);
    }
}
