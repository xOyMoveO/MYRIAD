package com.myriad.client.firstperson;

import com.myriad.client.platform.HandRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Place a rigid skin sleeve between the camera-side shoulder and a specified grip. */
final class WeaponHands {
    private static final ModelPart[] arms=new ModelPart[4];
    private WeaponHands(){}
    static void draw(HandRenderContext context, boolean left, Vector3f palm) {
        var player=Minecraft.getInstance().player;
        boolean slim=player.getSkin().model()==PlayerModelType.SLIM;
        int index=(left?1:0)+(slim?2:0);
        if(arms[index]==null)arms[index]=NightDaggerArmModel.bake(slim).getChild(left?"left_arm":"right_arm");
        var arm=arms[index];arm.resetPose();
        arm.getChild(left?"left_sleeve":"right_sleeve").visible=player.isModelPartShown(
                left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var along=new Vector3f(palm).sub(left?-.87f:.87f,-.78f,-.12f).normalize();
        Matrix4f placement=ShuoRig.basis(palm,along,new Vector3f(1,0,0),false)
                .scale(.74f).mul(NightDaggerRig.arm(slim,left));
        var stack=context.getPoseStack();stack.pushPose();
        try{
            stack.mulPose(placement);
            context.getSubmitNodeCollector().submitModelPart(arm,stack,RenderTypes.entityTranslucent(
                    player.getSkin().body().texturePath()),context.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
    }
    static void support(HandRenderContext e,boolean mainLeft,Vector3f contact,float weight){
        if(weight<=.01f || !Minecraft.getInstance().player.getOffhandItem().isEmpty())return;
        var rest=new Vector3f(mainLeft?.82f:-.82f,-1.15f,-.72f);
        draw(e,!mainLeft,rest.lerp(contact,Math.clamp(weight,0,1)));
    }
}
