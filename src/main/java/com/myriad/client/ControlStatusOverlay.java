package com.myriad.client;

import com.myriad.network.ControlStatusPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact, stacked reason cards. All flags come from the owner's server snapshot, never an animation. */
public final class ControlStatusOverlay {
    private ControlStatusOverlay() {}
    private static ControlStatusPayload latest = ControlStatusPayload.empty();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long received;
    public static void reset() { latest = ControlStatusPayload.empty(); world = null; received = 0; }
    public static void receive(ControlStatusPayload p) {
        var mc = Minecraft.getInstance(); latest = p; world = mc.level; received = world == null ? 0 : world.getGameTime();
    }
    public static ControlStatusPayload current() {
        var mc = Minecraft.getInstance();
        if (!ClientDataHolder.modEnabled() || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.level == null || mc.level != world || !latest.role().equals(ClientDataHolder.characterId())
                || !latest.dimension().equals(mc.level.dimension().identifier().toString())) return ControlStatusPayload.empty();
        return latest.elapsed((int)Math.clamp(mc.level.getGameTime() - received, -1, Integer.MAX_VALUE));
    }
    private record Row(Component text, int color) {}
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20d); }
    public static void render(GuiGraphicsExtractor gui, Font font) {
        var s = current(); if (!s.active()) return;
        var rows = new ArrayList<Row>();
        if (s.stun() > 0) rows.add(new Row(Component.translatable("control.myriad.stun", seconds(s.stun())), 0xFFFFCC6A));
        if (s.root() > 0) rows.add(new Row(Component.translatable("control.myriad.root", seconds(s.root())), 0xFF9CDFFF));
        if (s.silence() > 0) rows.add(new Row(Component.translatable("control.myriad.silence", seconds(s.silence())), 0xFFD5AEFF));
        if (s.actionRate() < 1) rows.add(new Row(Component.translatable("control.myriad.action_slow", Math.max(1, Math.round(s.actionRate() * 100))), 0xFF92EAE5));
        if (s.charge() > 0) rows.add(new Row(Component.translatable(s.walkFactor() == 0 ? "control.myriad.charge_still" : "control.myriad.charge_walk",
                Component.translatable("skill.myriad." + s.chargeSkill()), seconds(s.charge()), Math.round(s.walkFactor() * 100)), 0xFFB5CFFF));
        else if (s.motion() > 0) rows.add(new Row(Component.translatable("control.myriad.motion"), 0xFFB5CFFF));
        var mc = Minecraft.getInstance(); int width = Math.max(80, Math.min(312, mc.getWindow().getGuiScaledWidth() - 18));
        var wrapped = rows.stream().map(row -> font.split(row.text, width - 16)).toList();
        int height = wrapped.stream().mapToInt(p -> p.size() * 11 + 9).sum() + 5;
        float scale = Math.min(1, mc.getWindow().getGuiScaledHeight() * .32f / Math.max(1, height));
        float x = mc.getWindow().getGuiScaledWidth() * .5f, top = mc.getWindow().getGuiScaledHeight() * .14f;
        gui.pose().pushMatrix(); gui.pose().translate(x, top); gui.pose().scale(scale);
        int left = -width / 2; gui.fill(left, 0, left + width, height, 0xE5101926);
        int y = 5;
        for (int i = 0; i < rows.size(); i++) {
            gui.fill(left, y-1, left+2, y + wrapped.get(i).size() * 11, rows.get(i).color);
            for (var line : wrapped.get(i)) { gui.text(font, line, left+8, y, rows.get(i).color); y += 11; }
            y += 9;
        }
        gui.pose().popMatrix();
    }
}
