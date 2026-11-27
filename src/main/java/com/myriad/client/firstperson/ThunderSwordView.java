package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.MeleeAttackBuffer;
import com.myriad.client.platform.ClientItemRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.HitResult;
import com.myriad.client.platform.AttackInput;
import com.myriad.client.platform.HandRenderContext;

/** Tianxing-only own-camera cosmetics; vanilla input and authoritative damage remain untouched. */
public final class ThunderSwordView {
    private static final TianxingSwordPrototype MOTION = new TianxingSwordPrototype();
    private static final ThunderSkillMotion SKILL=new ThunderSkillMotion();
    private static boolean aiming;
    private static double aimStart;
    private static Object owner,world;
    private static final float DEG = (float)(Math.PI / 180);
    private static float lastYaw, lastPitch, yawLag, pitchLag, oldYawLag, oldPitchLag;
    private static double lastVertical, bounce, oldBounce, bounceVelocity;
    private static Matrix4f skillEntry;
    private static double skillEntryTime;
    private static Object equippedOwner, equippedWorld;
    private static int equippedSlot = -1;
    private static HumanoidArm equippedArm;
    private static boolean equipPending;

    private static void observeEquipment(Minecraft mc) {
        var player = mc.player;
        boolean held = player != null && player.getMainHandItem().is(ModItems.LEITINGJIAN.get());
        int slot = held ? player.getInventory().getSelectedSlot() : -1;
        var arm = held ? player.getMainArm() : null;
        if (equippedOwner != player || equippedWorld != mc.level || equippedSlot != slot || equippedArm != arm) {
            equippedOwner = player; equippedWorld = mc.level; equippedSlot = slot; equippedArm = arm;
            equipPending = held;
        }
    }

    private ThunderSwordView(){}
    private static boolean enabled(Minecraft mc){
        return mc.player!=null&&mc.level!=null&&ThunderSwordRig.allowed(ClientDataHolder.modEnabled(),
                ClientDataHolder.characterId(),mc.player.isAlive()&&!mc.player.isSpectator()
                &&!RespawnSpectatorView.isActive()&&ClientDataHolder.syncedCounter("hero_respawn")<=0
                &&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson(),
                mc.player.getMainHandItem().is(ModItems.LEITINGJIAN.get()));
    }
    private static boolean busy(Minecraft mc){
        return mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())
                ||ClientDataHolder.placementType()!=0||ClientDataHolder.isChanneling()||ClientDataHolder.buffTicks("g1_stun")>0;
    }
    public static void reset(){MOTION.reset();SKILL.reset();aiming=false;owner=null;world=null;skillEntry=null;}
    public static void receive(com.myriad.network.ThunderSkillViewPayload cue){
        var mc=Minecraft.getInstance();
        if(!cue.valid()||!enabled(mc)||mc.gui.screen()!=null)return;
        checkOwner(mc);double elapsed=Math.max(0,mc.level.getGameTime()-cue.started());
        double now = frameTime(mc);
        var startPose = displayedSword(mc, now);
        if (SKILL.accept(cue, mc.player.tickCount, elapsed, ThunderSwordMotion.IDLE)) {
            skillEntry = startPose;
            skillEntryTime = now;
            MOTION.reset();
        }
    }
    private static void checkOwner(Minecraft mc){
        if (owner == mc.player && world == mc.level) return;
        reset(); owner = mc.player; world = mc.level;
        lastYaw = mc.player.getYRot(); lastPitch = mc.player.getXRot();
        oldYawLag = yawLag = oldPitchLag = pitchLag = 0;
        oldBounce = bounce = bounceVelocity = lastVertical = 0;
    }
    public static void tick(){
        var mc=Minecraft.getInstance();
        observeEquipment(mc);
        if(!enabled(mc)||mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())){reset();return;}
        checkOwner(mc);
        updateInertia(mc);
        if(ClientDataHolder.buffTicks("g1_stun")>0){SKILL.interrupt(mc.player.tickCount);MOTION.reset();}
        boolean next=ClientDataHolder.placementType()==106&&ClientDataHolder.buffTicks("g1_stun")<=0;
        if(next&&!aiming)aimStart=mc.player.tickCount;
        if(!next&&aiming&&!SKILL.active(mc.player.tickCount)&&SKILL.recovery(mc.player.tickCount)==null)
            SKILL.settle(ThunderSkillMotion.aim(mc.player.tickCount-aimStart),mc.player.tickCount);
        aiming=next;
        if(busy(mc))MOTION.reset();
    }
    /** Observe an unconsumed, recovered vanilla attack; never cancel or send an attack request. */
    public static void onAttack(AttackInput event){
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||event.isCanceled()||!event.shouldSwingHand()||!enabled(mc)||busy(mc)
                ||mc.player.getAttackStrengthScale(.5f)<1)return;
        if(!event.isMeleeAssist()&&mc.hitResult!=null&&mc.hitResult.getType()==HitResult.Type.BLOCK)return;
        checkOwner(mc);if(SKILL.active(mc.player.tickCount))return;MOTION.attack(frameTime(mc),mc.player.getCurrentItemAttackStrengthDelay());
    }
    public static boolean render(HandRenderContext event){
        var mc=Minecraft.getInstance();
        observeEquipment(mc);
        if(!enabled(mc)){reset();return false;}
        if(event.isCanceled())return false;
        if(mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())){reset();return false;}
        checkOwner(mc);
        double now=mc.player.tickCount+event.getPartialTick();
        var skill = skill(now);
        if (event.getHand() != InteractionHand.MAIN_HAND) return false;
        if (!event.getItemStack().is(ModItems.LEITINGJIAN.get())) return false;
        boolean gesture = skill.action() >= 0;
        if (equipPending) {
            if (!gesture && !MOTION.active(now)) MOTION.equip(now, event.getEquipProgress());
            equipPending = false;
        }
        boolean left = mc.player.getMainArm() == HumanoidArm.LEFT;
        var stack = event.getPoseStack();
        stack.pushPose();
        try {
            // The hand pass already has the camera view rotation bound in RenderSystem.
            // Restore its inverse and author our own motion on the main sword's private stack.
            var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            stack.setIdentity();
            stack.mulPose(new Matrix4f(camera.viewRotationMatrix).invert());
            if (!gesture) addMicroMotion(stack, mc, now, event.getPartialTick(), left);
            stack.pushPose();
            try {
                stack.mulPose(displayedSword(mc, now));
                if (!gesture || skill.showBlade()) {
                    ClientItemRenderer.submit(mc.player, event.getItemStack(), ItemDisplayContext.NONE,
                            stack, event.getSubmitNodeCollector(), event.getPackedLight());
                    if (gesture) ThunderSkillHandRenderer.submit(event, stack, ThunderSkillHandVfx.blade(skill, now));
                }
            } finally {
                stack.popPose();
            }
            if (gesture) ThunderSkillHandRenderer.submit(event, stack, ThunderSkillHandVfx.camera(skill, now, left));
            else if (!busy(mc)) AttackTrailRenderer.submit(event, ThunderBasicTrail.prototype(MOTION, now, left));
        } finally {
            stack.popPose();
        }
        event.setCanceled(true);
        return true;
    }

    private static ThunderSkillMotion.Frame skill(double now) {
        var frame = SKILL.sample(now);
        return frame.action() < 0 && aiming ? ThunderSkillMotion.aim(now - aimStart) : frame;
    }

    private static Matrix4f displayedSword(Minecraft mc, double now) {
        boolean left = mc.player.getMainArm() == HumanoidArm.LEFT;
        var frame = skill(now);
        var result = frame.action() < 0 ? MOTION.sword(now, left) : ThunderSkillRig.sword(frame, left);
        var recovery = SKILL.recovery(now);
        if (recovery != null) {
            result = ThunderSkillRig.recover(ThunderSkillRig.sword(recovery.from(), left), result, recovery.amount(now));
        }
        if (skillEntry != null && now >= skillEntryTime && now < skillEntryTime + 1.4) {
            result = ThunderSkillRig.recover(skillEntry, result, (float)((now - skillEntryTime) / 1.4));
        }
        return result;
    }

    private static double frameTime(Minecraft mc) {
        return mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static void updateInertia(Minecraft mc) {
        var player = mc.player;
        oldYawLag = yawLag;
        oldPitchLag = pitchLag;
        float turn = Mth.wrapDegrees(player.getYRot() - lastYaw);
        float tilt = player.getXRot() - lastPitch;
        yawLag = .57f * yawLag + .43f * Math.clamp(turn, -16, 16);
        pitchLag = .57f * pitchLag + .43f * Math.clamp(tilt, -14, 14);
        lastYaw = player.getYRot(); lastPitch = player.getXRot();
        double vertical = player.getDeltaMovement().y;
        oldBounce = bounce;
        if (player.onGround() && lastVertical < -.11) bounceVelocity -= Math.min(.045, -lastVertical * .1);
        // Exact damped spring evolution over one simulation tick.
        double frequency = .72, damping = .48;
        double c = Math.cos(frequency), s = Math.sin(frequency), decay = Math.exp(-damping);
        double derivative = (bounceVelocity + damping * bounce) / frequency;
        double next = decay * (bounce*c + derivative*s);
        bounceVelocity = decay * frequency * (derivative*c - bounce*s) - damping*next;
        bounce = next;
        lastVertical = vertical;
    }

    private static void addMicroMotion(PoseStack stack, Minecraft mc, double now, float partial, boolean left) {
        double weight = MOTION.microWeight(now);
        double side = left ? -1 : 1;
        var walk = mc.player.walkAnimation;
        double step = walk.position(partial) * .78;
        double walkWeight = mc.options.bobView().get() ? Math.min(1, walk.speed(partial)) : 0;
        double yaw = Mth.lerp(partial, oldYawLag, yawLag);
        double pitch = Mth.lerp(partial, oldPitchLag, pitchLag);
        double breath = Math.sin(now * .087);
        double dx = side * .027 * walkWeight * Math.sin(step);
        double dy = .008*breath + .022 * walkWeight * Math.sin(2*step) + Mth.lerp(partial, oldBounce, bounce);
        float prep = MeleeAttackBuffer.preparation(mc, partial);
        stack.translate(dx * weight, dy * weight + .028 * prep, .007 * breath * weight + .026 * prep);
        stack.mulPose(new Matrix4f().rotationXYZ(
                (float)(DEG * (.55*breath - .28*pitch) * weight),
                (float)(DEG * .39*yaw * weight),
                (float)(side * DEG * (.92*walkWeight*Math.sin(step) + 4*prep) * weight)));
    }
}
