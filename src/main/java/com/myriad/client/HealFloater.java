package com.myriad.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 回复飘字（吸血/回血）：在 HUD 生命值上方显示绿色 +N，向上浮动并淡出。
 */
public final class HealFloater {
    private static final int LIFETIME_TICKS = 20;
    private static final List<HealEntry> entries = new CopyOnWriteArrayList<>();

    private record HealEntry(int amount, float bornTick) {
    }

    private HealFloater() {
    }

    /** 网络包到达时调用（客户端） */
    public static void add(int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        entries.add(new HealEntry(amount, mc.player.tickCount));
    }

    /** HUD 层调用：在生命值上方渲染 */
    public static void render(GuiGraphicsExtractor gui, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entries.isEmpty()) {
            return;
        }
        float now = mc.player.tickCount;
        entries.removeIf(e -> (now - e.bornTick()) >= LIFETIME_TICKS);
        int baseX = mc.getWindow().getGuiScaledWidth() / 2 - 91;
        int baseY = mc.getWindow().getGuiScaledHeight() - 47;
        for (HealEntry e : entries) {
            float age = now - e.bornTick();
            if (age < 0) {
                continue;
            }
            int alpha = (int) (255 * (1.0f - age / LIFETIME_TICKS));
            int color = (alpha << 24) | 0x55FF55;
            String text = "+" + e.amount();
            int x = baseX + 20;
            int y = baseY - 16 - (int) (age * 1.2f);
            gui.text(font, text, x, y, color);
        }
    }
}