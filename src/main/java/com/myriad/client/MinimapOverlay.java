package com.myriad.client;

import com.myriad.network.NetworkSetup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** 方块化战术小地图 HUD。 */
public final class MinimapOverlay {
    private MinimapOverlay() {}

    public static final int SIZE = 154;
    private static final int BORDER = 0xFFE4C56A;
    private static final int BACKGROUND = 0xC9131B22;
    private static final int GRID = 0x3F8EA5AD;
    private static final int XUANXIAO = 0xFFFF625C;
    private static final int YOUMING = 0xFF59C7FF;
    private static final Identifier MELEE_ICON = icon("melee_minion");
    private static final Identifier RANGED_ICON = icon("ranged_minion");
    private static final Identifier MAGIC_ICON = icon("magic_minion");
    private static final Identifier SIEGE_ICON = icon("siege_minion");
    private static final Identifier TOWER_ICON = icon("tower");
    private static final Identifier CRYSTAL_ICON = icon("crystal");

    public static void render(GuiGraphicsExtractor gui, Font font) {
        NetworkSetup.MinimapSnapshotPayload data = MinimapClientState.snapshot();
        if (data == null || !data.configured()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int x = MinimapConfig.x(minecraft.getWindow().getGuiScaledWidth(), SIZE);
        int y = MinimapConfig.y(minecraft.getWindow().getGuiScaledHeight(), SIZE);
        renderAt(gui, font, x, y, data);
    }

    public static boolean isHeld() {
        return Minecraft.getInstance().player != null;
    }

    public static void renderPreview(GuiGraphicsExtractor gui, Font font, int x, int y) {
        NetworkSetup.MinimapSnapshotPayload data = MinimapClientState.snapshot();
        if (data == null || !data.configured()) {
            data = new NetworkSetup.MinimapSnapshotPayload(true,
                    0, 0, 100, 0, 100, 100, 0, 100,
                    List.of(new NetworkSetup.MinimapPathPoint(0, 0, 5, 15),
                            new NetworkSetup.MinimapPathPoint(0, 1, 45, 22),
                            new NetworkSetup.MinimapPathPoint(0, 2, 95, 15),
                            new NetworkSetup.MinimapPathPoint(1, 0, 5, 50),
                            new NetworkSetup.MinimapPathPoint(1, 1, 95, 50),
                            new NetworkSetup.MinimapPathPoint(2, 0, 5, 85),
                            new NetworkSetup.MinimapPathPoint(2, 1, 55, 78),
                            new NetworkSetup.MinimapPathPoint(2, 2, 95, 85)),
                    List.of(new NetworkSetup.MinimapMarker(1, 1, 45, 54, new java.util.UUID(0, 1)),
                            new NetworkSetup.MinimapMarker(0, 1, 68, 32, new java.util.UUID(0, 2)),
                            new NetworkSetup.MinimapMarker(4, 1, 30, 66, new java.util.UUID(0, 0)),
                            new NetworkSetup.MinimapMarker(5, 2, 88, 50, new java.util.UUID(0, 0))));
        }
        renderAt(gui, font, x, y, data);
    }

    private static void renderAt(GuiGraphicsExtractor gui, Font font, int x, int y,
            NetworkSetup.MinimapSnapshotPayload data) {
        gui.fill(x, y, x + SIZE, y + SIZE, 0xDD05080B);
        gui.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, BORDER);
        gui.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, BACKGROUND);
        Component title = Component.translatable("hud.myriad.minimap");
        gui.text(font, title, x + (SIZE - font.width(title)) / 2, y + 4, 0xFFFFE6A0);

        int left = x + 5;
        int top = y + 15;
        int width = SIZE - 10;
        int height = SIZE - 31;
        renderSchematic(gui, left, top, width, height);

        Projection projection = projection(data);
        if (projection == null) return;
        renderPaths(gui, data.paths(), projection, left, top, width, height);
        // 建筑与兵线先画，玩家头像最后画，混战时仍能一眼找到队友。
        for (NetworkSetup.MinimapMarker marker : data.markers()) {
            if (marker.kind() <= 1) continue;
            renderMarker(gui, marker, projection, left, top, width, height);
        }
        for (NetworkSetup.MinimapMarker marker : data.markers()) {
            if (marker.kind() > 1) continue;
            renderMarker(gui, marker, projection, left, top, width, height);
        }
        renderLegend(gui, font, left, y + SIZE - 12);
    }

    private static void renderMarker(GuiGraphicsExtractor gui, NetworkSetup.MinimapMarker marker,
            Projection projection, int left, int top, int width, int height) {
            double[] uv = projection.project(marker.x(), marker.z());
            if (uv[0] < 0 || uv[0] > 1 || uv[1] < 0 || uv[1] > 1) return;
            int px = left + (int) Math.round(uv[0] * (width - 1));
            int py = top + (int) Math.round(uv[1] * (height - 1));
            int color = marker.factionId() == 1 ? XUANXIAO : YOUMING;
            switch (marker.kind()) {
                case 0 -> renderPlayerFace(gui, marker, px, py, 8, color, false);
                case 1 -> renderPlayerFace(gui, marker, px, py, 10, color, true);
                case 2 -> renderIcon(gui, MELEE_ICON, px, py, 7, color);
                case 3 -> renderIcon(gui, RANGED_ICON, px, py, 7, color);
                case 4 -> renderIcon(gui, TOWER_ICON, px, py, 10, color);
                case 5 -> renderIcon(gui, CRYSTAL_ICON, px, py, 12, color);
                case 6 -> renderIcon(gui, MAGIC_ICON, px, py, 8, color);
                case 7 -> renderIcon(gui, SIEGE_ICON, px, py, 9, color);
                case NetworkSetup.MinimapMarker.CAMP -> renderCamp(gui, px, py, false);
                case NetworkSetup.MinimapMarker.BOSS -> renderCamp(gui, px, py, true);
                case NetworkSetup.MinimapMarker.FOUNTAIN -> renderFountain(gui, px, py, color);
                default -> { }
            }
    }

    /** Small code-drawn pixel symbols: no world scans, textures or extra render passes. */
    private static void renderCamp(GuiGraphicsExtractor gui, int x, int y, boolean boss) {
        gui.fill(x - 4, y - 4, x + 5, y + 5, 0xDD080B0D);
        int color = boss ? 0xFFFFD36C : 0xFF84E3CF;
        if (boss) {
            gui.fill(x - 3, y - 2, x - 1, y + 3, color);
            gui.fill(x + 2, y - 2, x + 4, y + 3, color);
            gui.fill(x, y - 3, x + 1, y + 3, color);
            gui.fill(x - 3, y + 1, x + 4, y + 4, color);
        } else {
            gui.fill(x - 3, y - 3, x - 1, y - 1, color);
            gui.fill(x, y - 4, x + 2, y - 2, color);
            gui.fill(x + 3, y - 2, x + 5, y, color);
            gui.fill(x - 2, y, x + 3, y + 3, color);
            gui.fill(x - 1, y + 3, x + 2, y + 4, color);
        }
    }

    private static void renderFountain(GuiGraphicsExtractor gui, int x, int y, int color) {
        gui.fill(x - 4, y - 4, x + 5, y + 5, 0xDD080B0D);
        gui.fill(x - 3, y + 1, x + 4, y + 3, color);
        gui.fill(x - 2, y + 3, x + 3, y + 4, color);
        gui.fill(x, y - 3, x + 1, y + 1, 0xFFDCF9FF);
        gui.fill(x - 2, y - 2, x + 3, y - 1, 0xFFDCF9FF);
    }

    private static void renderLegend(GuiGraphicsExtractor gui, Font font, int x, int y) {
        for (int kind : new int[]{8, 9, 10}) {
            if (kind == 10) renderFountain(gui, x + 4, y + 4, YOUMING);
            else renderCamp(gui, x + 4, y + 4, kind == 9);
            Component label = Component.translatable("hud.myriad.minimap_" + switch (kind) {
                case 8 -> "camp";
                case 9 -> "boss";
                default -> "fountain";
            });
            gui.text(font, label, x + 11, y, 0xFFD8E4DC);
            x += 17 + font.width(label);
        }
    }

    private static void renderPlayerFace(GuiGraphicsExtractor gui, NetworkSetup.MinimapMarker marker,
            int centerX, int centerY, int size, int color, boolean self) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        gui.fill(x - 2, y - 2, x + size + 2, y + size + 2, self ? 0xFFFFFFFF : 0xFF10151A);
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, color);
        PlayerSkin skin = skin(marker.playerId());
        PlayerFaceExtractor.extractRenderState(gui, skin, x, y, size);
    }

    private static PlayerSkin skin(java.util.UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getUUID().equals(id)) return minecraft.player.getSkin();
        if (minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null && info.getSkin() != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(id);
    }

    private static void renderIcon(GuiGraphicsExtractor gui, Identifier icon, int centerX, int centerY,
            int size, int color) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xDD080B0D);
        gui.fill(x, y, x + size, y + size, color);
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, size, size);
    }

    /** 轻量战场示意底图：固定十余个矩形，不再逐像素绘制或扫描世界地形。 */
    private static void renderSchematic(GuiGraphicsExtractor gui, int left, int top, int width, int height) {
        gui.fill(left, top, left + width, top + height, 0xE5222926);
        gui.fill(left, top, left + width, top + 1, 0xFFB99B5D);
        gui.fill(left, top + height - 1, left + width, top + height, 0xFFB99B5D);
        gui.fill(left, top, left + 1, top + height, 0xFFB99B5D);
        gui.fill(left + width - 1, top, left + width, top + height, 0xFFB99B5D);
        for (int i = 1; i < 4; i++) {
            int gx = left + width * i / 4;
            int gy = top + height * i / 4;
            gui.fill(gx, top + 1, gx + 1, top + height - 1, 0x287D917F);
            gui.fill(left + 1, gy, left + width - 1, gy + 1, 0x287D917F);
        }
    }

    private static void renderPaths(GuiGraphicsExtractor gui, List<NetworkSetup.MinimapPathPoint> paths,
            Projection projection, int left, int top, int width, int height) {
        for (int lane = 0; lane < 3; lane++) {
            List<NetworkSetup.MinimapPathPoint> points = new ArrayList<>();
            for (NetworkSetup.MinimapPathPoint point : paths) if (point.lane() == lane) points.add(point);
            points.sort(Comparator.comparingInt(NetworkSetup.MinimapPathPoint::order));
            for (int i = 1; i < points.size(); i++) {
                double[] from = projection.project(points.get(i - 1).x(), points.get(i - 1).z());
                double[] to = projection.project(points.get(i).x(), points.get(i).z());
                int x1 = left + (int) Math.round(from[0] * (width - 1));
                int y1 = top + (int) Math.round(from[1] * (height - 1));
                int x2 = left + (int) Math.round(to[0] * (width - 1));
                int y2 = top + (int) Math.round(to[1] * (height - 1));
                drawLine(gui, x1, y1, x2, y2, 3, 0xB52B342D);
                drawLine(gui, x1, y1, x2, y2, 1, 0xFFD0B36B);
            }
        }
    }

    private static void drawLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2,
            int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) steps = 1;
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x1 + dx * (i / (float) steps));
            int y = Math.round(y1 + dy * (i / (float) steps));
            gui.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
        }
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("myriad", "minimap/" + name);
    }

    /** 与服务端一致：使用四条边的平均方向，减小角点落位误差。 */
    private static Projection projection(NetworkSetup.MinimapSnapshotPayload p) {
        double ux = ((p.x2() - p.x1()) + (p.x3() - p.x4())) * 0.5;
        double uz = ((p.z2() - p.z1()) + (p.z3() - p.z4())) * 0.5;
        double vx = ((p.x4() - p.x1()) + (p.x3() - p.x2())) * 0.5;
        double vz = ((p.z4() - p.z1()) + (p.z3() - p.z2())) * 0.5;
        double det = ux * vz - uz * vx;
        return Math.abs(det) < 0.001 ? null : new Projection(p.x1(), p.z1(), ux, uz, vx, vz, det);
    }

    private record Projection(double originX, double originZ,
            double ux, double uz, double vx, double vz, double determinant) {
        double[] project(double x, double z) {
            double rx = x - originX;
            double rz = z - originZ;
            return new double[] {(rx * vz - rz * vx) / determinant,
                    (ux * rz - uz * rx) / determinant};
        }
    }
}
