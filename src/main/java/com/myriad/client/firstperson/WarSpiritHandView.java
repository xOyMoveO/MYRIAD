package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import static com.myriad.character.ability.WarSpiritHandCues.SPIRIT;
import static com.myriad.character.ability.WarSpiritHandCues.WAR;

public final class WarSpiritHandView {
    private static final WarSpiritHandMotion DAO=new WarSpiritHandMotion(true);
    private static final WarSpiritHandMotion BELL=new WarSpiritHandMotion(false);
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static int theme;
    private static long newest=Long.MIN_VALUE;
    private static ModelPart slimArms,wideArms;

    private WarSpiritHandView() {}

    private static int available(Minecraft mc) {
        if(mc.level==null || mc.player==null || !ClientDataHolder.modEnabled() || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.getCameraEntity()!=mc.player || !mc.options.getCameraType().isFirstPerson()
                || RespawnSpectatorView.isActive() || ClientDataHolder.syncedCounter("hero_respawn")>0
                || mc.gui.screen()!=null || mc.player.isUsingItem()
                || mc.gameMode!=null && mc.gameMode.isDestroying()) return 0;
        String role=ClientDataHolder.characterId();
        ItemStack stack=mc.player.getMainHandItem();
        if("chixiao".equals(role) && correct(stack,WAR)) return WAR;
        if("yunsheng".equals(role) && correct(stack,SPIRIT)) return SPIRIT;
        return 0;
    }

    private static boolean correct(ItemStack stack,int id) {
        return stack.is(id==WAR?ModItems.CHIXIAO_BLADE.get():ModItems.YUNSHENG_BELL.get());
    }

    private static void attach(Minecraft mc,int id) {
        if(owner==mc.player && world==mc.level && theme==id)return;
        reset();owner=mc.player;world=mc.level;theme=id;
    }

    private static WarSpiritHandMotion motion() {return theme==WAR?DAO:BELL;}

    public static void reset() {
        DAO.reset();BELL.reset();owner=null;world=null;theme=0;newest=Long.MIN_VALUE;
    }
    public static void clearOwner(int id) {if(owner!=null && owner.getId()==id)reset();}
    public static void cancelSkill(AbilityVfxPayload p) {
        if(owner!=null && p.owner()==owner.getId() && p.theme()==theme)motion().cancel(p.variant(),owner.tickCount);
    }

    public static void tick() {
        var mc=Minecraft.getInstance();int id=available(mc);
        if(id==0){if(owner!=null)reset();return;}
        attach(mc,id);
        if(ClientDataHolder.buffTicks("g1_stun")>0)motion().abort(owner.tickCount);
    }

    public static void receive(AbilityVfxPayload cue) {
        var mc=Minecraft.getInstance();int id=available(mc);
        if(id==0 || cue.theme()!=id || cue.owner()!=mc.player.getId())return;
        attach(mc,id);
        if(cue.started()<newest)return;
        newest=cue.started();
        motion().cue(cue.variant(),(int)cue.angle(),owner.tickCount,cue.life(),cue.radius());
    }

    public static boolean render(HandRenderContext event) {
        var mc=Minecraft.getInstance();int id=available(mc);
        if(id==0 || event.isCanceled())return false;
        attach(mc,id);
        if(event.getHand()!=InteractionHand.MAIN_HAND || !correct(event.getItemStack(),id))return false;
        double now=owner.tickCount+event.getPartialTick();
        float equip=event.getEquipProgress();
        boolean left=owner.getMainArm()==HumanoidArm.LEFT;
        var pose=motion().pose(now);
        var item=WarSpiritHandRig.item(motion(),now,id==WAR,left,equip);
        var stack=event.getPoseStack();stack.pushPose();
        try {
            stack.mulPose(item);
            ClientItemRenderer.submit(owner,event.getItemStack(),ItemDisplayContext.NONE,stack,
                    event.getSubmitNodeCollector(),event.getPackedLight());
        } finally {stack.popPose();}
        drawArm(event,pose,item,left,false);
        if(owner.getOffhandItem().isEmpty() && pose.palm().y> -1.10)drawArm(event,pose,item,left,true);
        event.setCanceled(true);
        return true;
    }

    private static void drawArm(HandRenderContext event,WarSpiritHandMotion.Pose pose,Matrix4f item,boolean left,boolean off) {
        if (FirstPersonArmVisibility.hidePlayerArms()) return;
        boolean slim=owner.getSkin().model()==PlayerModelType.SLIM, armLeft=left!=off;
        if(slim && slimArms==null)slimArms=NightDaggerArmModel.bake(true);
        if(!slim && wideArms==null)wideArms=NightDaggerArmModel.bake(false);
        String side=armLeft?"left":"right";
        var arm=(slim?slimArms:wideArms).getChild(side+"_arm");
        arm.getChild(side+"_sleeve").visible=owner.isModelPartShown(
                armLeft?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE);
        var stack=event.getPoseStack();stack.pushPose();
        try {
            stack.mulPose(WarSpiritHandRig.arm(pose,item,theme==WAR,left,off,slim,event.getEquipProgress()));
            var type=RenderTypes.entityTranslucent(owner.getSkin().body().texturePath());
            event.getSubmitNodeCollector().submitModelPart(arm,stack,type,event.getPackedLight(),OverlayTexture.NO_OVERLAY,null);
        } finally {stack.popPose();}
    }
}
