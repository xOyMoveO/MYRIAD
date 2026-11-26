package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.platform.ClientItemRenderer;
import com.myriad.client.platform.HandRenderContext;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The pivot is the midpoint of 恒刻's leather grip; scaling changes only the sword. */
public final class KehengView {
    private static final float GRIP_Y=(5.7f-8)/16;
    private KehengView(){}
    public static boolean render(HandRenderContext e){
        var mc=Minecraft.getInstance();var owner=mc.player;
        if(owner==null||mc.level==null||!ClientDataHolder.modEnabled()||!"keheng".equals(ClientDataHolder.characterId())
                ||!owner.isAlive()||owner.isSpectator()||!mc.options.getCameraType().isFirstPerson()||mc.getCameraEntity()!=owner
                ||!owner.getMainHandItem().is(ModItems.HENGKE.get())||e.isCanceled()||mc.gui.screen()!=null||owner.isUsingItem()
                ||mc.gameMode!=null&&mc.gameMode.isDestroying()||e.getHand()!=InteractionHand.MAIN_HAND)return false;
        boolean left=owner.getMainArm()==HumanoidArm.LEFT;int side=left?-1:1;
        var motion=WeaponBodyPose.sample(owner,e.getPartialTick());
        var wrist=new Matrix4f().translation(side*.45f,-.31f,-.98f).mul(motion.wrist(left)).rotateY(-WeaponBodyPose.aimingTurn(owner));
        var palm=wrist.transformPosition(new Vector3f());
        WeaponHands.draw(e,left,palm);
        WeaponHands.support(e,left,new Vector3f(-side*.20f,-.34f,-1.04f),motion.support());
        var pose=e.getPoseStack();pose.pushPose();
        try{
            pose.mulPose(wrist.rotateZ(side*-.24f).rotateX(-.58f).rotateY(side*.32f)
                    .scale(.72f*.70f*1.20f).translate(0,-GRIP_Y,0));
            ClientItemRenderer.submit(owner,e.getItemStack(),ItemDisplayContext.NONE,pose,e.getSubmitNodeCollector(),e.getPackedLight());
        }finally{pose.popPose();}
        e.setCanceled(true);return true;
    }
}
