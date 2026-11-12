package com.myriad.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** 死亡等待期间锁定第一人称附体视角，并保存/恢复玩家原先的相机模式。 */
public final class RespawnSpectatorView {
    private static CameraType previousCameraType;
    private static boolean active;
    private static String targetName = "";

    private RespawnSpectatorView() { }

    public static void update(boolean newActive, String newTargetName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (newActive) {
            if (!active) previousCameraType = minecraft.options.getCameraType();
            active = true;
            targetName = newTargetName == null ? "" : newTargetName;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            reset();
        }
    }

    /** 防止死亡观战时用切换视角键脱离“直接附体”。 */
    public static void enforceFirstPerson() {
        if (active) Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
    }

    public static boolean isActive() {
        return active;
    }

    public static String targetName() {
        return targetName;
    }

    public static void reset() {
        if (active && previousCameraType != null) {
            Minecraft.getInstance().options.setCameraType(previousCameraType);
        }
        previousCameraType = null;
        active = false;
        targetName = "";
    }
}
