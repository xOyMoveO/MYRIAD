package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Tianxing-only own-camera cosmetics; vanilla input and authoritative damage remain untouched. */
public final class ThunderSwordView {
    private static final ThunderSwordMotion MOTION=new ThunderSwordMotion();
    private static final ThunderSkillMotion SKILL=new ThunderSkillMotion();
    private static boolean aiming;
    private static double aimStart;
    private static Object owner,world;
    private static ModelPart wideRoot,slimRoot;
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
    public static void reset(){MOTION.reset();SKILL.reset();aiming=false;owner=null;world=null;}
    public static void receive(com.myriad.network.ThunderSkillViewPayload cue){
        var mc=Minecraft.getInstance();
        if(!cue.valid()||!enabled(mc)||mc.gui.screen()!=null)return;
        checkOwner(mc);double elapsed=Math.max(0,mc.level.getGameTime()-cue.started());
        if(SKILL.accept(cue,mc.player.tickCount,elapsed,MOTION.sample(mc.player.tickCount)))MOTION.reset();
    }
    private static void checkOwner(Minecraft mc){
        if(owner!=mc.player||world!=mc.level){reset();owner=mc.player;world=mc.level;}
    }
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(!enabled(mc)||mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())){reset();return;}
        checkOwner(mc);
        if(ClientDataHolder.buffTicks("g1_stun")>0){SKILL.interrupt();MOTION.reset();}
        boolean next=ClientDataHolder.placementType()==106&&ClientDataHolder.buffTicks("g1_stun")<=0;
        if(next&&!aiming)aimStart=mc.player.tickCount;aiming=next;
        if(busy(mc))MOTION.reset();
    }
    /** Observe an unconsumed, recovered vanilla attack; never cancel or send an attack request. */
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||event.isCanceled()||!event.shouldSwingHand()||!enabled(mc)||busy(mc)
                ||mc.player.getAttackStrengthScale(.5f)<1)return;
        if(mc.hitResult!=null&&mc.hitResult.getType()==HitResult.Type.BLOCK)return;
        checkOwner(mc);if(SKILL.active(mc.player.tickCount))return;MOTION.attack(mc.player.tickCount,mc.player.getCurrentItemAttackStrengthDelay());
    }
    public static boolean render(RenderHandEvent event){
        var mc=Minecraft.getInstance();
        if(!enabled(mc)){reset();return false;}
        if(event.isCanceled())return false;
        if(mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())){reset();return false;}
        checkOwner(mc);
        double now=mc.player.tickCount+event.getPartialTick();
        var skill=SKILL.sample(now);
        if(skill.action()<0&&aiming)skill=ThunderSkillMotion.aim(now-aimStart);
        if(event.getHand()!=InteractionHand.MAIN_HAND){if(skill.showOff()){event.setCanceled(true);return true;}return false;}
        if(!event.getItemStack().is(ModItems.LEITINGJIAN.get()))return false;
        // Maps, offhand use, mining and other vanilla use poses keep their ordinary rendering.
        if(busy(mc)){MOTION.reset();if(mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying()))return false;}
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT;
        boolean slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        var frame=MOTION.frame(now);boolean gesture=skill.action()>=0;
        PoseStack stack=event.getPoseStack();stack.pushPose();
        try{
            stack.mulPose(gesture?ThunderSkillRig.main(skill,left):ThunderSwordRig.wrist(frame.pose(),event.getEquipProgress(),left));
            submitArm(event,stack,mc,slim,left);
            stack.pushPose();try{
                stack.mulPose(gesture?ThunderSkillRig.blade(skill,left):ThunderSwordRig.blade(frame,left));
                // NONE prevents a second application of item display offsets after the palm attachment.
                if(!gesture||skill.showBlade())mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,event.getItemStack(),
                        ItemDisplayContext.NONE,stack,event.getSubmitNodeCollector(),event.getPackedLight());
                if(gesture&&skill.showBlade())ThunderSkillHandRenderer.submit(event,stack,ThunderSkillHandVfx.blade(skill,now));
            }finally{stack.popPose();}
        }finally{stack.popPose();}
        if(gesture&&skill.showOff()){
            stack.pushPose();try{stack.mulPose(ThunderSkillRig.off(skill,left));submitArm(event,stack,mc,slim,!left);}finally{stack.popPose();}
        }
        if(gesture)ThunderSkillHandRenderer.submit(event,stack,ThunderSkillHandVfx.camera(skill,now,left));
        if(!gesture&&!busy(mc))AttackTrailRenderer.submit(event,ThunderBasicTrail.mesh(MOTION,now,left,event.getEquipProgress()));
        event.setCanceled(true);return true;
    }
    private static void submitArm(RenderHandEvent event,PoseStack stack,Minecraft mc,boolean slim,boolean left){
        // Private copies of the tested vanilla skin mesh: never mutate shared third-person player parts.
        if(slim&&slimRoot==null)slimRoot=NightDaggerArmModel.bake(true);
        if(!slim&&wideRoot==null)wideRoot=NightDaggerArmModel.bake(false);
        ModelPart arm=(slim?slimRoot:wideRoot).getChild(left?"left_arm":"right_arm");
        arm.resetPose();arm.setPos(0,0,0);arm.setRotation(0,0,0);arm.visible=true;
        arm.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        stack.pushPose();try{
            stack.mulPose(ThunderSwordRig.arm(slim,left));
            event.getSubmitNodeCollector().submitModelPart(arm,stack,
                    RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
    }
}
