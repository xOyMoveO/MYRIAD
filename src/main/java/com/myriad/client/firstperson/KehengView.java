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

/** Both the sword and the skin hand follow the midpoint of 恒刻's leather grip. */
public final class KehengView {
    private static final float GRIP_Y = (5.7f - 8) / 16;
    private static final float SWORD_SCALE = 1.18f;
    private static final float ARM_ROLL = (float) Math.toRadians(22);
    private static final float BLADE_ROLL = (float) Math.toRadians(142);

    private KehengView() {}

    public static boolean render(HandRenderContext e) {
        var mc = Minecraft.getInstance();
        var owner = mc.player;
        if (owner == null || mc.level == null || !ClientDataHolder.modEnabled()
                || !"keheng".equals(ClientDataHolder.characterId()) || !owner.isAlive() || owner.isSpectator()
                || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != owner
                || !owner.getMainHandItem().is(ModItems.HENGKE.get()) || e.isCanceled() || mc.gui.screen() != null
                || owner.isUsingItem() || mc.gameMode != null && mc.gameMode.isDestroying()
                || e.getHand() != InteractionHand.MAIN_HAND) return false;

        boolean left = owner.getMainArm() == HumanoidArm.LEFT;
        int side = left ? -1 : 1;
        var motion = WeaponBodyPose.sample(owner, e.getPartialTick());
        float thrust = motion.thrust();
        var wrist = new Matrix4f().translation(side * (.56f - .14f * thrust), -.42f + .04f * thrust, -1.22f - .15f * thrust)
                .mul(motion.wrist(left)).rotateY(-WeaponBodyPose.aimingTurn(owner));
        var palm = wrist.transformPosition(new Vector3f());
        WeaponHands.draw(e, left, palm, new Vector3f(side * .90f, -.90f, -.20f), side * ARM_ROLL);
        WeaponHands.support(e, left, new Vector3f(-side * .24f, -.40f, -1.14f), motion.support());

        // Turn the blade at the grip so that extension brings the tip towards the sight line.
        var axis = new Vector3f(side * .38f, .77f, -.51f)
                .lerp(new Vector3f(-side * .23f, .26f, -.94f), thrust);
        var blade = ShuoRig.basis(new Vector3f(), axis, new Vector3f(1, 0, 0), false)
                .rotateY(side * BLADE_ROLL).scale(SWORD_SCALE).translate(0, -GRIP_Y, 0);
        var pose = e.getPoseStack();
        pose.pushPose();
        try {
            pose.mulPose(wrist.mul(blade));
            ClientItemRenderer.submit(owner, e.getItemStack(), ItemDisplayContext.NONE, pose,
                    e.getSubmitNodeCollector(), e.getPackedLight());
        } finally {
            pose.popPose();
        }
        e.setCanceled(true);
        return true;
    }
}
