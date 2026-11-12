package com.myriad.client.firstperson;
import com.myriad.character.skills.shuo.ShuoBladeActivity;
import com.myriad.client.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
/** Attached immediately at the actual vanilla hand/item transform; no fabricated third-person animation. */
public final class ShuoHeldLight {
    private static final ShuoBladeActivity ACTIVITY=new ShuoBladeActivity();
    private static Object world,resources;private static ItemTransforms transforms;
    private ShuoHeldLight(){}
    private static void check(Minecraft mc){if(world!=mc.level){ACTIVITY.clear();world=mc.level;}}
    public static void reset(){ACTIVITY.clear();world=resources=null;transforms=null;}
    public static void receive(AbilityVfxPayload p){var mc=Minecraft.getInstance();check(mc);if(mc.level!=null)ACTIVITY.receive(p,mc.level.getGameTime());}
    public static void submit(ArmedEntityRenderState state,ItemStack item,HumanoidArm arm,PoseStack stack,SubmitNodeCollector collector){
        var mc=Minecraft.getInstance();check(mc);
        if(mc.level==null||!ClientDataHolder.modEnabled()||!(state instanceof AvatarRenderState avatar)||state.isInvisible||avatar.isSpectator
            ||arm!=state.mainArm||!item.is(ModItems.SHUO_SWORD.get())||!com.myriad.character.skills.shuo.ShuoWeaponState.drawn(item)
            ||!FogOfWarClient.shouldRenderPlayer(avatar.id)&&(mc.player==null||mc.player.getId()!=avatar.id))return;
        float alpha=ACTIVITY.opacity(avatar.id,mc.level.getGameTime());if(alpha<=0)return;
        if(transforms==null||resources!=mc.getResourceManager()){
            try(var reader=mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath("myriad","models/item/shuo_sword.json")).orElseThrow().openAsReader()){
                transforms=CuboidModel.fromStream(reader).transforms();resources=mc.getResourceManager();
            }catch(Exception ignored){return;}
        }
        boolean left=arm==HumanoidArm.LEFT;stack.pushPose();try{
            transforms.getTransform(left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left,stack.last());
            AttackTrailRenderer.submit(stack,collector,ShuoBladeLight.build(new org.joml.Matrix4f(),1,state.ageInTicks,.9f*alpha,false));
        }finally{stack.popPose();}
    }
}
