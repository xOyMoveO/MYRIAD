package com.myriad.client.firstperson;
import com.myriad.client.*;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

/** A low right-hand rapier grip with a short forward thrust; changes presentation only. */
public final class KehengView {
    private KehengView(){}
    private static ModelPart wide,slim;
    private static double attacked=-100;private static Object owner,world;
    private static boolean enabled(Minecraft mc){return mc.player!=null&&mc.level!=null&&ClientDataHolder.modEnabled()
        &&"keheng".equals(ClientDataHolder.characterId())&&mc.player.isAlive()&&!mc.player.isSpectator()
        &&mc.options.getCameraType().isFirstPerson()&&mc.getCameraEntity()==mc.player&&mc.player.getMainHandItem().is(ModItems.HENGKE.get());}
    public static void attack(InputEvent.InteractionKeyMappingTriggered e){var mc=Minecraft.getInstance();
        if(e.isAttack()&&!e.isCanceled()&&enabled(mc)&&BasicAttackReadiness.canAttack(mc.player)&&ClientDataHolder.placementType()==0)attacked=mc.player.tickCount;}
    public static boolean render(RenderHandEvent e){var mc=Minecraft.getInstance();if(!enabled(mc))return false;
        if(e.isCanceled()||e.getHand()!=InteractionHand.MAIN_HAND||mc.gui.screen()!=null||mc.player.isUsingItem()||mc.gameMode!=null&&mc.gameMode.isDestroying())return false;
        if(owner!=mc.player||world!=mc.level){owner=mc.player;world=mc.level;attacked=-100;}
        double age=mc.player.tickCount+e.getPartialTick()-attacked;
        double thrust=age>=0&&age<7?Math.sin(Math.PI*age/7):0;boolean charging=ClientDataHolder.isChanneling();
        float mirror=mc.player.getMainArm()==HumanoidArm.LEFT?-1:1;
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(new Matrix4f().translation(mirror*(.55f-(float)thrust*.10f),-.41f+(charging?.045f:0),-.88f-(float)thrust*.28f)
                    .rotateX((float)Math.toRadians(-12)).rotateY(mirror*(float)Math.toRadians(-7)).rotateZ(mirror*(float)Math.toRadians(64)).scale(.72f));
            boolean small=mc.player.getSkin().model()==PlayerModelType.SLIM;boolean left=mirror<0;
            if(small&&slim==null)slim=NightDaggerArmModel.bake(true);if(!small&&wide==null)wide=NightDaggerArmModel.bake(false);
            var arm=(small?slim:wide).getChild(left?"left_arm":"right_arm");arm.resetPose();arm.setPos(0,0,0);arm.setRotation(0,0,0);
            arm.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
            stack.pushPose();try{stack.mulPose(NightDaggerRig.arm(small,left));e.getSubmitNodeCollector().submitModelPart(arm,stack,
                    RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);}finally{stack.popPose();}
            // Undo the wrist roll, then point the thin +Y blade forward. Grip center is 0.15 blocks below the model center.
            stack.mulPose(new Matrix4f().rotateZ(mirror*(float)Math.toRadians(-64)).rotateX((float)Math.toRadians(charging?-80:-65)).translate(0,.15f,0).scale(.70f));
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,e.getItemStack(),ItemDisplayContext.NONE,stack,e.getSubmitNodeCollector(),e.getPackedLight());
        }finally{stack.popPose();}
        e.setCanceled(true);return true;
    }
}
