package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Project the independently aimed muzzle onto the viewport. */
public final class YikeHud {
    private YikeHud() {}

    public static void drawMuzzle(GuiGraphicsExtractor gui, Font font) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player || mc.gui.hud.isHidden()
                || !CombatHudState.suppressesVanillaStatus() || !"yike".equals(ClientDataHolder.characterId())) return;
        var state = YikeClientState.get(mc.player.getId());
        if (state == null || state.mode() == 0) return;

        int width = gui.guiWidth();
        int height = gui.guiHeight();
        Vec3 forward = mc.player.getLookAngle();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(forward).normalize();
        double z = state.aim().dot(forward);
        double scale = height / (2 * Math.tan(Math.toRadians(mc.options.fov().get() * .5)));
        int rx = (int)(width * .5 + state.aim().dot(right) / Math.max(.05, z) * scale);
        int ry = (int)(height * .5 - state.aim().dot(up) / Math.max(.05, z) * scale);
        rx = Math.clamp(rx, 14, width - 14);
        ry = Math.clamp(ry, 14, height - 14);
        int color = z < .99 ? 0xFFFF6AAE : 0xFF5DDEFF;
        gui.fill(rx - 5, ry - 5, rx - 2, ry - 4, color);
        gui.fill(rx + 2, ry - 5, rx + 5, ry - 4, color);
        gui.fill(rx - 5, ry + 4, rx - 2, ry + 5, color);
        gui.fill(rx + 2, ry + 4, rx + 5, ry + 5, color);
        if (z < .95) {
            var label = CombatHudOverlay.label("yike_aiming");
            gui.text(font, label, Math.clamp(rx - font.width(label) / 2, 0, Math.max(0, width - font.width(label))),
                    ry + 9, color);
        }
    }
}
