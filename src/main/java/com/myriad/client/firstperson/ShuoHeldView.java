package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.character.skills.shuo.ShuoWeaponState;
import com.myriad.client.*;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;

/** Closed weapon follows the real left hand, using original sword/sheath meshes and UVs. */
public final class ShuoHeldView {
    private static Object resources;private static ItemTransforms transforms;
    private ShuoHeldView(){}
    public static boolean submit(ArmedModel<ArmedEntityRenderState> model,ArmedEntityRenderState state,ItemStack item,HumanoidArm arm,
            PoseStack stack,SubmitNodeCollector collector,int light){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!ClientDataHolder.modEnabled()||!(state instanceof AvatarRenderState avatar)||state.isInvisible||avatar.isSpectator)return false;
        var main=state.getMainHandItemStack();
        if(!main.is(ModItems.SHUO_SWORD.get())||ShuoWeaponState.drawn(main))return false;
        if(!(mc.level.getEntity(avatar.id) instanceof LivingEntity entity))return false;
        if(!FogOfWarClient.shouldRenderPlayer(avatar.id)&&(mc.player==null||mc.player.getId()!=avatar.id))return true;
        // A real offhand object must not overlap the closed sword in the same left fist.
        // Change its visual hand only; no equipment or inventory mutation is performed.
        if(arm!=state.mainArm){
            if(arm==HumanoidArm.RIGHT||item.isEmpty())return false;
            stack.pushPose();try{
                model.translateToHand(state,HumanoidArm.RIGHT,stack);
                stack.mulPose(Axis.XP.rotationDegrees(-90));stack.mulPose(Axis.YP.rotationDegrees(180));stack.translate(1f/16,2f/16,-10f/16);
                mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(entity,item,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,stack,collector,light);
            }finally{stack.popPose();}
            return true;
        }
        if(transforms==null||resources!=mc.getResourceManager()){
            try(var reader=mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath("myriad","models/item/shuo_sword.json")).orElseThrow().openAsReader()){
                transforms=CuboidModel.fromStream(reader).transforms();resources=mc.getResourceManager();
            }catch(Exception ignored){return false;}
        }
        stack.pushPose();try{
            leftHand(model,state,stack,transforms);
            // Both meshes are centred at (8,8,8). Blade root is exactly 3.5 model units above grip.
            var renderer=mc.getEntityRenderDispatcher().getItemInHandRenderer();
            renderer.renderItem(entity,item,ItemDisplayContext.NONE,stack,collector,light);
            stack.translate(0,ShuoWeaponState.GRIP_TO_MOUTH,0);
            renderer.renderItem(entity,ModItems.SHUO_SCABBARD.get().getDefaultInstance(),ItemDisplayContext.NONE,stack,collector,light);
        }finally{stack.popPose();}
        return true;
    }
    /** Shared with offline mesh verification: exactly the existing vanilla left-hand transform. */
    public static void leftHand(ArmedModel<ArmedEntityRenderState> model,ArmedEntityRenderState state,PoseStack stack,ItemTransforms itemTransforms){
        model.translateToHand(state,HumanoidArm.LEFT,stack);
        stack.mulPose(Axis.XP.rotationDegrees(-90));stack.mulPose(Axis.YP.rotationDegrees(180));
        stack.translate(-1f/16,2f/16,-10f/16);
        itemTransforms.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).apply(true,stack.last());
        // 26.2 ItemTransform.apply already includes -0.5 centring. NONE rendering centres again.
        // Undo only that last centring here, otherwise both meshes float away from the hand.
        stack.translate(.5,.5,.5);
    }
}
