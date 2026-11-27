package com.myriad.client.firstperson;

import com.myriad.character.skills.rivet.RivetVfx;
import com.myriad.character.skills.rivet.RivetWeaponMotion;
import com.myriad.character.skills.rivet.RivetWeaponRig;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.MeleeAttackBuffer;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

import static com.myriad.character.ability.AbilityVfxGeometry.BURST;

/** Local first-person impact hammer. Existing skill cues retain their own authored poses. */
public final class RivetWeaponView {
    private static final RivetHammerPrototype HAMMER = new RivetHammerPrototype();
    private static final RivetHammerContact CONTACT = new RivetHammerContact();
    private static final RivetWeaponMotion SKILLS = new RivetWeaponMotion();
    private static Object owner, world;
    private static int slot = -1;
    private static HumanoidArm arm;
    private static boolean equipPending, cameraPlayed;
    private static double equippedAt;
    private static float equipDrop;

    private RivetWeaponView() {}

    private static boolean enabled(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isSpectator()
                && ClientDataHolder.modEnabled() && "rivet".equals(ClientDataHolder.characterId())
                && !RespawnSpectatorView.isActive() && ClientDataHolder.syncedCounter("hero_respawn") <= 0
                && mc.player.getMainHandItem().is(ModItems.RIVET_HAMMER.get())
                && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    private static boolean usable(Minecraft mc) {
        return mc.gui.screen() == null && !mc.player.isUsingItem()
                && (mc.gameMode == null || !mc.gameMode.isDestroying());
    }

    public static void reset() {
        cancelStroke();
        CONTACT.reset();
        SKILLS.reset();
        owner = world = null;
        arm = null; slot = -1; equipDrop = 0; equipPending = false;
    }

    private static void cancelStroke() {
        HAMMER.reset(); CONTACT.cancel(); RivetImpactCamera.reset(); cameraPlayed = false;
    }

    private static void checkOwner(Minecraft mc) {
        int selected = mc.player.getInventory().getSelectedSlot();
        var hand = mc.player.getMainArm();
        if (owner == mc.player && world == mc.level && slot == selected && arm == hand) return;
        if (owner == mc.player && world == mc.level) {
            cancelStroke();
            SKILLS.reset();
        } else reset();
        owner = mc.player; world = mc.level; slot = selected; arm = hand; equipPending = true;
    }

    private static double now(Minecraft mc) {
        return mc.level.getGameTime() + (double)mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static void attack(Minecraft mc) {
        if (!enabled(mc) || !usable(mc) || ClientDataHolder.buffTicks("g1_stun") > 0
                || ClientDataHolder.isChanneling() || ClientDataHolder.placementType() != 0) return;
        checkOwner(mc);
        double time = now(mc);
        double oldTime = CONTACT.animationTime(time);
        SKILLS.reset();
        HAMMER.attack(time, mc.player.getCurrentItemAttackStrengthDelay(), oldTime);
        CONTACT.start(time, HAMMER.contactOpen(), HAMMER.contactClose());
        RivetImpactCamera.reset(); cameraPlayed = false;
        equipDrop = 0; equipPending = false;
    }

    public static void receive(AbilityVfxPayload p) {
        var mc = Minecraft.getInstance();
        if (!enabled(mc) || p.owner() != mc.player.getId()) return;
        checkOwner(mc);
        double time = now(mc);
        if (RivetVfx.basicContact(p)) {
            if (usable(mc) && ClientDataHolder.buffTicks("g1_stun") <= 0) CONTACT.receive(p.started(), time);
            return;
        }
        // Passive rhythm owns its world effects. It cannot replace its triggering basic stroke.
        if (p.theme() == RivetVfx.THEME && p.kind() == BURST && p.variant() == 1
                && p.angle() == RivetVfx.RHYTHM_HIT && HAMMER.active(CONTACT.animationTime(time))) return;
        if (SKILLS.accept(p,time)) {
            cancelStroke(); equipDrop = 0; equipPending = false;
        }
    }

    /** The world and hand passes can sample the same confirmed event in either order. */
    public static void updateImpactCamera(double time) {
        var mc = Minecraft.getInstance();
        if (!enabled(mc)) { if (owner != null) reset(); return; }
        checkOwner(mc);
        if (!usable(mc) || ClientDataHolder.buffTicks("g1_stun") > 0
                || ClientDataHolder.isChanneling() || ClientDataHolder.placementType() != 0) {
            if (HAMMER.cut() >= 0) cancelStroke();
            return;
        }
        if (HAMMER.cut() != 2 || cameraPlayed) return;
        double age = CONTACT.impactAge(time);
        if (age >= 0) {
            RivetImpactCamera.confirmedSmash(time - age);
            cameraPlayed = true;
        }
    }

    public static boolean render(HandRenderContext event) {
        var mc = Minecraft.getInstance();
        if (!enabled(mc)) { if (owner != null) reset(); return false; }
        checkOwner(mc);
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().is(ModItems.RIVET_HAMMER.get())) return false;
        if (!usable(mc)) { cancelStroke(); return false; }
        double time = mc.level.getGameTime() + (double)event.getPartialTick();
        updateImpactCamera(time);
        boolean left = arm == HumanoidArm.LEFT;
        if (equipPending) {
            equippedAt = time; equipDrop = event.getEquipProgress(); equipPending = false;
        }
        double weaponTime = CONTACT.animationTime(time);
        var pose = HAMMER.item(weaponTime,left,CONTACT.vibration(time));
        var skillPose = SKILLS.sample(time,0,ClientDataHolder.buffTicks("g1_rivet_rhythm") > 0 && !HAMMER.active(weaponTime));
        if (!skillPose.equals(RivetWeaponMotion.IDLE)) {
            // Express existing skill offsets relative to the original rest frame.
            pose.mul(RivetWeaponRig.item(RivetWeaponMotion.IDLE,left).invert())
                    .mul(RivetWeaponRig.item(skillPose,left));
        }
        var stack = event.getPoseStack();
        stack.pushPose();
        try {
            var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            stack.setIdentity();
            stack.mulPose(new Matrix4f(camera.viewRotationMatrix).invert());
            var motion = microMotion(mc, time, event.getPartialTick(), HAMMER.microWeight(weaponTime), left);
            motion.translate(0,-.46f*(float)Math.max(0,equipDrop-.4*(time-equippedAt)),0);
            var item = motion.mul(pose);
            stack.pushPose();
            try {
                stack.mulPose(item);
                ClientItemRenderer.submit(mc.player,event.getItemStack(),ItemDisplayContext.NONE,stack,
                        event.getSubmitNodeCollector(),event.getPackedLight());
            } finally { stack.popPose(); }
            double age = CONTACT.impactAge(time);
            if (age >= 0) MeleeBladeLight.sparks(event,RivetHammerPrototype.strikingFace(item),left?-1:1,age);
        } finally { stack.popPose(); }
        event.setCanceled(true);
        return true;
    }

    private static Matrix4f microMotion(Minecraft mc,double time,float partial,double weight,boolean left) {
        var player = mc.player;
        var walk = player.walkAnimation;
        double step = .76*walk.position(partial);
        double walkAmount = mc.options.bobView().get() ? Math.min(1,walk.speed(partial)) : 0;
        double breathing = Math.sin(time*.073);
        double yaw = Math.clamp(Mth.wrapDegrees(player.getViewYRot(partial) - Mth.rotLerp(partial,player.yBobO,player.yBob)), -15,15);
        double pitch = Math.clamp(player.getViewXRot(partial)-Mth.lerp(partial,player.xBobO,player.xBob),-12,12);
        double rise = Math.clamp(player.getDeltaMovement().y,-.5,.5);
        double side = left ? -1 : 1;
        double prep = MeleeAttackBuffer.preparation(mc,partial)*weight;
        return new Matrix4f()
                .translate((float)(side*.030*walkAmount*Math.sin(step)*weight),
                        (float)((.006*breathing+.024*walkAmount*Math.sin(2*step)-.045*rise)*weight+.032*prep),
                        (float)((.009*breathing-.027*rise)*weight+.025*prep))
                .rotateXYZ((float)Math.toRadians((.45*breathing-.32*pitch)*weight-3.2*prep),
                        (float)Math.toRadians(.40*yaw*weight),(float)Math.toRadians(side*1.1*walkAmount*Math.sin(step)*weight));
    }
}
