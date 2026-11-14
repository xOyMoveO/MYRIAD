package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.client.*;
import com.myriad.character.skills.yike.YikeWeaponAnimation;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Weapon-local hinges. The hand stays on the exposed grip in both views, never on a barrel. */
public final class YikeWeaponView {
    private static final net.minecraft.client.model.geom.ModelPart[] ARMS=new net.minecraft.client.model.geom.ModelPart[4];
    private YikeWeaponView(){}
    public static boolean render(RenderHandEvent e){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!ClientDataHolder.modEnabled()||!"yike".equals(ClientDataHolder.characterId())
            ||!mc.player.isAlive()||mc.player.isSpectator()||!mc.player.getMainHandItem().is(ModItems.HONG_LIE.get())||mc.gui.screen()!=null)return false;
        if(e.getHand()==InteractionHand.OFF_HAND){if(mc.player.getOffhandItem().isEmpty()){e.setCanceled(true);return true;}return false;}
        var state=YikeClientState.get(mc.player.getId());var pose=YikeClientState.weaponPose(mc.player.getId(),e.getPartialTick());
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT;float mirror=left?-1:1;
        Vector3f palm=new Vector3f(.66f*mirror,-.62f,-.85f),axis=new Vector3f(.10f*mirror,.96f,-.26f).normalize();
        var aim=state==null?mc.player.getLookAngle():YikeClientState.weaponAim(mc.player.getId(),e.getPartialTick());
        Vec3 camera=mc.player.getLookAngle();var rightParts=YikeWeaponAnimation.screenRight(camera.x,camera.z,mc.player.getYRot());
        Vec3 right=new Vec3(rightParts[0],rightParts[1],rightParts[2]),up=right.cross(camera).normalize();
        var targetAxis=new Vector3f((float)aim.dot(right),(float)aim.dot(up),(float)-aim.dot(camera)).normalize();
        // Rotate the weapon and hand together; the camera remains fully responsive.
        var turn=new Quaternionf().rotationTo(axis,targetAxis);
        new Quaternionf().slerp(turn,(float)pose.aimBlend()).transform(axis);
        palm.lerp(new Vector3f(.62f*mirror,-.55f,-.78f),(float)pose.aimBlend());
        Matrix4f base=basis(palm,axis);
        boolean slim=mc.player.getSkin().model()==PlayerModelType.SLIM;int index=(slim?2:0)+(left?1:0);
        if(ARMS[index]==null)ARMS[index]=ShuoArmModel.bake(slim,left);var hand=ARMS[index];hand.resetPose();
        hand.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(new Matrix4f(base).translate(0,.05f,0).rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f).scale(.74f).mul(NightDaggerRig.arm(slim,left)));
            e.getSubmitNodeCollector().submitModelPart(hand,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
        stack.pushPose();try{stack.mulPose(base);parts(mc.player,stack,e.getSubmitNodeCollector(),e.getPackedLight(),pose,mc.level.getGameTime()+e.getPartialTick());}finally{stack.popPose();}
        e.setCanceled(true);return true;
    }
    /** Applied before the avatar is submitted; arm and item share the same server-approved sight. */
    public static void pose(HumanoidModel<?> model,HumanoidRenderState state){
        if(!(state instanceof AvatarRenderState avatar)||!state.getMainHandItemStack().is(ModItems.HONG_LIE.get())||!ClientDataHolder.modEnabled())return;
        float partial=partial(state.ageInTicks);var pose=YikeClientState.weaponPose(avatar.id,partial);if(pose.aimBlend()<=0)return;
        Vec3 aim=YikeClientState.weaponAim(avatar.id,partial);float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)Math.toDegrees(-Math.asin(Math.clamp(aim.y,-1,1)));
        var arm=state.mainArm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        float blend=(float)pose.aimBlend();arm.xRot+=((float)Math.toRadians(-90+pitch)-arm.xRot)*blend;
        arm.yRot+=(float)Math.toRadians(net.minecraft.util.Mth.wrapDegrees(yaw-state.bodyRot))*blend-arm.yRot*blend;arm.zRot*=1-blend;
    }
    public static boolean held(ArmedModel<ArmedEntityRenderState> model,ArmedEntityRenderState state,ItemStack item,HumanoidArm arm,PoseStack stack,SubmitNodeCollector collector,int light){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!ClientDataHolder.modEnabled()||!(state instanceof AvatarRenderState avatar)||state.isInvisible||avatar.isSpectator
                ||arm!=state.mainArm||!item.is(ModItems.HONG_LIE.get()))return false;
        if(!(mc.level.getEntity(avatar.id) instanceof LivingEntity entity))return false;
        if(mc.player!=entity&&!FogOfWarClient.shouldRenderPlayer(avatar.id))return true;
        float partial=partial(state.ageInTicks);var pose=YikeClientState.weaponPose(avatar.id,partial);
        stack.pushPose();try{
            model.translateToHand(state,arm,stack);
            // Model origin is the palm at y=10/16 along the actual arm; mesh grip is (8,8,8).
            stack.translate(arm==HumanoidArm.RIGHT?-1f/16:1f/16,10f/16,0);
            stack.mulPose(Axis.XP.rotationDegrees((float)(180*(1-pose.aimBlend()))));
            parts(entity,stack,collector,light,pose,mc.level.getGameTime()+partial);
        }finally{stack.popPose();}
        return true;
    }
    private static Matrix4f basis(Vector3f origin,Vector3f axis){
        var y=new Vector3f(axis).normalize();var right=YikeWeaponAnimation.gripRight(y.x,y.y,y.z);
        var x=new Vector3f((float)right[0],(float)right[1],(float)right[2]);var z=new Vector3f(x).cross(y).normalize();
        return new Matrix4f().setColumn(0,new Vector4f(x,0)).setColumn(1,new Vector4f(y,0)).setColumn(2,new Vector4f(z,0)).setColumn(3,new Vector4f(origin,1));
    }
    private static void item(LivingEntity owner,PoseStack stack,SubmitNodeCollector collector,int light,Item item){Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(owner,item.getDefaultInstance(),ItemDisplayContext.NONE,stack,collector,light);}
    private static float partial(float age){return age-(float)Math.floor(age);}
    private static void parts(LivingEntity owner,PoseStack stack,SubmitNodeCollector collector,int light,YikeWeaponAnimation.Pose pose,double time){
        stack.pushPose();try{
        stack.translate(0,pose.recoil()/16,0);
        item(owner,stack,collector,light,ModItems.HONG_LIE_FRAME.get());
        for(int i=0;i<3;i++){
            stack.pushPose();try{
                stack.translate(0,5.5f/16,0);stack.mulPose(Axis.YP.rotationDegrees(i*120));
                stack.mulPose(Axis.XP.rotationDegrees((float)pose.petal(i)));
                stack.translate(0,pose.petalSway(i,time)/16,pose.latch()/16);
                item(owner,stack,collector,light,ModItems.HONG_LIE_PETAL.get());
            }finally{stack.popPose();}
        }
        stack.pushPose();try{stack.translate(0,(13.2+pose.ringSlide())/16,0);stack.mulPose(Axis.YP.rotationDegrees((float)pose.ringTwist()));
            item(owner,stack,collector,light,ModItems.HONG_LIE_RING.get());}finally{stack.popPose();}
        }finally{stack.popPose();}
    }
}
