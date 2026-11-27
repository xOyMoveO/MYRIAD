package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.luoxia.LuoxiaGeometry;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.FogOfWarClient;
import com.myriad.client.LuoxiaPoseClient;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import com.myriad.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The grip is the model origin. Both views attach the arrow nock to the deformed string serving. */
public final class LuoxiaBowView {
    private static final ItemStack[] BOWS = new ItemStack[4];
    private static Object equipOwner, equipWorld;
    private static int equipSlot = -1;
    private static HumanoidArm equipArm;
    private static double equipAt;
    private static float initialDrop;
    private static final Map<String,List<Quad>> ARROWS = new HashMap<>();
    private static final List<Quad> FEATHER_LIGHT = List.of(
            new Quad(new Vec3(-.024, .22, .09), new Vec3(.048, .31, .09),
                    new Vec3(.048, .33, .09), new Vec3(-.032, .24, .09), 0xFFCA7C, .80f, 0, false, .03f, .16f, .10f, .24f),
            new Quad(new Vec3(.048, .31, .09), new Vec3(.09, .39, .09),
                    new Vec3(.048, .33, .09), new Vec3(.04, .31, .09), 0xFFECCA, .85f, 0, false, .03f, .16f, .10f, .24f));
    private static final Identifier ATLAS = Identifier.parse("myriad:textures/particle/vfx/luoxia_arrow_atlas.png");
    private static final float DEG = (float)Math.PI / 180f;
    private static final List<Quad> READY_FERRULE = List.of(
            facet(.99, 1.50, 0, 0x643F70),
            facet(.75, 1.18, .025, 0xE4AD62),
            facet(.35, .72, .050, 0x865B88),
            facet(.13, .37, .075, 0xFFF1B7));

    // A small gilded purple fitting. The UV is an opaque pearly texel in the existing arrow atlas.
    private static Quad facet(double width, double height, double z, int tint) {
        return new Quad(new Vec3(0,-height,z), new Vec3(width,0,z), new Vec3(0,height,z),
                new Vec3(-width,0,z),tint,1,0,false,.42578125f,.42578125f,.5390625f,.5390625f);
    }

    private static void readyFerrule(LuoxiaBowPrototype.Frame frame, PoseStack stack, SubmitNodeCollector collector) {
        if (frame.readyPulse() < 0) return;
        var anchor = frame.readyAnchor();
        var nock = frame.bow().transformPosition(new Vector3f(frame.serving(),0,0));
        float scale = .0105f*(1+.42f*frame.readyPulse());
        stack.pushPose();
        try {
            stack.translate(anchor.x,anchor.y,anchor.z+.004f);
            stack.rotate(Axis.ZP.rotation((float)Math.atan2(nock.x-anchor.x,anchor.y-nock.y)));
            stack.scale(scale,scale,scale);
            submitMesh(READY_FERRULE,stack,collector,0xF000F0);
        } finally { stack.popPose(); }
    }

    private LuoxiaBowView() {}

    private static boolean usesLockedAim(LuoxiaBowPosePayload cue) {
        return cue != null && (cue.phase() == Phase.LOCK || cue.skill() == 7 && cue.phase() == Phase.RELEASE);
    }

    private static ItemStack model(int stage) {
        if (BOWS[stage] == null) {
            var item = ModItems.GUIXIA_BOW.get().getDefaultInstance();
            String name = stage == 0 ? "guixia_bow" : "guixia_bow_pulling_" + (stage - 1);
            item.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("myriad", name + "_held"));
            BOWS[stage] = item;
        }
        return BOWS[stage];
    }

    public static boolean first(HandRenderContext event) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !ClientDataHolder.modEnabled() || !"luoxia".equals(ClientDataHolder.characterId())
                || !p.isAlive() || p.isSpectator() || !p.getMainHandItem().is(ModItems.GUIXIA_BOW.get())) {
            equipOwner = null;
            return false;
        }
        if (mc.getCameraEntity() != p || mc.gui.screen() != null || !mc.options.getCameraType().isFirstPerson()
                || event.isCanceled()) return false;
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (!p.getOffhandItem().isEmpty()) return false;
            event.setCanceled(true);
            return true;
        }
        if (!event.getItemStack().is(ModItems.GUIXIA_BOW.get())) return false;
        float partial = event.getPartialTick();
        double now = mc.level.getGameTime() + (double)partial;
        var hand = p.getMainArm();
        int slot = p.getInventory().getSelectedSlot();
        if (equipOwner != p || equipWorld != mc.level || equipSlot != slot || equipArm != hand) {
            equipOwner = p; equipWorld = mc.level; equipSlot = slot; equipArm = hand;
            equipAt = now; initialDrop = event.getEquipProgress();
        }
        double step = p.walkAnimation.position(partial)*.78;
        double walk = mc.options.bobView().get() ? Math.min(1,p.walkAnimation.speed(partial)) : 0;
        float yaw = p.getViewYRot(partial), pitch = p.getViewXRot(partial);
        double yawLag = Math.clamp(net.minecraft.util.Mth.wrapDegrees(yaw-net.minecraft.util.Mth.rotLerp(partial,p.yBobO,p.yBob)),-15,15);
        double pitchLag = Math.clamp(pitch-net.minecraft.util.Mth.lerp(partial,p.xBobO,p.xBob),-12,12);
        var frame = LuoxiaPoseClient.FIRST_PERSON.sample(now,hand==HumanoidArm.LEFT,yaw,pitch,
                LuoxiaPoseClient.stepping(p),step,walk,yawLag,pitchLag);
        if (frame.loaded() || frame.releaseAge() >= 0) initialDrop = 0;
        var stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.setIdentity();
            stack.mulPose(new Matrix4f(mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix).invert());
            stack.translate(0,-.38f*(float)Math.max(0,initialDrop-.4*(now-equipAt)),0);
            stack.pushPose();
            try {
                stack.mulPose(frame.bow());
                LuoxiaBowDeformation.submit(p,frame.draw(),frame.stringOffset(),stack,event.getSubmitNodeCollector(),event.getPackedLight());
                var cue = LuoxiaPoseClient.pose(p);
                if (LuoxiaPoseClient.chained(p) && (cue == null || cue.skill() != 1))
                    submitMesh(FEATHER_LIGHT,stack,event.getSubmitNodeCollector(),0xF000F0);
            } finally { stack.popPose(); }
            for (int i = 0; i < frame.arrows(); i++) {
                stack.pushPose();
                try {
                    stack.mulPose(frame.arrow(i));
                    submitMesh(ARROWS.computeIfAbsent(frame.mesh(),LuoxiaGeometry::nockedArrow),stack,
                            event.getSubmitNodeCollector(),event.getPackedLight());
                } finally { stack.popPose(); }
            }
            readyFerrule(frame,stack,event.getSubmitNodeCollector());
        } finally { stack.popPose(); }
        event.setCanceled(true);
        return true;
    }

    private static LivingEntity avatar(ArmedEntityRenderState state) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !ClientDataHolder.modEnabled() || !(state instanceof AvatarRenderState a)
                || state.isInvisible || a.isSpectator || !state.getMainHandItemStack().is(ModItems.GUIXIA_BOW.get())
                || !(mc.level.getEntity(a.id) instanceof LivingEntity p) || !p.isAlive()) return null;
        if (p == mc.player && (mc.gui.screen() != null || !"luoxia".equals(ClientDataHolder.characterId()))) return null;
        return p;
    }

    public static void pose(HumanoidModel<?> model, HumanoidRenderState state) {
        LivingEntity p = avatar(state);
        if (p == null) return;
        var cue = LuoxiaPoseClient.pose(p);
        boolean step = LuoxiaPoseClient.stepping(p);
        if (cue == null && !step) return;
        float partial = state.ageInTicks - (float)Math.floor(state.ageInTicks);
        var m = LuoxiaBowMotion.sample(cue, LuoxiaPoseClient.recoil(p, partial), step);
        boolean left = state.mainArm == HumanoidArm.LEFT;
        float side = left ? -1 : 1;
        var front = left ? model.leftArm : model.rightArm;
        var rear = left ? model.rightArm : model.leftArm;
        boolean aimed = cue != null;
        boolean locked = usesLockedAim(cue);
        float aimYaw = locked ? (cue.yaw() - state.bodyRot) * DEG : model.head.yRot;
        float pitch = locked ? cue.pitch() * DEG : model.head.xRot;
        model.body.yRot += side * (aimed ? .16f + .20f * m.pull() : .08f);
        model.body.xRot += m.crouch() * .13f;
        if (aimed) {
            front.xRot = -1.48f + pitch + m.recoil() * .13f;
            front.yRot = aimYaw - side * .13f;
            front.zRot = side * m.roll() * .004f;
            if (p.getOffhandItem().isEmpty()) {
                rear.xRot = -1.5f + pitch + .35f * m.pull();
                rear.yRot = aimYaw + side * (.66f + .38f * m.pull() + .20f * m.recoil());
                rear.zRot = -side * (.12f + m.pull() * .22f);
            }
        }
        if (step) {
            float direction = LuoxiaPoseClient.stepDirection(p) * DEG;
            model.body.zRot -= .16f * (float)Math.sin(direction);
            model.body.xRot += .12f * (float)Math.cos(direction);
        }
        if (step || m.crouch() > 0) {
            model.rightLeg.xRot -= m.crouch() * .24f;
            model.leftLeg.xRot += m.crouch() * .22f;
        }
    }

    public static boolean held(ArmedModel<ArmedEntityRenderState> model, ArmedEntityRenderState state,
            ItemStack item, HumanoidArm arm, PoseStack stack, SubmitNodeCollector collector, int light) {
        LivingEntity p = avatar(state);
        if (p == null || arm != state.mainArm || !item.is(ModItems.GUIXIA_BOW.get())) return false;
        var mc = Minecraft.getInstance();
        if (p != mc.player && !FogOfWarClient.shouldRenderPlayer(p.getId())) return true;
        var cue = LuoxiaPoseClient.pose(p);
        if (cue == null && !LuoxiaPoseClient.stepping(p) && !LuoxiaPoseClient.chained(p)) return false;
        var avatar = (AvatarRenderState)state;
        float partial = state.ageInTicks - (float)Math.floor(state.ageInTicks);
        var m = LuoxiaBowMotion.sample(cue, LuoxiaPoseClient.recoil(p, partial), LuoxiaPoseClient.stepping(p));
        // Extract only the palm position. Aiming is expressed in the avatar's body frame.
        var palm = new PoseStack();
        model.translateToHand(state, arm, palm);
        var grip = palm.last().pose().transformPosition(new Vector3f(arm == HumanoidArm.RIGHT ? -.06f : .06f, .57f, 0));
        boolean lock = usesLockedAim(cue);
        float yaw = lock ? cue.yaw() : p.getYRot(), pitch = lock ? cue.pitch() : p.getXRot();
        stack.pushPose();
        try {
            stack.translate(grip.x, grip.y, grip.z);
            stack.rotate(Axis.YP.rotationDegrees(net.minecraft.util.Mth.wrapDegrees(yaw - avatar.bodyRot)));
            stack.rotate(Axis.XP.rotationDegrees(pitch));
            stack.rotate(Axis.ZP.rotationDegrees((arm == HumanoidArm.LEFT ? -1 : 1) * m.roll()));
            stack.rotate(Axis.YP.rotationDegrees(-90));
            stack.rotate(Axis.XP.rotationDegrees(180));
            stack.scale(.80f, .80f, .80f);
            weapon(p, m, stack, collector, light, false);
        } finally { stack.popPose(); }
        return true;
    }

    private static void weapon(LivingEntity p, LuoxiaBowMotion motion, PoseStack stack, SubmitNodeCollector collector, int light, boolean firstPerson) {
        stack.pushPose();
        try {
            stack.scale(motion.bowWidth(), 1, 1);
            ClientItemRenderer.submit(p, model(motion.stage()), ItemDisplayContext.NONE, stack, collector, light);
        } finally { stack.popPose(); }
        if (LuoxiaPoseClient.chained(p) && motion.skill() != 1) {
            submitMesh(FEATHER_LIGHT, stack, collector, 0xF000F0);
        }
        if (!motion.loaded()) return;
        for (int i = 0; i < motion.arrows(); i++) {
            stack.pushPose();
            try {
                stack.translate(motion.nock(), 0, 0);
                // The nock stays on the serving and the shaft passes above the offset shelf.
                float shelf = p.getMainArm() == HumanoidArm.LEFT ? -.058f : .058f;
                stack.rotate(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(shelf, motion.nock()))
                        + (motion.arrows() == 3 ? (i - 1) * 8 : 0)));
                // More of the shaft remains forward of the first-person grip at full draw.
                stack.scale(firstPerson ? 1.69f : 1.18f, firstPerson ? .61f : .68f, firstPerson ? .61f : .68f);
                submitMesh(ARROWS.computeIfAbsent(motion.mesh(), LuoxiaGeometry::nockedArrow), stack, collector, light);
            } finally { stack.popPose(); }
        }
    }

    private static void submitMesh(List<Quad> mesh, PoseStack stack, SubmitNodeCollector collector, int light) {
        collector.submitCustomGeometry(stack, RenderTypes.entityTranslucent(ATLAS), (pose, buffer) -> {
            for (var q : mesh) {
                var n = q.b().subtract(q.a()).cross(q.c().subtract(q.a())).normalize();
                vertex(buffer, pose, q.a(), q.startU(), q.endV(), q.color(), n, light);
                vertex(buffer, pose, q.b(), q.endU(), q.endV(), q.color(), n, light);
                vertex(buffer, pose, q.c(), q.endU(), q.startV(), q.color(), n, light);
                vertex(buffer, pose, q.d(), q.startU(), q.startV(), q.color(), n, light);
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int tint, Vec3 normal, int light) {
        buffer.addVertex(pose.pose(), (float)p.x, (float)p.y, (float)p.z).setColor(0xFF000000 | tint).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
    }
}
