package com.myriad.client;

import com.myriad.Myriad;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LowHealthOverlay {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Myriad.MODID, "textures/gui/low_health_vignette.png");
    private static final int TEXTURE_SIZE = 256;
    private static final float LOW_HEALTH_RATIO = 0.20f;
    private static final long PULSE_PERIOD_NANOS = 1_400_000_000L;

    private LowHealthOverlay() {}

    public static void render(GuiGraphicsExtractor gui, DeltaTracker delta) {
        var data = ClientDataHolder.get();
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (data == null || !data.modEnabled() || data.characterId().isEmpty()
                || player == null || !player.isAlive() || player.isSpectator()
                || mc.getCameraEntity() != player || mc.gui.screen() != null) {
            return;
        }

        int alpha = opacity(data.health(), data.maxHealth(), System.nanoTime());
        if (alpha > 0) {
            gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, gui.guiWidth(), gui.guiHeight(),
                    TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, (alpha << 24) | 0xFFFFFF);
        }
    }

    private static int opacity(float health, float maxHealth, long now) {
        float threshold = maxHealth * LOW_HEALTH_RATIO;
        if (!(health > 0 && health < threshold)) {
            return 0;
        }

        float severity = 1f - health / threshold;
        float peak = Math.min(1f, severity * 8f) * (0.22f + 0.44f * severity);
        double phase = Math.floorMod(now, PULSE_PERIOD_NANOS) / (double) PULSE_PERIOD_NANOS;
        double pulse = 0.64 - 0.36 * Math.cos(phase * Math.PI * 2.0);
        return (int) Math.round(255 * peak * pulse);
    }
}
