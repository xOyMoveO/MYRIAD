package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Night Shadow's LOCAL first-person arm. Does not touch shared player models or third-person poses. */
public final class NightDaggerView {
    private static final NightDaggerMotion MOTION = new NightDaggerMotion();
    private static final NightSkillMotion SKILL = new NightSkillMotion();
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("myriad","yingxiao_shadow_dagger");
    private static Object owner, world;
    private static ModelPart normalRoot, slimRoot;
    private static ItemStack renderDagger = ItemStack.EMPTY;
    private NightDaggerView() { }

    private static boolean enabled(Minecraft mc) {
        return mc.player != null && mc.level != null && ClientDataHolder.modEnabled()
                && "yingxiao".equals(ClientDataHolder.characterId())
                && mc.player.isAlive() && !mc.player.isSpectator() && !RespawnSpectatorView.isActive()
                && ClientDataHolder.syncedCounter("hero_respawn") <= 0
                && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && mc.player.getMainHandItem().is(ModItems.YINGCI.get());
    }
    public static void reset() {
        MOTION.reset(); SKILL.reset();owner=null; world=null; renderDagger=ItemStack.EMPTY;
    }
    /** Only a server-confirmed owner cue can start a skill gesture. Failed targeting does not play it. */
    public static void receive(com.myriad.network.NightSkillViewPayload cue){
        var mc=Minecraft.getInstance();
        if(!cue.valid()||!enabled(mc)||mc.gui.screen()!=null)return;
        checkOwner(mc);
        double elapsed=Math.max(0,mc.level.getGameTime()-cue.started());
        if(SKILL.accept(cue,mc.player.tickCount,elapsed,MOTION.sample(mc.player.tickCount)))MOTION.reset();
    }
    public static void tick() {
        Minecraft mc=Minecraft.getInstance();
        if (!enabled(mc) || mc.gui.screen()!=null) { reset(); return; }
        checkOwner(mc);
        if(ClientDataHolder.placementType()!=0||ClientDataHolder.isChanneling()||ClientDataHolder.buffTicks("g1_stun")>0)MOTION.reset();
    }
    private static void checkOwner(Minecraft mc) {
        if (owner!=mc.player || world!=mc.level) {
            reset(); owner=mc.player; world=mc.level;
        }
    }
    /** LOWEST priority, after skill-input consumers; never cancels or changes vanilla attack input. */
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc=Minecraft.getInstance();
        if (!event.isAttack() || event.isCanceled() || !event.shouldSwingHand()
                || !enabled(mc) || mc.gui.screen()!=null || mc.player.isUsingItem()
                || ClientDataHolder.placementType()!=0 || ClientDataHolder.isChanneling()
                || ClientDataHolder.buffTicks("g1_stun")>0 || mc.player.getAttackStrengthScale(.5f)<1) return;
        // Mining uses its own vanilla hand animation, not the combat chain.
        if (mc.hitResult!=null && mc.hitResult.getType()==HitResult.Type.BLOCK) return;
        checkOwner(mc);
        if(SKILL.active(mc.player.tickCount))return;
        MOTION.attack(mc.player.tickCount,mc.player.getCurrentItemAttackStrengthDelay());
    }
    public static boolean render(RenderHandEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if (!enabled(mc)) { reset(); return false; }
        if(event.isCanceled())return false;
        if(mc.gui.screen()!=null){MOTION.reset();return false;}
        checkOwner(mc);
        if (event.getHand()!=InteractionHand.MAIN_HAND) {
            if(SKILL.sample(mc.player.tickCount+event.getPartialTick()).showOff()){
                // The cosmetic two-hand gesture temporarily replaces offhand rendering, not its inventory stack.
                event.setCanceled(true);return true;
            }
            // Only one idle hand; never conceal a real offhand item such as a map.
            if (event.getItemStack().isEmpty()) { event.setCanceled(true); return true; }
            return false;
        }
        // Respect the actual render stack during item-switch transitions.
        if (!event.getItemStack().is(ModItems.YINGCI.get())) return false;
        if (mc.player.isUsingItem() || (mc.gameMode!=null && mc.gameMode.isDestroying())) {MOTION.reset();return false;}
        if(ClientDataHolder.placementType()!=0||ClientDataHolder.isChanneling()||ClientDataHolder.buffTicks("g1_stun")>0)MOTION.reset();
        checkOwner(mc);
        double now=mc.player.tickCount+event.getPartialTick();
        var skill=SKILL.sample(now);
        var pose=skill.action()>=0?skill.main():MOTION.sample(now);
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT;
        boolean slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        PoseStack stack=event.getPoseStack();
        stack.pushPose();
        try {
            // One wrist matrix drives BOTH the fist and the knife. Nothing is attached at the blade centre.
            stack.mulPose(NightDaggerRig.wrist(pose,left));
            submitArm(event,stack,mc,slim,left);
            stack.pushPose();
            try {
                // Source grip [8,8,8] stays at the palm through the reverse-to-forward grip turn.
                stack.mulPose(NightDaggerRig.blade(left,pose.grip()));
                if (renderDagger.isEmpty()) {
                    renderDagger=new ItemStack(ModItems.YINGCI.get());
                    renderDagger.set(DataComponents.ITEM_MODEL,MODEL);
                }
                if(skill.action()<0||skill.showBlade()){
                    mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,renderDagger,
                            ItemDisplayContext.NONE,stack,event.getSubmitNodeCollector(),event.getPackedLight());
                    NightSkillHandRenderer.submit(event,stack,NightSkillHandVfx.blade(skill,now));
                }
            } finally { stack.popPose(); }
            stack.pushPose();
            try{stack.scale(.55f,.55f,.55f);NightSkillHandRenderer.submit(event,stack,NightSkillHandVfx.needle(skill));}
            finally{stack.popPose();}
        } finally { stack.popPose(); }
        if(skill.showOff()){
            stack.pushPose();try{
                stack.mulPose(NightDaggerRig.wrist(skill.off(),!left));
                submitArm(event,stack,mc,slim,!left);
            }finally{stack.popPose();}
        }
        NightSkillHandRenderer.submit(event,stack,NightSkillHandVfx.camera(skill,now,left));
        if(skill.action()<0)AttackTrailRenderer.submit(event,NightBasicTrail.mesh(MOTION,now,left));
        event.setCanceled(true);
        return true;
    }
    private static void submitArm(RenderHandEvent event,PoseStack stack,Minecraft mc,boolean slim,boolean left) {
        // Private baked parts: third-person rendering and the vanilla AvatarRenderer are never mutated.
        if (slim && slimRoot==null) slimRoot=NightDaggerArmModel.bake(true);
        if (!slim && normalRoot==null) normalRoot=NightDaggerArmModel.bake(false);
        ModelPart arm=(slim ? slimRoot : normalRoot).getChild(left ? "left_arm" : "right_arm");
        arm.resetPose(); arm.setPos(0,0,0); arm.setRotation(0,0,0); arm.visible=true;
        arm.getChild(left ? "left_sleeve" : "right_sleeve").visible=mc.player.isModelPartShown(
                left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        stack.pushPose();
        try {
            stack.mulPose(NightDaggerRig.arm(slim,left));
            event.getSubmitNodeCollector().submitModelPart(arm,stack,
                    RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),
                    event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        } finally { stack.popPose(); }
    }
}
