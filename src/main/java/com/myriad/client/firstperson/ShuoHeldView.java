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
import org.joml.Matrix4f;

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
                stack.rotate(Axis.XP.rotationDegrees(-90));stack.rotate(Axis.YP.rotationDegrees(180));stack.translate(1f/16,2f/16,-10f/16);
                com.myriad.client.platform.ClientItemRenderer.submit(entity,item,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,stack,collector,light);
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
            com.myriad.client.platform.ClientItemRenderer.submit(entity,item,ItemDisplayContext.NONE,stack,collector,light);
            stack.translate(0,ShuoWeaponState.GRIP_TO_MOUTH,0);
            com.myriad.client.platform.ClientItemRenderer.submit(entity,ModItems.SHUO_SCABBARD.get().getDefaultInstance(),ItemDisplayContext.NONE,stack,collector,light);
        }finally{stack.popPose();}
        return true;
    }
    /** Closed-only half turn in hand space: keep the fist pivot and downward pitch, point the sheath aft. */
    public static Matrix4f closedGrip(ItemTransforms itemTransforms){
        var held=new PoseStack();
        held.rotate(Axis.XP.rotationDegrees(-90));held.rotate(Axis.YP.rotationDegrees(180));
        held.translate(-1f/16,2f/16,-10f/16);
        itemTransforms.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).apply(true,held.last());
        // ItemTransform.apply and NONE rendering each centre the mesh; undo just the former.
        held.translate(.5,.5,.5);
        var forward=held.last().pose();
        // Two sign changes are a proper 180-degree rotation, not a reflection. Restore the
        // grip translation so the sword and sheath turn together without orbiting the hand.
        return new Matrix4f().scaling(-1,1,-1).mul(forward)
                .setTranslation(forward.m30(),forward.m31(),forward.m32());
    }
    /** Shared by the live third-person renderer and offline mesh checks; follows the real left arm. */
    public static void leftHand(ArmedModel<ArmedEntityRenderState> model,ArmedEntityRenderState state,PoseStack stack,ItemTransforms itemTransforms){
        model.translateToHand(state,HumanoidArm.LEFT,stack);
        stack.mulPose(closedGrip(itemTransforms));
    }
}
