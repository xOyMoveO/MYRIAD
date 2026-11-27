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

    private static Object ownerIdentity, world;
    private static double start = Double.NEGATIVE_INFINITY, duration = 10;
    private static Stroke entry;
    private record Stroke(float x,float y,float z,float aim) {
        Stroke mix(Stroke b,double v) {
            float t=(float)Math.clamp(v,0,1);
            t=t*t*(3-2*t);
            return new Stroke(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,aim+(b.aim-aim)*t);
        }
    }
    private static final Stroke REST = new Stroke(0,0,0,0);
    private static final Stroke READY = new Stroke(.015f,.016f,.06f,.35f);
    private static final Stroke HIT = new Stroke(-.10f,.035f,-.25f,.94f);
    private static final Stroke FOLLOW = new Stroke(-.12f,.025f,-.32f,1);
    private static final Stroke LOW = new Stroke(-.06f,-.09f,-.16f,.72f);

    public static void clearStroke() { start=Double.NEGATIVE_INFINITY; entry=REST; }
    public static void reset() { clearStroke(); ownerIdentity=null; world=null; }
    private static Stroke stroke(double now) {
        double age=now-start;
        if (age<0 || age>=duration) return REST;
        if (age<.33) return entry.mix(READY,age/.33);
        if (age<MeleeStrokeTiming.HIT) return READY.mix(HIT,(age-.33)/(MeleeStrokeTiming.HIT-.33));
        if (age<2) return HIT.mix(FOLLOW,(age-MeleeStrokeTiming.HIT)/(2-MeleeStrokeTiming.HIT));
        double t=(age-2)/(duration-2);
        return t<.42 ? FOLLOW.mix(LOW,t/.42) : LOW.mix(REST,(t-.42)/.58);
    }

    public static void attack(Minecraft mc) {
        if (!ClientDataHolder.modEnabled() || !"keheng".equals(ClientDataHolder.characterId())
                || !mc.player.getMainHandItem().is(ModItems.HENGKE.get())) return;
        if (ownerIdentity!=mc.player || world!=mc.level) clearStroke();
        ownerIdentity=mc.player; world=mc.level;
        double now=mc.player.tickCount;
        entry=stroke(now); start=now;
        duration=MeleeStrokeTiming.recovery(mc.player.getCurrentItemAttackStrengthDelay());
    }

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

        if (ownerIdentity!=owner || world!=mc.level || ClientDataHolder.buffTicks("g1_stun")>0) clearStroke();
        boolean left = owner.getMainArm() == HumanoidArm.LEFT;
        int side = left ? -1 : 1;
        var motion = WeaponBodyPose.firstPersonRapier(owner, e.getPartialTick());
        double now=owner.tickCount+e.getPartialTick();
        var stab=stroke(now);
        float thrust = motion.thrust();
        var wrist = new Matrix4f().translation(side * (.56f + stab.x - .14f * thrust), -.42f + stab.y + .04f * thrust, -1.22f + stab.z - .15f * thrust)
                .mul(motion.wrist(left)).rotateY(-WeaponBodyPose.aimingTurn(owner));
        var palm = wrist.transformPosition(new Vector3f());
        WeaponHands.draw(e, left, palm, new Vector3f(side * .90f, -.90f, -.20f), side * ARM_ROLL);
        WeaponHands.support(e, left, new Vector3f(-side * .24f, -.40f, -1.14f), motion.support());

        // Turn the blade at the grip so that extension brings the tip towards the sight line.
        var axis = new Vector3f(-side * .13f, .25f, -.96f)
                .lerp(new Vector3f(-side * .23f, .13f, -.96f), Math.max(thrust,stab.aim));
        var blade = ShuoRig.basis(new Vector3f(), axis, new Vector3f(1, 0, 0), false)
                .rotateY(side * BLADE_ROLL).scale(SWORD_SCALE).translate(0, -GRIP_Y, 0);
        var pose = e.getPoseStack();
        pose.pushPose();
        try {
            var item = wrist.mul(blade);
            pose.mulPose(item);
            ClientItemRenderer.submit(owner, e.getItemStack(), ItemDisplayContext.NONE, pose,
                    e.getSubmitNodeCollector(), e.getPackedLight());
        } finally {
            pose.popPose();
        }
        double age=now-start;
        if (age>.35 && age<2.4) {
            var tip=wrist.transformPosition(new Vector3f(0,22f/16,0));
            MeleeBladeLight.needle(e,tip,side,age);
        }
        e.setCanceled(true);
        return true;
    }
}
