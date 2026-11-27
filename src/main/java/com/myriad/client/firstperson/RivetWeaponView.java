package com.myriad.client.firstperson;

import com.myriad.character.skills.rivet.RivetWeaponMotion;
import com.myriad.character.skills.rivet.RivetWeaponRig;
import com.myriad.client.*;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemDisplayContext;
import com.myriad.client.platform.HandRenderContext;

/** Rivet-only held hammer; no camera motion, input prediction, inventory or global arm changes. */
public final class RivetWeaponView {
    private static final RivetWeaponMotion MOTION=new RivetWeaponMotion();
    private static final ModelPart[] ARMS=new ModelPart[4];
    private static Object owner,world;
    private RivetWeaponView(){}
    private static boolean enabled(Minecraft mc){return mc.player!=null&&mc.level!=null&&ClientDataHolder.modEnabled()
            &&"rivet".equals(ClientDataHolder.characterId())&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&!RespawnSpectatorView.isActive()&&ClientDataHolder.syncedCounter("hero_respawn")<=0
            &&mc.player.getMainHandItem().is(ModItems.RIVET_HAMMER.get());}
    private static double contact=Double.NEGATIVE_INFINITY;
    public static void attack(Minecraft mc) {
        if (!enabled(mc)) return;
        check(mc);
        MOTION.basic(mc.level.getGameTime(),MeleeStrokeTiming.recovery(mc.player.getCurrentItemAttackStrengthDelay()));
    }
    public static void reset(){contact=Double.NEGATIVE_INFINITY;MOTION.reset();owner=null;world=null;}
    private static void check(Minecraft mc){if(owner!=mc.player||world!=mc.level){reset();owner=mc.player;world=mc.level;}}
    public static void receive(AbilityVfxPayload p){var mc=Minecraft.getInstance();
        if(!enabled(mc)||p.owner()!=mc.player.getId())return;check(mc);
        if (com.myriad.character.skills.rivet.RivetVfx.basicContact(p)) {
            if (mc.level.getGameTime()-p.started()<3) contact=p.started();
        } else MOTION.accept(p,mc.level.getGameTime());}
    public static boolean render(HandRenderContext e){var mc=Minecraft.getInstance();
        if(!enabled(mc)){reset();return false;}check(mc);
        if(e.isCanceled()||mc.gui.screen()!=null||mc.player.isUsingItem()||mc.gameMode!=null&&mc.gameMode.isDestroying()
                ||!mc.options.getCameraType().isFirstPerson()||mc.getCameraEntity()!=mc.player)return false;
        if(ClientDataHolder.buffTicks("g1_stun")>0)MOTION.reset();
        var pose=MOTION.sample(mc.level.getGameTime()+e.getPartialTick(),0,
                ClientDataHolder.buffTicks("g1_rivet_rhythm")>0);
        if(e.getHand()!=InteractionHand.MAIN_HAND)return false;
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT,slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        arm(e,pose,slim,left);
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(RivetWeaponRig.item(pose,left));
            com.myriad.client.platform.ClientItemRenderer.submit(mc.player,e.getItemStack(),ItemDisplayContext.NONE,
                    stack,e.getSubmitNodeCollector(),e.getPackedLight());
        }finally{stack.popPose();}
        double age=mc.level.getGameTime()+e.getPartialTick()-contact;
        if (age>=0 && age<2.5) {
            var head=RivetWeaponRig.rawItem(pose,left).transformPosition(new org.joml.Vector3f(-9,23,0));
            MeleeBladeLight.sparks(e,head,left?-1:1,age);
        }
        e.setCanceled(true);return true;
    }
    private static void arm(HandRenderContext e,RivetWeaponMotion.Pose pose,boolean slim,boolean left){
        if (FirstPersonArmVisibility.hidePlayerArms()) return;
        var mc=Minecraft.getInstance();int index=(slim?2:0)+(left?1:0);
        if(ARMS[index]==null)ARMS[index]=RivetArmModel.bake(slim,left);
        var arm=ARMS[index];arm.resetPose();arm.setPos(0,0,0);arm.setRotation(0,0,0);arm.visible=true;
        arm.getChild(left?"left_sleeve":"right_sleeve").visible=
                mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(RivetWeaponRig.hand(pose,slim,left));
            e.getSubmitNodeCollector().submitModelPart(arm,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),
                    e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
    }
}
