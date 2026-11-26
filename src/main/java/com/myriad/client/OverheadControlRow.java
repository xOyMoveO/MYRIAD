package com.myriad.client;

import com.myriad.network.EnemyHealthPayload.Controls;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Projected positions use the health bar's GUI coordinates, before the player's HUD transform. */
final class OverheadControlRow {
    private static final int SIZE = 12, GAP = 2, TOP = -35;
    record Bounds(float left, float top, float right, float bottom) {
        boolean overlaps(Bounds other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }
    }
    private enum Icon {
        STUN("stun", 0xFFFFCF75), SILENCE("silence", 0xFFE5C4FF), ROOT("root", 0xFFADEDE6);
        final Identifier sprite;
        final int color;
        Icon(String name, int color) {
            sprite = Identifier.fromNamespaceAndPath("myriad", "control_status/" + name);
            this.color = color;
        }
        int ticks(Controls controls) {
            return switch (this) {
                case STUN -> controls.stun();
                case SILENCE -> controls.silence();
                case ROOT -> controls.root();
            };
        }
        void draw(GuiGraphicsExtractor gui, int x, float remaining) {
            var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
            if (atlas.getSprite(sprite) != atlas.missingSprite()) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, TOP, SIZE, SIZE);
            } else {
                // Three geometric silhouettes remain distinct without a texture pack.
                switch (this) {
                    case STUN -> { gui.fill(x+5, TOP+2, x+7, TOP+10, color); gui.fill(x+2, TOP+5, x+10, TOP+7, color); }
                    case SILENCE -> { gui.fill(x+2, TOP+3, x+10, TOP+5, color); gui.fill(x+2, TOP+7, x+10, TOP+9, color); }
                    case ROOT -> { gui.fill(x+2, TOP+3, x+4, TOP+9, color); gui.fill(x+8, TOP+3, x+10, TOP+9, color);
                        gui.fill(x+4, TOP+7, x+8, TOP+9, color); }
                }
            }
            border(gui, x, TOP, 1, 0xFF202C34);
            border(gui, x, TOP, Math.clamp(remaining / 40f, 0f, 1f), color);
        }
    }

    // Clockwise rounded perimeter, starting at twelve o'clock. The dark track stays visible on sky.
    private record Edge(int x, int y, int dx, int dy, int length) {}
    private static final List<Edge> BORDER = List.of(new Edge(6,0,1,0,3), new Edge(9,1,1,1,2),
            new Edge(11,3,0,1,6), new Edge(10,9,-1,1,2), new Edge(8,11,-1,0,6),
            new Edge(2,10,-1,-1,2), new Edge(0,8,0,-1,6), new Edge(1,2,1,-1,2), new Edge(3,0,1,0,3));
    private static void border(GuiGraphicsExtractor gui, int x, int y, float fill, int color) {
        int pixels = (int)Math.ceil(fill * 32);
        for (Edge edge : BORDER) {
            int count = Math.min(pixels, edge.length);
            if (count <= 0) break;
            int ax = x + edge.x, ay = y + edge.y;
            if (edge.dx != 0 && edge.dy != 0) {
                for (int i = 0; i < count; i++) gui.fill(ax+i*edge.dx, ay+i*edge.dy, ax+i*edge.dx+1, ay+i*edge.dy+1, color);
            } else {
                int bx = ax + (count-1)*edge.dx, by = ay + (count-1)*edge.dy;
                gui.fill(Math.min(ax,bx), Math.min(ay,by), Math.max(ax,bx)+1, Math.max(ay,by)+1, color);
            }
            pixels -= count;
        }
    }

    private record Candidate(int id, float distance, int x, int y, float scale, Bounds bounds, List<Icon> icons, Controls controls) {}
    private final List<Bounds> plates = new ArrayList<>();
    private final List<Candidate> rows = new ArrayList<>();

    void protect(int x, int y, float scale, int textWidth, int bottom) {
        float halfWidth = Math.max(31, (textWidth + 2) / 2f);
        plates.add(new Bounds(x-halfWidth*scale, y-21*scale, x+halfWidth*scale, y+bottom*scale));
    }

    void offer(int id, float distance, int x, int y, float scale, float hudScale, Controls controls, float age) {
        float pixels = SIZE * scale * hudScale;
        if (pixels < 6 || !controls.active()) return;
        var icons = new ArrayList<Icon>(3);
        for (Icon icon : Icon.values()) {
            if (icon.ticks(controls) <= age) continue;
            icons.add(icon);
            if (pixels < 8) break;
        }
        if (icons.isEmpty()) return;
        float half = rowWidth(icons.size()) * scale / 2;
        rows.add(new Candidate(id, distance, x, y, scale, new Bounds(x-half, y+TOP*scale,
                x+half, y+(TOP+SIZE)*scale), icons, controls));
    }

    private static int rowWidth(int count) { return count * (SIZE + GAP) - GAP; }

    void draw(GuiGraphicsExtractor gui, int width, int height, float age) {
        rows.sort(Comparator.comparingDouble(Candidate::distance).thenComparingInt(Candidate::id));
        var pose = gui.pose();
        for (Candidate row : rows) {
            var b = row.bounds;
            if (b.left*pose.m00()+pose.m20() < 0 || b.right*pose.m00()+pose.m20() > width
                    || b.top*pose.m11()+pose.m21() < 0 || b.bottom*pose.m11()+pose.m21() > height
                    || plates.stream().anyMatch(b::overlaps)) continue;
            plates.add(b);
            pose.pushMatrix();
            pose.translate(row.x, row.y);
            pose.scale(row.scale);
            int left = -rowWidth(row.icons.size())/2;
            for (Icon icon : row.icons) {
                icon.draw(gui, left, icon.ticks(row.controls)-age);
                left += SIZE + GAP;
            }
            pose.popMatrix();
        }
    }
}
