package com.myriad.game;

import com.myriad.client.gui.SkillChoiceInteraction;
import com.myriad.client.gui.SkillChoiceLayout;
import com.myriad.client.gui.SkillChoiceTooltipLayout;
import com.myriad.character.rework.FirstGroupSkills;
import net.minecraft.gametest.framework.GameTestHelper;
import javax.imageio.ImageIO;

/** Geometry and input contracts; actual Minecraft font and mouse acceptance remain separate. */
final class SkillChoiceUiChecks {
    static void verify(GameTestHelper h) {
        int states = 0;
        int tooltipStates = 0;
        for (int width : new int[]{320, 384, 426, 427, 480, 576, 640, 960}) {
            for (int height : new int[]{180, 240, 270, 344, 360, 480}) {
                var base = SkillChoiceLayout.create(width, height, 0);
                var panel = base.panel();
                h.assertTrue(panel.w() <= 300, "Panel width follows the compact content limit");
                h.assertTrue(base.titleY() > panel.y() + panel.h() * .18 && base.viewport().y() >= base.statusY() + 12,
                        "Header clears the complete sword crest before the four rows begin");
                h.assertTrue(base.primary().bottom() <= panel.bottom() - 14,
                        "Selected action stays above the complete bottom artwork frame");
                h.assertTrue(panel.x() >= 0 && panel.right() <= width
                        && panel.y() >= 0 && panel.bottom() <= height, "Panel stays on screen");
                for (int index = 0; index < 3; index++) {
                    var tab = base.tab(index);
                    h.assertTrue(tab.x() < base.viewport().x() && tab.y() >= panel.y()
                            && tab.bottom() < panel.bottom(), "Navigation stays at the far left");
                    if (index > 0) {
                        h.assertTrue(base.tab(index - 1).bottom() < tab.y(), "Navigation icons have a clear gap");
                    }
                }
                h.assertTrue(base.primary().y() >= base.viewport().bottom()
                        && base.primary().bottom() < panel.bottom(), "Independent action is fixed below the scroll area");
                if (height >= 180) {
                    h.assertTrue(base.maxY() == 0, "All four skill rows fit at every supported GUI height");
                }
                for (int requested : new int[]{-999, 9999}) {
                    var layout = SkillChoiceLayout.create(width, height, requested);
                    var view = layout.viewport();
                    h.assertTrue(layout.scrollY() >= 0 && layout.scrollY() <= layout.maxY(), "Scroll clamps after resize");
                    for (int row = 0; row < 4; row++) {
                        int first = SkillChoiceLayout.firstIndex(row);
                        var a = layout.card(first);
                        var b = layout.card(first + 1);
                        h.assertTrue(a.y() == b.y() && a.right() < b.x(), "Each category has two separate icons in one row");
                        h.assertTrue(a.y() >= view.y() && a.bottom() <= view.bottom(), "Every full icon and rank baseline stays inside the viewport");
                        h.assertTrue(a.x() - layout.category(first / 2).right() <= 8 && b.x() - a.right() <= 12, "Category and candidates stay close instead of stretching to fill width");
                        h.assertTrue(a.x() >= view.x() && b.right() <= view.right(), "Both candidates fit horizontally");
                        h.assertTrue(layout.category(first / 2).right() < a.x(), "Category labels align beside their icon pair");
                        if (row > 0) {
                            h.assertTrue(layout.card(SkillChoiceLayout.firstIndex(row - 1)).bottom() < a.y(), "The four categories do not overlap");
                        }
                        h.assertTrue(!layout.canHit(a, a.x() + 1, view.y() - 1)
                                && !layout.canHit(b, view.right(), b.y() + 1), "Clipped icons never receive clicks");
                    }
                    h.assertTrue(SkillChoiceLayout.firstIndex(0) == 0 && SkillChoiceLayout.firstIndex(1) == 4
                            && SkillChoiceLayout.firstIndex(2) == 2 && SkillChoiceLayout.firstIndex(3) == 6,
                            "Row order is passive, major, regular, ultimate");
                    var track = layout.verticalTrack();
                    var thumb = layout.verticalThumb();
                    h.assertTrue(thumb.y() >= track.y() && thumb.bottom() <= track.bottom(), "Scrollbar handle stays in its track");
                    h.assertTrue(layout.scrollToVertical(-999) == 0
                            && layout.scrollToVertical(99999) == layout.maxY(), "Scrollbar reaches both ends");
                    states++;
                }
                for (int index = 0; index < 8; index++) {
                    var icon = base.card(index);
                    var v = base.viewport();
                    var moved = SkillChoiceLayout.create(width, height,
                            icon.y() + icon.h() / 2 - v.y() - v.h() / 2);
                    var target = moved.card(index);
                    h.assertTrue(moved.canHit(target, target.x() + target.w() / 2, target.y() + target.h() / 2),
                            "Every candidate center can be reached at the smallest supported GUI size");
                    for (int lines : new int[]{1, 15, 100, 300}) {
                        var tip = SkillChoiceTooltipLayout.create(width, height, target, lines, 9999);
                        var b = tip.box();
                        h.assertTrue(b.x() >= 0 && b.y() >= 0 && b.right() <= width && b.bottom() <= height,
                                "Even the longest description stays inside the screen");
                        h.assertTrue(tip.scroll() + tip.visibleLines() == lines,
                                "Scrolling reaches every final line without truncation");
                        h.assertTrue(b.w() >= 120 && tip.visibleLines() > 0, "Tooltip retains readable width and complete lines");
                        tooltipStates++;
                    }
                }
                for (int index = 0; index < 5; index++) {
                    var b = base.attribute(index, 0);
                    var v = base.viewport();
                    int requested = Math.clamp(b.y() + b.h() / 2 - v.y() - v.h() / 2, 0, base.attributeMaximum());
                    var moved = base.attribute(index, requested);
                    h.assertTrue(base.canHit(moved, moved.x() + moved.w() / 2, moved.y() + moved.h() / 2),
                            "All five growth icons are reachable and fit horizontally");
                    var buy = base.supplyAction(index, 0);
                    h.assertTrue(buy.x() >= v.x() && buy.right() <= v.right(), "Explicit supply purchase fits the viewport");
                }
            }
        }
        var reference = SkillChoiceLayout.create(427, 240, 0);
        var second = reference.card(1);
        var shortTip = SkillChoiceTooltipLayout.create(427, 240, second, 2, 0, 64);
        h.assertTrue(shortTip.box().w() == 64 && shortTip.box().x() > second.right(),
                "Short descriptions use their measured width beside the second candidate");
        var longTip = SkillChoiceTooltipLayout.create(427, 240, second, 100, 0, 220);
        h.assertTrue(longTip.box().x() > second.right() && longTip.box().right() <= 427,
                "Standard GUI uses readable right-side space before covering candidates");
        var clicks = new SkillChoiceInteraction();
        h.assertTrue(!clicks.select("skill-a", false, true), "Single click selects without spending");
        h.assertTrue(clicks.select("skill-a", true, true), "The second click on the same icon may spend one point");
        h.assertTrue(!clicks.select("skill-a", true, true), "Additional double-click events cannot repeat that purchase");
        h.assertTrue(!clicks.select("skill-b", true, true), "Two different icons are not a purchase double-click");
        h.assertTrue(!clicks.select("skill-b", false, false) && !clicks.select("skill-b", true, false),
                "Unavailable choices remain inspectable without spending");
        h.assertTrue(!clicks.confirm("skill-b", false, false), "A disabled explicit action cannot spend");
        h.assertTrue(clicks.confirm("skill-b", false, true) && !clicks.confirm("skill-b", true, true),
                "Double-clicking the explicit action submits once");
        clicks.reset();
        h.assertTrue(clicks.selected().isEmpty() && !clicks.confirm("skill-b", false, true), "Page changes clear stale selections");
        for (String role : FirstGroupSkills.ROLES) {
            var list = FirstGroupSkills.candidates(role);
            h.assertTrue(list.size() == 8 && FirstGroupSkills.opposite(list.get(2).index()) < 0
                    && FirstGroupSkills.opposite(list.get(3).index()) < 0, "Regular skills remain independent for every current hero");
        }
        try (var input = SkillChoiceUiChecks.class.getResourceAsStream("/assets/myriad/textures/gui/skill_choice/meridian_hall.png")) {
            h.assertTrue(input != null, "Existing illustrated panel remains packaged");
            var image = ImageIO.read(input);
            h.assertTrue(image != null && image.getWidth() == 1774 && image.getHeight() == 887,
                    "The existing background artwork remains unchanged");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        System.out.println("[MYRIAD-SKILL-UI] PASS: " + states + " responsive scroll states, " + tooltipStates
                + " bounded tooltip states, four icon rows, all growth icons reachable, single/double-click spending contract");
    }
}
