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
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class LingyuanHandView {
    private static final LingyuanHandMotion MOTION=new LingyuanHandMotion();
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static long newest=Long.MIN_VALUE;
    private static ModelPart slimArms,wideArms,leftFingers,rightFingers;

    private LingyuanHandView(){}

    private static boolean eligible(Minecraft mc) {
        return mc.level!=null && mc.player!=null && ClientDataHolder.modEnabled()
                && "lingyuan".equals(ClientDataHolder.characterId()) && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.getCameraEntity()==mc.player && mc.options.getCameraType().isFirstPerson()
                && !RespawnSpectatorView.isActive() && ClientDataHolder.syncedCounter("hero_respawn")<=0
                && mc.player.getMainHandItem().is(ModItems.LINGYUAN_SWORD.get())
                && mc.gui.screen()==null && !mc.player.isUsingItem()
                && (mc.gameMode==null || !mc.gameMode.isDestroying());
    }

    private static void attach(Minecraft mc) {
        if (owner==mc.player && world==mc.level) return;
        reset();
        owner=mc.player;
        world=mc.level;
    }

    public static void reset() {
        MOTION.reset();
        newest=Long.MIN_VALUE;
        owner=null;
        world=null;
    }

    public static void clearOwner(int id) {
        if(owner!=null && owner.getId()==id)reset();
    }

    public static void tick() {
        var mc=Minecraft.getInstance();
        if(!eligible(mc)){if(owner!=null)reset();return;}
        attach(mc);
        if(ClientDataHolder.buffTicks("g1_stun")>0)MOTION.abort(mc.player.tickCount);
    }

    public static void attack(Minecraft mc) {
        if (!(eligible(mc))) return;
        attach(mc);
        MOTION.cue(0, com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.BASIC, mc.player.tickCount, 9,
                mc.player.getCurrentItemAttackStrengthDelay());
    }

    public static void receive(AbilityVfxPayload cue) {
        var mc=Minecraft.getInstance();
        if(!eligible(mc) || cue.owner()!=mc.player.getId())return;
        attach(mc);
        if(cue.started()<newest)return;
        newest=cue.started();
        if ((int)cue.angle() == com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.BASIC) return; // Local input already owns the basic.
        MOTION.cue(cue.variant(),(int)cue.angle(),mc.player.tickCount,cue.life(),cue.radius());
    }

    public static boolean render(HandRenderContext event) {
        var mc=Minecraft.getInstance();
        if(event.isCanceled() || !eligible(mc))return false;
        attach(mc);
        double now=mc.player.tickCount+event.getPartialTick();
        var pose=MOTION.pose(now);
        boolean off=pose.palm().y>-1.19 && mc.player.getOffhandItem().isEmpty();
        if(event.getHand()==InteractionHand.OFF_HAND){
            if(off)event.setCanceled(true);
            return off;
        }
        if(!event.getItemStack().is(ModItems.LINGYUAN_SWORD.get()))return false;
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT;
        if (Double.isFinite(MOTION.basicStart(now))) event.holdEquipped();
        var sword=LingyuanHandRig.sword(MOTION,now,left,event.getEquipProgress());
        drawArm(event,mc.player,pose,left,false,sword);
        var stack=event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(sword);
            ClientItemRenderer.submit(mc.player,event.getItemStack(),ItemDisplayContext.NONE,stack,
                    event.getSubmitNodeCollector(),event.getPackedLight());
        } finally { stack.popPose(); }
        AttackTrailRenderer.submit(event,MeleeBladeLight.swept(now,MOTION.basicStart(now),.32,2.35,
                MeleeBladeLight.SILVER,new org.joml.Vector3f(0,.26f,0),new org.joml.Vector3f(0,1.20625f,0),
                at->LingyuanHandRig.sword(MOTION,at,left,event.getEquipProgress())));
        if(off)drawArm(event,mc.player,pose,left,true,sword);
        event.setCanceled(true);
        return true;
    }

    private static void drawArm(HandRenderContext event, LocalPlayer player, LingyuanHandMotion.Pose pose,
                                boolean left, boolean off, Matrix4f sword) {
        if (FirstPersonArmVisibility.hidePlayerArms()) return;
        boolean slim=player.getSkin().model()==PlayerModelType.SLIM, armLeft=left!=off;
        if(slim && slimArms==null)slimArms=NightDaggerArmModel.bake(true);
        if(!slim && wideArms==null)wideArms=NightDaggerArmModel.bake(false);
        String side=armLeft?"left":"right";
        var arm=(slim?slimArms:wideArms).getChild(side+"_arm");
        arm.getChild(side+"_sleeve").visible=player.isModelPartShown(
                armLeft?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(LingyuanHandRig.arm(pose,left,off,slim,event.getEquipProgress(),sword));
            var type=RenderTypes.entityTranslucent(player.getSkin().body().texturePath());
            var collector=event.getSubmitNodeCollector();
            collector.submitModelPart(arm,stack,type,event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
            if(off && pose.point()>.35){
                if(armLeft && leftFingers==null)leftFingers=fingers(true);
                if(!armLeft && rightFingers==null)rightFingers=fingers(false);
                stack.translate(armLeft?(slim?.03f:0):(slim?-.03f:0),10f/16,0);
                stack.scale(1,(float)((pose.point()-.35)/.65),1);
                collector.submitModelPart(armLeft?leftFingers:rightFingers,stack,type,event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
            }
        } finally { stack.popPose(); }
    }

    private static ModelPart fingers(boolean left) {
        var faces=EnumSet.allOf(Direction.class);
        var index=new ModelPart.Cube(left?39:46,left?50:18,left?.12f:-.91f,-.14f,-.78f,
                .79f,1.8f,1.28f,0,0,0,false,64,64,faces);
        var middle=new ModelPart.Cube(left?40:47,left?50:18,left?1.04f:-1.83f,-.14f,-.78f,
                .79f,1.6f,1.28f,0,0,0,false,64,64,faces);
        return new ModelPart(List.of(index,middle),Map.of());
    }
}
