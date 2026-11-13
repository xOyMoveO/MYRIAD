package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端冲刺控制器（影袭协同）：
 * 收到服务端冲刺方向（含垂直）后，每 tick 在玩家 Post 事件设置本地速度，
 * 客户端平滑移动（镜头自然跟随），位置包与服务端一致。
 */
public final class DashController {
    private DashController() {}

    private static float dirX = 0;
    private static float dirY = 0;
    private static float dirZ = 0;
    private static int remainingTicks = 0;
    private static int totalTicks = 0;
    private static boolean parabolic = false;
    private static net.minecraft.world.phys.Vec3 boundedTarget;
    private static float deltaX = 0, deltaY = 0, deltaZ = 0, arcHeight = 0;
    /** 冲刺速度（与服务端一致，1.0 × 6 tick ≈ 6 格） */
    private static final double SPEED = 1.0;

    public static void start(float x, float y, float z, int ticks) {
        boundedTarget = null;
        // 负持续时间表示服务端要求精确停在技能落点。仅用于必须以落点开启
        // 后续交互的位移，普通突进仍保留自然惯性。
        if (ticks < 0) {
            remainingTicks = 0;
            totalTicks = 0;
            parabolic = false;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) player.setDeltaMovement(x, y, z);
            return;
        }
        dirX = x;
        dirY = y;
        dirZ = z;
        remainingTicks = ticks;
        totalTicks = ticks;
        parabolic = false;
        // A stop packet releases motion ownership. Vanilla friction/gravity keep
        // the handoff continuous instead of deleting the player's momentum.
    }

    public static void startParabola(float x, float y, float z, float height, int ticks) {
        boundedTarget = null;
        deltaX = x; deltaY = y; deltaZ = z; arcHeight = height;
        remainingTicks = ticks; totalTicks = Math.max(1, ticks);
        parabolic = true;
    }

    public static boolean isDashing() {
        return remainingTicks > 0;
    }

    public static void startBounded(com.myriad.network.AbilityMotionPayload motion){
        start(motion.x(),motion.y(),motion.z(),Math.clamp(motion.ticks(),1,3));
        boundedTarget=new net.minecraft.world.phys.Vec3(motion.tx(),motion.ty(),motion.tz());
    }

    /** 每 tick 调用（玩家 Post）：设置冲刺速度，结束后由原版摩擦与重力接管。 */
    public static void tick() {
        if (!ClientDataHolder.modEnabled()) { remainingTicks = 0; return; }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            remainingTicks = 0;
            return;
        }
        if (remainingTicks <= 0) {
            return;
        }
        if (parabolic) {
            int age = totalTicks - remainingTicks;
            double t0 = age / (double) totalTicks;
            double t1 = (age + 1) / (double) totalTicks;
            double arcStep = 4.0 * arcHeight * (t1 * (1.0 - t1) - t0 * (1.0 - t0));
            p.setDeltaMovement(deltaX / totalTicks, deltaY / totalTicks + arcStep, deltaZ / totalTicks);
        } else {
            var v=new net.minecraft.world.phys.Vec3(dirX*SPEED,dirY*SPEED,dirZ*SPEED);
            if(boundedTarget!=null){
                v=com.myriad.character.ability.AbilityTargeting.clampPrediction(p.position(),boundedTarget,v);
                if(v.lengthSqr()==0){remainingTicks=0;p.setDeltaMovement(0,0,0);return;}
            }
            p.setDeltaMovement(v);
        }
        remainingTicks--;
    }
}
