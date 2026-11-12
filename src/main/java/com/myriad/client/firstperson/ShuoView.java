package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.network.ShuoViewPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/** Shuo only: visible skin hands share the item's actual grip matrices. No third-person rig. */
public final class ShuoView {
    private static final ShuoMotion MOTION=new ShuoMotion();
    private static final net.minecraft.client.model.geom.ModelPart[] ARMS=new net.minecraft.client.model.geom.ModelPart[4];
    private static Object owner,world;private static boolean predictedDrawn;
    private ShuoView(){}
    private static boolean enabled(Minecraft mc){return mc.player!=null&&mc.level!=null&&ClientDataHolder.modEnabled()
        &&"shuo".equals(ClientDataHolder.characterId())&&mc.player.isAlive()&&!mc.player.isSpectator()
        &&!RespawnSpectatorView.isActive()&&ClientDataHolder.syncedCounter("hero_respawn")<=0
        &&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson()
        &&mc.player.getMainHandItem().is(ModItems.SHUO_SWORD.get())&&mc.player.getOffhandItem().isEmpty();}
    private static boolean menu(Minecraft mc){return mc.gui.screen()!=null||mc.player.isUsingItem()||mc.gameMode!=null&&mc.gameMode.isDestroying();}
    private static boolean drawn(){return ClientDataHolder.syncedCounter("g1_shuo_drawn")>0;}
    public static void reset(){MOTION.reset();owner=world=null;predictedDrawn=false;}
    private static void check(Minecraft mc){if(owner!=mc.player||world!=mc.level){reset();owner=mc.player;world=mc.level;}}
    public static void receive(ShuoViewPayload p){var mc=Minecraft.getInstance();if(!enabled(mc)||menu(mc))return;check(mc);
        double now=mc.player.tickCount;boolean old=drawn()||predictedDrawn;
        if(MOTION.accept(p,now,Math.max(0,mc.level.getGameTime()-p.started()),old)){
            if(ShuoViewPayload.basicAction(p.action())||p.action()==ShuoViewPayload.FLASH||p.action()>=ShuoViewPayload.CUT_LEFT&&p.action()<=ShuoViewPayload.CUT_DOWN)predictedDrawn=true;
            if(p.action()==ShuoViewPayload.CANCEL)predictedDrawn=drawn();
        }
    }
    public static void tick(){var mc=Minecraft.getInstance();if(!enabled(mc)||menu(mc)){reset();return;}check(mc);
        if(!MOTION.active(mc.player.tickCount))predictedDrawn=drawn();
    }
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered e){var mc=Minecraft.getInstance();
        if(!e.isAttack()||e.isCanceled()||!e.shouldSwingHand()||!enabled(mc)||menu(mc)||ClientDataHolder.isChanneling()
            ||ClientDataHolder.buffTicks("g1_stun")>0||ClientDataHolder.buffTicks("g1_shuo_sheathing")>0||mc.player.getAttackStrengthScale(.5f)<1
            ||mc.hitResult!=null&&mc.hitResult.getType()==HitResult.Type.BLOCK)return;
        check(mc);MOTION.basic(mc.player.tickCount,drawn()||predictedDrawn);
    }
    public static boolean render(RenderHandEvent e){var mc=Minecraft.getInstance();if(!enabled(mc)){reset();return false;}
        if(e.isCanceled()||menu(mc))return false;check(mc);
        if(e.getHand()==InteractionHand.OFF_HAND){e.setCanceled(true);return true;}
        double now=mc.player.tickCount+e.getPartialTick();boolean held=drawn()||predictedDrawn;var f=MOTION.frame(now,held);
        boolean mirror=mc.player.getMainArm()==HumanoidArm.LEFT,slim=mc.player.getSkin().model()==PlayerModelType.SLIM;
        if(ShuoRig.SHOW_OFF_HAND)arm(e,ShuoRig.hand(f,true,mirror),slim,!mirror);
        if(ShuoRig.showMainHand(f,held))arm(e,ShuoRig.hand(f,false,mirror),slim,mirror);
        item(e,ShuoRig.sheath(f,mirror),ModItems.SHUO_SCABBARD.get().getDefaultInstance());
        item(e,ShuoRig.blade(f,mirror),e.getItemStack());
        AttackTrailRenderer.submit(e,ShuoTrail.build(f,mirror));
        AttackTrailRenderer.submit(e,ShuoSkillTrail.build(MOTION,now,mirror));e.setCanceled(true);return true;
    }
    private static void arm(RenderHandEvent e,Matrix4f hand,boolean slim,boolean left){var mc=Minecraft.getInstance();int i=(slim?2:0)+(left?1:0);
        if(ARMS[i]==null)ARMS[i]=ShuoArmModel.bake(slim,left);var model=ARMS[i];
        model.resetPose();model.setPos(0,0,0);model.setRotation(0,0,0);model.visible=true;
        model.getChild(left?"left_sleeve":"right_sleeve").visible=mc.player.isModelPartShown(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=e.getPoseStack();stack.pushPose();try{stack.mulPose(new Matrix4f(hand).mul(NightDaggerRig.arm(slim,left)));
            e.getSubmitNodeCollector().submitModelPart(model,stack,RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath()),e.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        }finally{stack.popPose();}}
    private static void item(RenderHandEvent e,Matrix4f at,net.minecraft.world.item.ItemStack item){var mc=Minecraft.getInstance();var stack=e.getPoseStack();stack.pushPose();try{
        stack.mulPose(at);mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player,item,ItemDisplayContext.NONE,stack,e.getSubmitNodeCollector(),e.getPackedLight());
    }finally{stack.popPose();}}
}
