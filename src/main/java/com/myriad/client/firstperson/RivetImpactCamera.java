package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.client.ClientDataHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;

/** Brief world-only roll at the contact of Rivet's confirmed third hammer stroke. */
public final class RivetImpactCamera {
    private static final float STRENGTH = readStrength();
    private static final double RISE_TICKS = 0.6;
    private static final double END_TICKS = 3;
    private static double impactStart = Double.NEGATIVE_INFINITY;

    private RivetImpactCamera() {}

    private static float readStrength() {
        try {
            float value = Float.parseFloat(System.getProperty("myriad.rivetCameraStrength", "1"));
            return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f;
        } catch (NumberFormatException invalidValue) {
            return 0f;
        }
    }

    public static void confirmedSmash(double localGameTick) {
        impactStart = localGameTick;
    }

    public static void reset() {
        impactStart = Double.NEGATIVE_INFINITY;
    }

    static float rollDegrees(double age) {
        if (age <= 0 || age >= END_TICKS) return 0;
        double fraction = age < RISE_TICKS ? age / RISE_TICKS : (END_TICKS - age) / (END_TICKS - RISE_TICKS);
        return (float) (0.72 * STRENGTH * fraction * fraction * (3 - 2 * fraction));
    }

    public static void applyToWorld(PoseStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson() || mc.gui.screen() != null
                || !ClientDataHolder.modEnabled() || !"rivet".equals(ClientDataHolder.characterId())) {
            reset();
            return;
        }
        double now = mc.level.getGameTime() + (double) mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RivetWeaponView.updateImpactCamera(now);
        float roll = rollDegrees(now - impactStart);
        if (roll != 0) {
            stack.rotateDegrees(Axis.ZP, mc.player.getMainArm() == HumanoidArm.LEFT ? -roll : roll);
        }
    }
}
