package com.myriad.client.gui;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.SkillHoverDetails;
import com.myriad.client.SkillIcon;
import com.myriad.client.gui.SkillChoiceLayout.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** A frame-local drawing. Its bounds are never registered as an input or focus target. */
final class SkillHoverCard {
    private static final int PAPER = 0xF90B1A22, EDGE = 0xFF90754E, TEXT = 0xFFE1E9E8;
    private static final int JADE = 0xFF95D5C5, AMBER = 0xFFF0CC8D, MUTED = 0xFFA9C2BD;
    private static final Identifier DEFAULT_SKIN = skin("default");
    private static final Map<String, Identifier> ROLE_SKINS = FirstGroupSkills.ROLES.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), SkillHoverCard::skin));

    private static final Identifier FRAME = skin("scene_frame");
    private static final Map<String, Identifier> ROLE_SCENES = FirstGroupSkills.ROLES.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), role -> skin("scene/" + role)));

    private record Line(int x, int y, FormattedCharSequence text, int color) {}
    private record Face(int width, int height, int pad, int icon, int stripTop, int stripBottom, int textRight, List<Line> lines) {}
    private record Placement(Box box, Face face, float scale, int side, int overlap) {}

    private SkillHoverCard() {}

    static void draw(GuiGraphicsExtractor g, Font font, SkillChoiceLayout layout, int width,
            AbilitySkill skill, SkillHoverDetails info) {
        Box anchor = layout.skillIcon(skill.index()).intersect(layout.viewport());
        var regions = SkillChoiceTooltipLayout.skillRegions(width, layout.footerDividerY(), anchor);
        Placement best = choose(font, layout, anchor, regions, info, false);
        if (best == null) best = choose(font, layout, anchor, regions, info, true);
        if (best == null) return;
        var box = best.box;
        var face = best.face;
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate(box.x(), box.y());
        g.pose().scale(best.scale);
        int w = face.width, h = face.height, pad = face.pad;
        g.fill(1, 2, w + 1, h + 2, 0x88030A0D);
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        Identifier background = availableSkin(atlas, skill.role());
        if (background != null && background.equals(ROLE_SCENES.get(skill.role()))) {
            drawScene(g, atlas, background, face);
        } else if (background != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, background, 0, 0, w, h);
        } else {
            g.fill(0, 0, w, h, EDGE);
            g.fill(1, 1, w - 1, h - 1, PAPER);
            for (int x : new int[]{2, w - 6}) {
                for (int y : new int[]{2, h - 3}) g.fill(x, y, x + 4, y + 1, JADE);
            }
        }
        if (face.stripBottom > face.stripTop)
            g.fill(pad - 2, face.stripTop, face.textRight + 2, face.stripBottom, 0x95172D32);
        SkillIcon.drawArtwork(g, font, pad, pad, face.icon, skill);
        for (var line : face.lines) g.text(font, line.text, line.x, line.y, line.color);
        g.pose().popMatrix();
    }

    private static void drawScene(GuiGraphicsExtractor g, TextureAtlas atlas, Identifier scene, Face face) {
        int w = face.width, h = face.height;
        g.fill(0, 0, w, h, PAPER);
        int innerW = w - 4, innerH = h - 4;
        int imageW = Math.max(innerW, innerH * 2);
        int imageH = (imageW + 1) / 2;
        // Crop the left and, when necessary, the top and bottom equally. Never nine-slice the landscape.
        g.blitSprite(RenderPipelines.GUI_TEXTURED, scene, imageW, imageH,
                imageW - innerW, (imageH - innerH) / 2, 2, 2, innerW, innerH);
        int artTextEdge = 2 + Math.round(imageW * .60f) - (imageW - innerW);
        if (face.textRight > artTextEdge + 4) {
            // When text crosses into the scenery, feather the reading area into the exposed art.
            int right = face.textRight + 1;
            g.fill(2, 2, right, h - 2, 0xCE09151D);
            int fade = Math.min(28, w - 2 - right);
            for (int i = 0; i < fade; i++) {
                int alpha = 206 * (fade - i) / (fade + 1);
                g.fill(right + i, 2, right + i + 1, h - 2, alpha << 24 | 0x09151D);
            }
        }
        if (atlas.getSprite(FRAME) != atlas.missingSprite())
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, w, h);
        else g.outline(0, 0, w, h, EDGE);
    }

    private static Identifier skin(String role) {
        return Identifier.fromNamespaceAndPath("myriad", "skill_choice/tooltip/" + role);
    }

    private static Identifier availableSkin(TextureAtlas atlas, String role) {
        Identifier scene = ROLE_SCENES.get(role);
        if (scene != null && atlas.getSprite(scene) != atlas.missingSprite()) return scene;
        Identifier skin = ROLE_SKINS.getOrDefault(role, DEFAULT_SKIN);
        if (atlas.getSprite(skin) != atlas.missingSprite()) return skin;
        return atlas.getSprite(DEFAULT_SKIN) != atlas.missingSprite() ? DEFAULT_SKIN : null;
    }

    private static Placement choose(Font font, SkillChoiceLayout layout, Box anchor, List<Box> regions,
            SkillHoverDetails info, boolean compact) {
        Placement best = null;
        for (int side = 0; side < regions.size(); side++) {
            Box region = regions.get(side);
            if (region.w() < (compact ? 32 : 158) || region.h() < 16) continue;
            int width = Math.clamp(region.w(), compact ? 148 : 158, compact ? 310 : 368);
            Face face = measure(font, info, width, compact);
            float scale = Math.min(1f, Math.min(region.w() / (float)width, region.h() / (float)face.height));
            if (!compact && scale < 1f) continue;
            int w = Math.min(region.w(), (int)Math.ceil(width * scale));
            int h = Math.min(region.h(), (int)Math.ceil(face.height * scale));
            int x = switch (side) {
                case 0 -> region.x();
                case 1 -> region.right() - w;
                default -> Math.clamp(anchor.x(), region.x(), region.right() - w);
            };
            int y = switch (side) {
                case 2 -> region.bottom() - h;
                case 3 -> region.y();
                default -> Math.clamp(anchor.y(), region.y(), region.bottom() - h);
            };
            Box box = new Box(x, y, w, h);
            int overlap = 0;
            for (int i = 0; i < 8; i++) {
                var covered = box.intersect(layout.skillIcon(i)).intersect(layout.viewport());
                overlap += covered.w() * covered.h();
            }
            var option = new Placement(box, face, scale, side, overlap);
            if (best == null || better(option, best)) best = option;
            if (best.side < 2 && best.face.width == 368 && best.scale == 1f && best.overlap == 0) return best;
        }
        return best;
    }

    private static boolean better(Placement a, Placement b) {
        if (a.scale != b.scale) return a.scale > b.scale;
        if (a.side / 2 != b.side / 2) return a.side / 2 < b.side / 2;
        if ((a.face.width >= 300) != (b.face.width >= 300)) return a.face.width >= 300;
        return a.overlap < b.overlap;
    }

    private static Face measure(Font font, SkillHoverDetails info, int w, boolean compact) {
        int pad = compact ? 6 : 9, gap = compact ? 3 : 5, icon = compact ? 14 : 19;
        int textRight = Math.min(w - pad, Math.max(compact ? 176 : 184, Math.round(w * .60f)));
        int inner = textRight - pad, titleX = pad + icon + 5;
        var lines = new ArrayList<Line>();
        int y = append(lines, font, info.name(), titleX, pad, textRight - titleX, SkillChoiceTheme.GOLD);
        if (!compact) y = append(lines, font, info.group(), titleX, y, textRight - titleX, MUTED);
        y = Math.max(pad + icon, y) + 2;
        y = append(lines, font, info.rank(), pad, y, inner, info.locked() ? AMBER : MUTED) + gap;
        y = append(lines, font, info.summary(), pad, y, inner, TEXT);
        int stripTop = y + gap;
        y = stripTop + 2;
        if (!info.costs().isEmpty()) y = append(lines, font, info.costs(), pad, y, inner, JADE);
        for (String metric : info.metrics()) y = append(lines, font, metric, pad, y, inner, JADE);
        int stripBottom = y + 1;
        y = stripBottom;
        if (!info.condition().isEmpty()) y = append(lines, font, info.condition(), pad, y + gap, inner, AMBER);
        if (!info.usual().isEmpty()) y = append(lines, font, info.usual(), pad, y + gap, inner, MUTED);
        int height = y + pad - 1;
        if (!compact && w >= 280) height = Math.max(height, w / 2);
        return new Face(w, height, pad, icon, stripTop, stripBottom, textRight, lines);
    }

    private static int append(List<Line> target, Font font, String value, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(value), width)) {
            target.add(new Line(x, y, line, color));
            y += font.lineHeight + 1;
        }
        return y;
    }
}
