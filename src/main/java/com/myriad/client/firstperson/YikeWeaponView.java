package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.client.*;
import com.myriad.network.YikeStatePayload;
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
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.HashMap;
import java.util.Map;

/** Weapon-local hinges. The hand stays on the exposed grip in both views, never on a barrel. */
public final class YikeWeaponView {
    private static final net.minecraft.client.model.geom.ModelPart[] ARMS=new net.minecraft.client.model.geom.ModelPart[4];
    private static final Map<Integer,Long> DEPLOY=new HashMap<>();
    private YikeWeaponView(){}
    private static double opening(YikeStatePayload s,double now){
        if(s==null)return 0;
        if(s.mode()==1||s.mode()==3)return 1;if(s.mode()==2)return .55;
        if(s.windup()==4||s.windup()==5)return s.progress()*(s.windup()==4?1:.55);
        DEPLOY.remove(s.owner());return s.windup()==7?1:0;
    }
    public static boolean render(RenderHandEvent e){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!ClientDataHolder.modEnabled()||!"yike".equals(ClientDataHolder.characterId())
            ||!mc.player.isAlive()||mc.player.isSpectator()||!mc.player.getMainHandItem().is(ModItems.HONG_LIE.get())||mc.gui.screen()!=null)return false;
        if(e.getHand()==InteractionHand.OFF_HAND){if(mc.player.getOffhandItem().isEmpty()){e.setCanceled(true);return true;}return false;}
        var state=YikeClientState.get(mc.player.getId());double open=opening(state,mc.level.getGameTime()+e.getPartialTick());boolean deployed=open>.02;
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT;float mirror=left?-1:1;
        Vector3f palm=new Vector3f(.66f*mirror,-.62f,-.85f),axis=new Vector3f(.10f*mirror,.96f,-.26f).normalize();
        if(deployed){
            var aim=state==null?mc.player.getLookAngle():state.aim();Vec3 camera=mc.player.getLookAngle(),right=camera.cross(new Vec3(0,1,0)).normalize(),up=right.cross(camera).normalize();
            axis.set((float)aim.dot(right),(float)aim.dot(up),(float)-aim.dot(camera));
            // Keep the real muzzle below/right of centre, independent of mouse responsiveness.
            palm.set(.62f*mirror,-.55f,-.78f);axis.normalize();
        }
        Matrix4f base=basis(palm,axis,new Vector3f(1,0,0));
        boolean slim=mc.player.getSkin().model()==PlayerModelType.SLIM;int index=(slim?2:0)+(left?1:0);
        if(ARMS[index]==null)ARMS[index]=ShuoArmModel.bake(slim,left);var hand=ARMS[index];hand.resetPose();
        hand.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(new Matrix4f(base).translate(0,.05f,0).rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f).scale(.74f).mul(NightDaggerRig.arm(slim,left)));
            e.getSubmitNodeCollector().submitModelPart(hand,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
        stack.pushPose();try{stack.mulPose(base);parts(mc.player,stack,e.getSubmitNodeCollector(),e.getPackedLight(),open,state!=null&&state.mode()==2);}finally{stack.popPose();}
        e.setCanceled(true);return true;
    }
    /** Applied before the avatar is submitted; arm and item share the same server-approved sight. */
    public static void pose(HumanoidModel<?> model,HumanoidRenderState state){
        if(!(state instanceof AvatarRenderState avatar)||!state.getMainHandItemStack().is(ModItems.HONG_LIE.get())||!ClientDataHolder.modEnabled())return;
        var s=YikeClientState.get(avatar.id);if(s==null||s.mode()==0&&s.windup()<4)return;
        Vec3 aim=s.aim();float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)Math.toDegrees(-Math.asin(Math.clamp(aim.y,-1,1)));
        var arm=state.mainArm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        arm.xRot=(float)Math.toRadians(-90+pitch);arm.yRot=(float)Math.toRadians(net.minecraft.util.Mth.wrapDegrees(yaw-state.bodyRot));arm.zRot=0;
    }
    public static boolean held(ArmedModel<ArmedEntityRenderState> model,ArmedEntityRenderState state,ItemStack item,HumanoidArm arm,PoseStack stack,SubmitNodeCollector collector,int light){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!ClientDataHolder.modEnabled()||!(state instanceof AvatarRenderState avatar)||state.isInvisible||avatar.isSpectator
                ||arm!=state.mainArm||!item.is(ModItems.HONG_LIE.get()))return false;
        if(!(mc.level.getEntity(avatar.id) instanceof LivingEntity entity))return false;
        if(mc.player!=entity&&!FogOfWarClient.shouldRenderPlayer(avatar.id))return true;
        var s=YikeClientState.get(avatar.id);double open=opening(s,mc.level.getGameTime());
        stack.pushPose();try{
            model.translateToHand(state,arm,stack);
            // Model origin is the palm at y=10/16 along the actual arm; mesh grip is (8,8,8).
            stack.translate(arm==HumanoidArm.RIGHT?-1f/16:1f/16,10f/16,0);
            if(open<.02)stack.mulPose(Axis.XP.rotationDegrees(180));
            parts(entity,stack,collector,light,open,s!=null&&s.mode()==2);
        }finally{stack.popPose();}
        return true;
    }
    private static Matrix4f basis(Vector3f origin,Vector3f axis,Vector3f edge){
        var y=new Vector3f(axis).normalize();var x=new Vector3f(edge).fma(-edge.dot(y),y).normalize();var z=new Vector3f(x).cross(y).normalize();
        return new Matrix4f().setColumn(0,new Vector4f(x,0)).setColumn(1,new Vector4f(y,0)).setColumn(2,new Vector4f(z,0)).setColumn(3,new Vector4f(origin,1));
    }
    private static void item(LivingEntity owner,PoseStack stack,SubmitNodeCollector collector,int light,Item item){Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(owner,item.getDefaultInstance(),ItemDisplayContext.NONE,stack,collector,light);}
    private static void parts(LivingEntity owner,PoseStack stack,SubmitNodeCollector collector,int light,double open,boolean mobile){
        item(owner,stack,collector,light,ModItems.HONG_LIE_FRAME.get());
        for(int i=0;i<3;i++){
            stack.pushPose();try{
                stack.translate(0,5.5f/16,0);stack.mulPose(Axis.YP.rotationDegrees(i*120));
                double angle=mobile&&i==0?0:30*open;
                stack.mulPose(Axis.XP.rotationDegrees((float)angle));
                if(mobile&&i>0)stack.translate(0,Math.sin(owner.tickCount*.5+i)*.012,0);
                item(owner,stack,collector,light,ModItems.HONG_LIE_PETAL.get());
            }finally{stack.popPose();}
        }
        stack.pushPose();try{stack.translate(0,(13.2+open*1.35)/16,0);item(owner,stack,collector,light,ModItems.HONG_LIE_RING.get());}finally{stack.popPose();}
    }
}
