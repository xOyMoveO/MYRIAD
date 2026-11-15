package com.myriad.client.firstperson;

import com.myriad.character.skills.rivet.RivetWeaponMotion;
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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

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
    public static void reset(){MOTION.reset();owner=null;world=null;}
    private static void check(Minecraft mc){if(owner!=mc.player||world!=mc.level){reset();owner=mc.player;world=mc.level;}}
    public static void receive(AbilityVfxPayload p){var mc=Minecraft.getInstance();
        if(!enabled(mc)||p.owner()!=mc.player.getId())return;check(mc);MOTION.accept(p,mc.level.getGameTime());}
    public static boolean render(RenderHandEvent e){var mc=Minecraft.getInstance();
        if(!enabled(mc)){reset();return false;}check(mc);
        if(e.isCanceled()||mc.gui.screen()!=null||mc.player.isUsingItem()||mc.gameMode!=null&&mc.gameMode.isDestroying()
                ||!mc.options.getCameraType().isFirstPerson()||mc.getCameraEntity()!=mc.player)return false;
        if(ClientDataHolder.buffTicks("g1_stun")>0)MOTION.reset();
        var pose=MOTION.sample(mc.level.getGameTime()+e.getPartialTick(),mc.player.getAttackAnim(e.getPartialTick()),
                ClientDataHolder.buffTicks("g1_rivet_rhythm")>0);
        if(e.getHand()!=InteractionHand.MAIN_HAND){
            if(e.getItemStack().isEmpty()&&pose.support()>.1){e.setCanceled(true);return true;}return false;
        }
        boolean left=mc.player.getMainArm()==HumanoidArm.LEFT,slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        float mirror=left?-1:1,deg=(float)(Math.PI/180);
        var base=new Matrix4f().translation((float)pose.x()*mirror,(float)pose.y(),(float)pose.z())
                .rotateX((float)pose.pitch()*deg).rotateY((float)pose.yaw()*mirror*deg).rotateZ((float)pose.roll()*mirror*deg);
        arm(e,base,slim,left);
        if(pose.support()>.1&&mc.player.getOffhandItem().isEmpty())
            arm(e,new Matrix4f(base).translate(.04f*mirror,(float)(.18+.10*pose.support()),.025f).rotateY(mirror*.22f),slim,!left);
        var stack=e.getPoseStack();stack.pushPose();try{
            // ItemRenderer centres raw item coordinates on (8,8,8). Grip is source (0,5.5,0).
            stack.mulPose(base);stack.scale(.70f,.70f,.70f);stack.translate(.5f,.15625f,.5f);
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,e.getItemStack(),ItemDisplayContext.NONE,
                    stack,e.getSubmitNodeCollector(),e.getPackedLight());
        }finally{stack.popPose();}
        e.setCanceled(true);return true;
    }
    private static void arm(RenderHandEvent e,Matrix4f base,boolean slim,boolean left){
        var mc=Minecraft.getInstance();int index=(slim?2:0)+(left?1:0);
        if(ARMS[index]==null)ARMS[index]=ShuoArmModel.bake(slim,left);
        var arm=ARMS[index];arm.resetPose();arm.getChild(left?"left_sleeve":"right_sleeve").visible=
                mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=e.getPoseStack();stack.pushPose();try{
            stack.mulPose(new Matrix4f(base).rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f)
                    .scale(.74f).mul(NightDaggerRig.arm(slim,left)));
            e.getSubmitNodeCollector().submitModelPart(arm,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),
                    e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}
    }
}
