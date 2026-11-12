package com.myriad.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 周天覆阵引导视角：灵魂出窍式——相机移到身侧（绕实体旋转 90°），略微俯视看向自己。
 * 服务端在引导开始时发送 ZhouTianChannelPayload（引导时长），结束时发送时长 0 立即复位。
 */
public final class ZhoutianChannelView {
    private ZhoutianChannelView() {}

    /** 身侧相机距离（格）：拉远以看到半径 8 格阵点全貌 */
    private static final double CAMERA_DISTANCE = 12.0;
    /** 绕实体旋转角度（度）：移到正侧方 */
    private static final float YAW_OFFSET = 90.0f;
    private static final Identifier CAM_DIST_MOD =
            Identifier.fromNamespaceAndPath("myriad", "zhoutian_cam_dist");

    private static int endTick = 0;
    private static int startTick = 0;
    private static CameraType previousCameraType = null;
    private static double previousDistance = -1.0;

    public static void start(int durationTicks) {
        if (durationTicks <= 0) {
            end();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        AttributeInstance inst = p.getAttribute(Attributes.CAMERA_DISTANCE);
        if (inst != null) {
            previousDistance = inst.getBaseValue();
            inst.removeModifier(CAM_DIST_MOD);
            inst.addTransientModifier(new AttributeModifier(
                    CAM_DIST_MOD, CAMERA_DISTANCE - previousDistance,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        startTick = p.tickCount;
        endTick = startTick + Math.max(1, durationTicks);
    }

    public static boolean isActive() {
        if (endTick == 0) {
            return false;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.tickCount >= endTick) {
            end();
            return false;
        }
        return true;
    }

    /** 引导进度 0~1；非引导状态返回 -1 */
    public static float progress() {
        if (endTick == 0) {
            return -1.0f;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return -1.0f;
        }
        int total = endTick - startTick;
        if (total <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) (p.tickCount - startTick) / total));
    }

    /** 相机回调：绕实体转到侧方看向自己，pitch 32°→18° 俯瞰全貌 */
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!isActive()) {
            return;
        }
        float prog = progress();
        if (prog < 0.0f) {
            return;
        }
        event.setYaw(event.getYaw() + YAW_OFFSET);
        event.setPitch(32.0f * (1.0f - 0.4375f * prog));
    }

    private static void end() {
        endTick = 0;
        Minecraft mc = Minecraft.getInstance();
        if (previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }
        LocalPlayer p = mc.player;
        if (p != null && previousDistance >= 0.0) {
            AttributeInstance inst = p.getAttribute(Attributes.CAMERA_DISTANCE);
            if (inst != null) {
                inst.removeModifier(CAM_DIST_MOD);
                inst.setBaseValue(previousDistance);
            }
            previousDistance = -1.0;
        }
    }
}