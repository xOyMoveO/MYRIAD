package com.myriad.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** 寒泽【千里冰封】期间：相机升至角色头顶 10 格并垂直俯视，结束后恢复玩家原本视角。 */
public final class HanzeFrostView {
    private static final double CAMERA_DISTANCE = 10.0;
    private static final Identifier CAMERA_MOD = Identifier.fromNamespaceAndPath("myriad", "hanze_frost_camera");
    private static int endTick;
    private static CameraType previousCameraType;
    private static double previousDistance = -1.0;

    private HanzeFrostView() { }

    public static void start(int durationTicks) {
        if (durationTicks <= 0) {
            end();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (previousCameraType == null) previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        AttributeInstance distance = player.getAttribute(Attributes.CAMERA_DISTANCE);
        if (distance != null) {
            if (previousDistance < 0) previousDistance = distance.getBaseValue();
            distance.removeModifier(CAMERA_MOD);
            distance.addTransientModifier(new AttributeModifier(CAMERA_MOD, CAMERA_DISTANCE - previousDistance,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        endTick = player.tickCount + durationTicks;
    }

    private static boolean active() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (endTick == 0 || player == null) return false;
        if (player.tickCount >= endTick) {
            end();
            return false;
        }
        return true;
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        // 领域相机仅拉远到头顶视距，不覆写 yaw/pitch，玩家可自由调整朝向。
        active();
    }

    private static void end() {
        endTick = 0;
        Minecraft mc = Minecraft.getInstance();
        if (previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }
        LocalPlayer player = mc.player;
        if (player != null && previousDistance >= 0) {
            AttributeInstance distance = player.getAttribute(Attributes.CAMERA_DISTANCE);
            if (distance != null) {
                distance.removeModifier(CAMERA_MOD);
                distance.setBaseValue(previousDistance);
            }
        }
        previousDistance = -1.0;
    }
}
