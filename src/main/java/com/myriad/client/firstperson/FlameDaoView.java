package com.myriad.client.firstperson;

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

/** Fenjue-only first-person cosmetics. No packets, attack cancellation, hit logic or player-model edits. */
public final class FlameDaoView {
    private static final FlameDaoMotion MOTION=new FlameDaoMotion();
    private static final FlameSlashMotion SLASH=new FlameSlashMotion();
    private static final FlameStepMotion STEP=new FlameStepMotion();
    private static final FlameSkillMotion SKILL=new FlameSkillMotion();
    private static double aimStart=-1;
    private static Object owner,world;
    private static final FlameDaoArmModel.Skin[] ARMS=new FlameDaoArmModel.Skin[4];
    private FlameDaoView(){}
    private static boolean enabled(Minecraft mc){
        return mc.player!=null&&mc.level!=null&&FlameDaoRig.allowed(ClientDataHolder.modEnabled(),
                ClientDataHolder.characterId(),mc.player.isAlive()&&!mc.player.isSpectator()
                &&!RespawnSpectatorView.isActive()&&ClientDataHolder.syncedCounter("hero_respawn")<=0
                &&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson(),
                mc.player.getMainHandItem().is(ModItems.LIEYANJIAN.get()));
    }
    private static boolean busy(Minecraft mc){
        return mc.gui.screen()!=null||mc.player.isUsingItem()||(mc.gameMode!=null&&mc.gameMode.isDestroying())
                ||ClientDataHolder.placementType()!=0||ClientDataHolder.isChanneling()||ClientDataHolder.buffTicks("g1_stun")>0;
    }
    private static boolean hardBusy(Minecraft mc){return mc.gui.screen()!=null||mc.player.isUsingItem()
            ||mc.gameMode!=null&&mc.gameMode.isDestroying();}
    public static void reset(){MOTION.reset();SLASH.reset();STEP.reset();SKILL.reset();aimStart=-1;owner=null;world=null;}
    public static void receive(com.myriad.network.FlameSkillViewPayload cue){
        var mc=Minecraft.getInstance();if(!enabled(mc)||hardBusy(mc))return;checkOwner(mc);
        double now=mc.player.tickCount,elapsed=Math.max(0,mc.level.getGameTime()-cue.started());
        var fallback=STEP.active(now)?STEP.frame(now).pose():SLASH.active(now)?SLASH.frame(now).pose():MOTION.sample(now);
        if(SKILL.accept(cue,now,elapsed,fallback,aimStart>=0||ClientDataHolder.placementType()==104)){
            MOTION.reset();SLASH.interrupt();STEP.interrupt();
        }
    }
    public static void receive(com.myriad.network.AbilityVfxPayload cue){
        var mc=Minecraft.getInstance();
        if(!enabled(mc)||busy(mc)||cue.owner()!=mc.player.getId())return;
        checkOwner(mc);double now=mc.player.tickCount,elapsed=Math.max(0,mc.level.getGameTime()-cue.started());
        var fallback=STEP.active(now)?STEP.frame(now):SLASH.active(now)?SLASH.frame(now):MOTION.frame(now);
        if(STEP.accept(cue,now,elapsed,fallback)){
            MOTION.reset();
            if(cue.kind()==com.myriad.character.ability.AbilityVfxGeometry.CANCEL)SLASH.accept(cue,now,elapsed,fallback);
            else SLASH.interrupt();
            return;
        }
        if(SLASH.accept(cue,now,elapsed,fallback)){MOTION.reset();STEP.interrupt();}
    }
    private static void checkOwner(Minecraft mc){
        if(owner!=mc.player||world!=mc.level){reset();owner=mc.player;world=mc.level;}
    }
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(!enabled(mc)||hardBusy(mc)){reset();return;}checkOwner(mc);
        if(ClientDataHolder.buffTicks("g1_stun")>0||ClientDataHolder.buffTicks("g1_silence")>0)SKILL.interrupt();
        SKILL.vertical(mc.player.tickCount,mc.player.getDeltaMovement().y);
        if(ClientDataHolder.placementType()==104){if(aimStart<0)aimStart=mc.player.tickCount;}else aimStart=-1;
        if(busy(mc)){MOTION.reset();SLASH.interrupt();STEP.interrupt();}
    }
    /** Lowest-priority observer: a consumed click, weak attempt or mining never advances the chain. */
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||event.isCanceled()||!event.shouldSwingHand()||!enabled(mc)||busy(mc)
                ||mc.player.getAttackStrengthScale(.5f)<1)return;
        if(mc.hitResult!=null&&mc.hitResult.getType()==HitResult.Type.BLOCK)return;
        checkOwner(mc);if(SKILL.active(mc.player.tickCount)||SLASH.active(mc.player.tickCount)||STEP.active(mc.player.tickCount))return;
        MOTION.attack(mc.player.tickCount,mc.player.getCurrentItemAttackStrengthDelay());
    }
    public static boolean render(RenderHandEvent event){
        var mc=Minecraft.getInstance();
        if(!enabled(mc)){reset();return false;}
        if(event.isCanceled())return false;
        if(hardBusy(mc)){reset();return false;}
        checkOwner(mc);
        double now=mc.player.tickCount+event.getPartialTick();
        boolean aiming=ClientDataHolder.placementType()==104;
        if(aiming&&aimStart<0)aimStart=mc.player.tickCount;
        if(SKILL.active(now)||aiming){
            if(event.getHand()==InteractionHand.OFF_HAND){event.setCanceled(true);return true;}
            if(!event.getItemStack().is(ModItems.LIEYANJIAN.get()))return false;
            var frame=SKILL.active(now)?SKILL.frame(now):FlameSkillMotion.aim(now-aimStart);
            renderSkill(event,frame,now);event.setCanceled(true);return true;
        }
        if(busy(mc))return false;
        // Real offhand maps/items retain their own renderer; never duplicate or hide that hand.
        if(event.getHand()!=InteractionHand.MAIN_HAND||!event.getItemStack().is(ModItems.LIEYANJIAN.get()))return false;
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT,slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        boolean slash=SLASH.active(now),step=STEP.active(now);
        var frame=step?STEP.frame(now):slash?SLASH.frame(now):MOTION.frame(now);
        int armIndex=(slim?2:0)+(left?1:0);
        if(ARMS[armIndex]==null)ARMS[armIndex]=FlameDaoArmModel.bake(slim,left);
        var parts=ARMS[armIndex];
        parts.pose(frame.pose(),event.getEquipProgress());
        String sleeve=left?"left_sleeve":"right_sleeve";
        boolean showSleeve=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=event.getPoseStack();stack.pushPose();
        try{
            ModelPart arm=parts.model();
            arm.resetPose();arm.setPos(0,0,0);arm.setRotation(0,0,0);arm.visible=true;
            arm.getChild(sleeve).visible=showSleeve;
            stack.pushPose();try{
                stack.mulPose(FlameDaoRig.hand(frame.pose(),event.getEquipProgress(),left).mul(FlameDaoRig.arm(slim,left)));
                event.getSubmitNodeCollector().submitModelPart(arm,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),
                        event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
            }finally{stack.popPose();}
            stack.pushPose();try{
                stack.mulPose(FlameDaoRig.wrist(frame.pose(),event.getEquipProgress(),left).mul(FlameDaoRig.blade(frame,left)));
                mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,event.getItemStack(),
                        ItemDisplayContext.NONE,stack,event.getSubmitNodeCollector(),event.getPackedLight());
            }finally{stack.popPose();}
        }finally{stack.popPose();}
        if(step)AttackTrailRenderer.submit(event,FlameStepTrail.mesh(STEP,now,left,event.getEquipProgress()));
        else if(!slash)AttackTrailRenderer.submit(event,FlameBasicTrail.mesh(MOTION,now,left,event.getEquipProgress()));
        event.setCanceled(true);return true;
    }
    private static void renderSkill(RenderHandEvent event,FlameSkillMotion.Frame f,double now){
        var mc=Minecraft.getInstance();boolean left=mc.player.getMainArm()==HumanoidArm.LEFT,slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        arm(event,f.main(),FlameDaoRig.hand(f.main(),0,left),slim,left);
        if(f.showBlade()){
            var stack=event.getPoseStack();stack.pushPose();try{
                stack.mulPose(FlameSkillRig.main(f,left).mul(FlameSkillRig.blade(f,left)));
                mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,event.getItemStack(),ItemDisplayContext.NONE,stack,event.getSubmitNodeCollector(),event.getPackedLight());
            }finally{stack.popPose();}
        }
        if(f.showOff())arm(event,f.off(),FlameSkillRig.off(f,left),slim,!left);
        AttackTrailRenderer.submit(event,FlameSkillHandVfx.build(f,now,left));
    }
    private static void arm(RenderHandEvent event,FlameDaoMotion.Pose pose,org.joml.Matrix4f hand,boolean slim,boolean left){
        var mc=Minecraft.getInstance();int index=(slim?2:0)+(left?1:0);
        if(ARMS[index]==null)ARMS[index]=FlameDaoArmModel.bake(slim,left);
        var parts=ARMS[index];parts.pose(hand);var model=parts.model();model.resetPose();model.setPos(0,0,0);model.setRotation(0,0,0);model.visible=true;
        model.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=event.getPoseStack();stack.pushPose();try{
            stack.mulPose(new org.joml.Matrix4f(hand).mul(FlameDaoRig.arm(slim,left)));
            event.getSubmitNodeCollector().submitModelPart(model,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
    }
}
