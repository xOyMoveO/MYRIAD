package com.myriad.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/**
 * 伤害飘字（仅对伤害来源玩家可见）：
 * 在生成时把命中实体的世界坐标投影到屏幕固定位置，之后只做向上飘动与淡出，
 * 不会因相机/实体移动而左右晃动。
 * 颜色按伤害类型区分：物理=红，法术=蓝，真实=白。
 */
public final class DamageFloater {
    public static final int TYPE_PHYSICAL = 0;
    public static final int TYPE_MAGIC = 1;
    public static final int TYPE_TRUE = 2;

    private static final int LIFETIME_TICKS = 20;
    private static final float CRIT_SCALE = 1.5f;
    /** 同屏最多同时显示的飘字数（攻速过快时丢弃最旧的，避免叠出屏幕） */
    private static final int MAX_ENTRIES = 12;
    /** 纵向错开上限（最多错开几层） */
    private static final int MAX_STACK = 4;
    private static final List<FloatEntry> entries = new CopyOnWriteArrayList<>();

    private record FloatEntry(int type, int amount, boolean crit, float bornTick, int screenX, int screenY, int stackY) {
    }

    private DamageFloater() {
    }

    /** 网络包到达时调用（客户端）：投影命中位置到屏幕并固定，同一实体同时多次伤害时纵向错开 */
    public static void add(int type, int amount, boolean crit, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        DpsTracker.record(amount);
        int screenX = -1;
        int screenY = -1;
        try {
            net.minecraft.client.renderer.GameRenderer gr = mc.gameRenderer;
            net.minecraft.client.Camera camera = gr.mainCamera();
            Vec3 point = new Vec3(x, y, z);
            Vec3 offset = point.subtract(camera.position());
            Vec3 forward = new Vec3(camera.forwardVector());
            if (offset.dot(forward) > 0.0) {
                Vec3 ndc = gr.projectPointToScreen(point);
                screenX = (int) ((ndc.x + 1.0) * 0.5 * mc.getWindow().getGuiScaledWidth());
                screenY = (int) ((1.0 - ndc.y) * 0.5 * mc.getWindow().getGuiScaledHeight());
            }
        } catch (Exception ignored) {
        }
        int stack = 0;
        for (FloatEntry e : entries) {
            if (mc.player.tickCount - e.bornTick() >= LIFETIME_TICKS) {
                continue;
            }
            // 屏幕位置接近视为同一点，计入纵向错开（有上限）
            if (Math.abs(e.screenX() - screenX) < 18 && Math.abs(e.screenY() - screenY) < 18) {
                stack++;
            }
        }
        if (stack > MAX_STACK) {
            stack = MAX_STACK;
        }
        entries.add(new FloatEntry(type, amount, crit, mc.player.tickCount, screenX, screenY, stack * 11));
        // 飘字过多时丢弃最旧的，避免大量飘字堆叠/飞出屏幕
        while (entries.size() > MAX_ENTRIES) {
            FloatEntry oldest = null;
            for (FloatEntry e : entries) {
                if (oldest == null || e.bornTick() < oldest.bornTick()) {
                    oldest = e;
                }
            }
            if (oldest != null) {
                entries.remove(oldest);
            } else {
                break;
            }
        }
    }

    /** HUD 层调用：渲染所有活动飘字（固定位置向上飘 + 淡出）；暴击粗体放大 */
    public static void render(GuiGraphicsExtractor gui, Font font, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entries.isEmpty()) {
            return;
        }
        float now = mc.player.tickCount + partialTick;
        entries.removeIf(e -> (now - e.bornTick()) >= LIFETIME_TICKS);
        for (FloatEntry e : entries) {
            if (e.screenX() < 0 || e.screenY() < 0) {
                continue; // 生成时在相机后方，跳过
            }
            float age = now - e.bornTick();
            if (age < 0) {
                continue;
            }
            int alpha = (int) (255 * (1.0f - age / LIFETIME_TICKS));
            int rgb = switch (e.type()) {
                case TYPE_MAGIC -> 0x55AAFF;
                case TYPE_TRUE -> 0xFFFFFF;
                default -> 0xFF4444;
            };
            int color = (alpha << 24) | rgb;
            int x = e.screenX();
            int y = e.screenY() - 16 - (int) (age * 1.6f) - e.stackY();
            // 限制在屏幕范围内，避免攻速过快时飘字飞离屏幕
            y = Math.max(6, y);
            y = Math.min(mc.getWindow().getGuiScaledHeight() - 8, y);
            String text = String.valueOf(e.amount());
            if (e.crit()) {
                // 暴击：按伤害类型着色（物理=红 / 法术=蓝 / 真实=白）+ 加粗 + 弹性放大，更醒目
                float t = age / LIFETIME_TICKS;
                float bounce = 1.0f + 0.7f * (float) Math.exp(-t * 5.0f);
                float scale = CRIT_SCALE * bounce;
                gui.pose().pushMatrix();
                gui.pose().translate(x, y);
                // 光晕：放大 1.3 倍半透明同色文字垫底
                gui.pose().pushMatrix();
                gui.pose().scale(scale * 1.3f);
                gui.text(font, net.minecraft.network.chat.Component.literal(text),
                        -font.width(text) / 2, 0, (alpha / 3 << 24) | rgb);
                gui.pose().popMatrix();
                gui.pose().scale(scale);
                gui.text(font, net.minecraft.network.chat.Component.literal(text)
                                .withStyle(net.minecraft.ChatFormatting.BOLD),
                        -font.width(text) / 2, 0, color);
                gui.pose().popMatrix();
            } else {
                gui.text(font, text, x - font.width(text) / 2, y, color);
            }
        }
    }
}
