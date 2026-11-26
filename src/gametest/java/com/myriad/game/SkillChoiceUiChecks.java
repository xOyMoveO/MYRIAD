package com.myriad.game;

import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.gui.SkillChoiceInteraction;
import com.myriad.client.gui.SkillChoiceLayout;
import com.myriad.client.gui.SkillChoiceLayout.Box;
import com.myriad.client.gui.SkillChoiceTooltipLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.gametest.framework.GameTestHelper;

/** Production geometry and activation contracts. The real font and native input are checked in the client. */
final class SkillChoiceUiChecks {
    static void verify(GameTestHelper h) {
        h.assertTrue(SkillChoiceLayout.create(427, 240, 0).panel().w() == 367, "427×240 uses a 367px frame");
        h.assertTrue(SkillChoiceLayout.create(320, 180, 0).panel().w() == 296, "320×180 uses a 296px frame");
        h.assertTrue(SkillChoiceLayout.create(960, 540, 0).panel().w() == 640, "960×540 caps at a 640px frame");
        int states = 0;
        int tooltipStates = 0;
        for (int width : new int[]{240, 280, 320, 384, 427, 480, 576, 640, 960}) {
            for (int height : new int[]{135, 180, 240, 270, 360, 540}) {
                for (int measuredLabel : new int[]{32, 104}) {
                    var initial = SkillChoiceLayout.create(width, height, 0, measuredLabel);
                    var selected = SkillChoiceLayout.create(width, height, 9999, measuredLabel);
                    verifyFrame(h, initial, width, height);
                    verifyFrame(h, selected, width, height);
                    h.assertTrue(initial.panel().equals(selected.panel()) && initial.body().equals(selected.body()),
                            "Pages and selection share the same outer frame and footer");
                    h.assertTrue(initial.viewport().w() == selected.viewport().w()
                            && selected.body().right() - selected.viewport().right() == 8,
                            "Selection uses the full list width at every GUI scale");
                    h.assertTrue(SkillChoiceLayout.create(width, height, -9999, measuredLabel).scrollY() == 0
                            && selected.scrollY() == selected.maxY(), "Resize clamps persistent skill scroll");
                    verifyScroll(h, selected.verticalTrack(), selected.maxY());
                    var seenRows = new int[4];
                    for (int row = 0; row < 4; row++) {
                        int first = SkillChoiceLayout.firstIndex(row);
                        seenRows[row] = first;
                        var a = initial.card(first);
                        var b = initial.card(first + 1);
                        var category = initial.category(first / 2);
                        h.assertTrue(a.y() == b.y() && category.right() < a.x() && a.right() < b.x(),
                                "Group and both candidates have separate columns");
                        h.assertTrue(a.x() >= initial.viewport().x() && b.right() <= initial.viewport().right(),
                                "Both card targets fit the visible content width");
                        for (int index = first; index <= first + 1; index++) {
                            var target = initial.card(index);
                            int targetScroll = initial.reveal(target, 0, initial.maxY());
                            var reached = SkillChoiceLayout.create(width, height, targetScroll, measuredLabel);
                            var card = reached.card(index);
                            var view = reached.viewport();
                            h.assertTrue(reached.canHit(card, card.x() + card.w() / 2, card.y() + card.h() / 2)
                                    && !reached.canHit(card, card.x() + 1, view.y() - 1), "Focus can reveal every candidate and clipping rejects hits");
                            var icon = reached.skillIcon(index);
                            var visibleIcon = icon.intersect(view);
                            h.assertTrue(contains(card, icon) && reached.canHit(icon, visibleIcon.x(), visibleIcon.y())
                                            && !reached.canHit(icon, icon.right(), icon.y()) && !reached.canHit(icon, icon.x(), view.y() - 1),
                                    "Only visible artwork, including its branch badge, triggers a skill card");
                            var sizes = SkillChoiceTooltipLayout.skillRegions(width, reached.footerDividerY(), visibleIcon);
                            h.assertTrue(sizes.stream().anyMatch(area -> area.w() >= 96 && area.h() >= 60),
                                    "There is room to place a compact card beside a visible icon");
                            for (var area : sizes) {
                                if (area.h() == 0 || area.w() == 0) continue;
                                h.assertTrue(!area.overlaps(visibleIcon) && !area.overlaps(reached.primary())
                                                && area.right() <= width && area.bottom() < reached.footerDividerY(),
                                        "Skill cards leave the visible icon and confirm button clear");
                            }
                            for (int lines : new int[]{1, 15, 100, 300}) {
                                var tip = SkillChoiceTooltipLayout.create(width, height, card, lines, 9999, 220,
                                        reached.footerDividerY() - 3);
                                var box = tip.box();
                                h.assertTrue(box.x() >= 0 && box.y() >= 0 && box.right() <= width
                                                && box.bottom() < reached.footerDividerY(),
                                        "Long tooltips preserve screen bounds and leave the purchase footer uncovered");
                                h.assertTrue(tip.scroll() + tip.visibleLines() == lines && box.w() >= 120,
                                        "Readable popovers scroll to the complete final line");
                                tooltipStates++;
                            }
                        }
                    }
                    h.assertTrue(java.util.Arrays.equals(seenRows, new int[]{0, 4, 2, 6}),
                            "Display order stays passive, major, regular, ultimate");
                    verifyShop(h, initial);
                    states++;
                }
            }
        }
        verifyActivation(h);
        verifyDamageText(h);
        verifyHoverResources(h);
        verifyMaterial(h, "panel/myriad_default", 512, 256);
        verifyMaterial(h, "tooltip/default", 256, 128);
        for (String role : FirstGroupSkills.ROLES) verifyMaterial(h, "tooltip/" + role, 256, 128);
        try (var input = SkillChoiceUiChecks.class.getResourceAsStream("/assets/myriad/textures/gui/skill_choice/meridian_crest.png")) {
            h.assertTrue(input != null, "Original sword crest is packaged as a separate image");
            var image = ImageIO.read(input);
            h.assertTrue(image != null && image.getWidth() == 164 && image.getHeight() == 170
                    && image.getColorModel().hasAlpha(), "Crest keeps its aspect ratio and transparent surround");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        System.out.println("[MYRIAD-SKILL-UI] PASS: " + states + " responsive layouts, " + tooltipStates
                + " bounded tooltip states, selection/confirmation, focus and scrollbar contracts");
    }

    private static void verifyMaterial(GameTestHelper h, String name, int width, int height) {
        String path = "/assets/myriad/textures/gui/sprites/skill_choice/" + name + ".png";
        try (var input = SkillChoiceUiChecks.class.getResourceAsStream(path);
             var metadata = SkillChoiceUiChecks.class.getResourceAsStream(path + ".mcmeta")) {
            h.assertTrue(input != null && metadata != null, "Panel art and scaling metadata are packaged for " + name);
            var image = ImageIO.read(input);
            h.assertTrue(image != null && image.getWidth() == width && image.getHeight() == height
                            && (image.getRGB(0, 0) >>> 24) == 0 && (image.getRGB(width / 2, height / 2) >>> 24) == 255,
                    "Material has the expected size, transparent cut corner and opaque text area: " + name);
            var scaling = JsonParser.parseReader(new InputStreamReader(metadata, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
            h.assertTrue("nine_slice".equals(scaling.get("type").getAsString()), "Corners use nine-slice scaling: " + name);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read " + path, error);
        }
    }

    private static void verifyFrame(GameTestHelper h, SkillChoiceLayout layout, int width, int height) {
        var panel = layout.panel();
        h.assertTrue(panel.x() >= 12 && panel.right() + 12 <= width && panel.w() <= 640
                && panel.y() >= 2 && panel.bottom() + 2 <= height, "Outer frame and shadow fit inside the GUI");
        h.assertTrue(contains(panel, layout.body()) && contains(layout.body(), layout.viewport())
                && contains(panel, layout.close()) && contains(panel, layout.headerText()), "Header, body and close part share panel bounds");
        h.assertTrue(layout.primary().y() > layout.viewport().bottom() && contains(panel, layout.primary()),
                "Explicit action stays fixed below the scroll region");
        h.assertTrue(!layout.selectedLabel().overlaps(layout.primary()), "Selected name and action do not overlap");
        for (int index = 0; index < 3; index++) {
            var tab = layout.tab(index);
            h.assertTrue(contains(panel, tab) && !tab.overlaps(layout.body()), "Tabs stay clear of the body");
            if (index > 0) h.assertTrue(!tab.overlaps(layout.tab(index - 1)), "Each tab has its own click target");
        }
    }

    private static void verifyShop(GameTestHelper h, SkillChoiceLayout layout) {
        for (int index = 0; index < SkillChoiceLayout.ATTRIBUTE_ROWS; index++) {
            var icon = layout.attribute(index, 0);
            int scroll = layout.reveal(icon, 0, layout.attributeMaximum());
            var row = layout.attributeRow(index, scroll);
            var reached = layout.attribute(index, scroll);
            var details = layout.attributeDetails(index, scroll);
            h.assertTrue(layout.canHit(reached, reached.x() + reached.w() / 2, reached.y() + reached.h() / 2),
                    "Every attribute icon can be selected and purchased");
            h.assertTrue(contains(row, details) && details.x() > reached.right() && details.w() >= 110,
                    "Attribute count and price have room beside the item icon");
        }
        int count = TacticalSupplySystem.supplyIds().size();
        int max = layout.supplyMaximum(count);
        for (int index = 0; index < count; index++) {
            var buy = layout.supplyAction(index, 0);
            int scroll = layout.reveal(buy, 0, max);
            var reached = layout.supplyAction(index, scroll);
            h.assertTrue(contains(layout.supply(index, scroll), reached)
                    && layout.canHit(reached, reached.x() + reached.w() / 2, reached.y() + reached.h() / 2),
                    "Every supply buy button can be revealed and clicked");
        }
        verifyScroll(h, layout.verticalTrack(), layout.attributeMaximum());
        verifyScroll(h, layout.verticalTrack(), max);
        h.assertTrue(FirstGroupGrowth.price(150) > Integer.MAX_VALUE, "Fixture includes a high-digit non-overflowing purchase quote");
    }

    private static void verifyScroll(GameTestHelper h, Box track, int maximum) {
        var thumb = SkillChoiceLayout.thumb(track, maximum, maximum);
        h.assertTrue(contains(track, thumb), "Thumb stays in the scrollbar track");
        if (maximum == 0) return;
        double offset = thumb.h() / 2.0;
        h.assertTrue(SkillChoiceLayout.scrollTo(track, track.y() + offset, maximum, offset) == 0
                && SkillChoiceLayout.scrollTo(track, thumb.y() + offset, maximum, offset) == maximum,
                "Dragging a scrollbar with the original grab offset reaches both ends");
    }

    private static void verifyActivation(GameTestHelper h) {
        var clicks = new SkillChoiceInteraction();
        h.assertTrue(!SkillChoiceInteraction.sameActionTwice("skill-a", "confirm", true)
                && SkillChoiceInteraction.sameActionTwice("confirm", "confirm", true),
                "A quick selection followed by the explicit button is two distinct actions");
        h.assertTrue(!clicks.select("skill-a", false, true), "Selecting with mouse or Enter does not activate");
        h.assertTrue(clicks.select("skill-a", true, true) && !clicks.select("skill-a", true, true),
                "The original same-icon double-click shortcut activates once");
        h.assertTrue(!clicks.select("skill-b", true, true), "Two different cards are not a purchase double-click");
        h.assertTrue(!clicks.select("skill-b", false, false) && !clicks.select("skill-b", true, false),
                "Unavailable choices remain inspectable without spending");
        h.assertTrue(!clicks.confirm("skill-b", false, false), "A disabled explicit button cannot spend");
        h.assertTrue(clicks.confirm("skill-b", false, true) && !clicks.confirm("skill-b", true, true),
                "Double-clicking the explicit action submits once");
        clicks.reset();
        h.assertTrue(clicks.selected().isEmpty() && !clicks.confirm("skill-b", false, true), "Page changes clear stale selections");
        var ids = List.of("page:0", "skill-a", "skill-b", "confirm", "close");
        h.assertTrue(SkillChoiceInteraction.stepFocus(ids, "close", false) == 0
                && SkillChoiceInteraction.stepFocus(ids, "page:0", true) == 4
                && SkillChoiceInteraction.stepFocus(ids, "skill-b", false) == 3
                && SkillChoiceInteraction.stepFocus(ids, "disappeared", false) == 0, "Tab and Shift+Tab cycle through existing controls");
        for (String role : FirstGroupSkills.ROLES) {
            var list = FirstGroupSkills.candidates(role);
            h.assertTrue(list.size() == 8 && FirstGroupSkills.opposite(list.get(2).index()) < 0
                    && FirstGroupSkills.opposite(list.get(3).index()) < 0, "Regular skills remain independent for every current hero");
        }
    }

    private static JsonObject jsonResource(String name) {
        try (var in = SkillChoiceUiChecks.class.getResourceAsStream("/assets/myriad/" + name + ".json");
                var reader = new InputStreamReader(java.util.Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void verifyHoverResources(GameTestHelper h) {
        var stats = jsonResource("skill_choice/metrics");
        var english = jsonResource("lang/en_us");
        var chinese = jsonResource("lang/zh_cn");
        for (var role : FirstGroupSkills.ROLES) {
            for (var skill : FirstGroupSkills.candidates(role)) {
                String id = skill.getId();
                String prefix = "skill.myriad." + id + ".hover.";
                var fields = stats.getAsJsonArray(id);
                h.assertTrue(fields.size() > 0 && fields.size() <= 2, "Concise metrics for " + id);
                h.assertTrue(chinese.has(prefix + "summary") && english.has(prefix + "summary")
                                && chinese.has(prefix + "limit") == english.has(prefix + "limit"),
                        "Both languages cover each skill summary and condition: " + id);
                for (int i = 0; i < fields.size(); i++) {
                    var metric = fields.get(i).getAsJsonObject();
                    if (metric.has("damage")) {
                        int formula = metric.get("damage").getAsInt();
                        h.assertTrue(formula >= 0 && formula < skill.damageFormulas().size(), "Damage segment exists: " + id);
                    } else {
                        String key = prefix + "stat" + i;
                        h.assertTrue(chinese.has(key) && english.has(key), "Both languages have the metric " + key);
                    }
                }
            }
        }
    }

    private static void verifyDamageText(GameTestHelper h) {
        String legacyMechanics = "生命低于30%时，造成93＋22(r－1)＋55%S物理伤害，并获得40＋15(r－1)＋25%S护盾。";
        String preview = AbilityFormulaText.evaluate(AbilityFormulaText.withoutInlineDamageAmounts(legacyMechanics), 2, 20);
        h.assertTrue(preview.equals("生命低于30%时，造成物理伤害，并获得60护盾。"),
                "Shared formulas replace old inline damage amounts without hiding health gates or shield scaling");
        h.assertTrue(AbilityFormulaText.amount(100.5).equals("100.5") && AbilityFormulaText.amount(7.5).equals("7.5"),
                "Mitigation-free forecasts keep the verified half-point precision");
    }

    private static boolean contains(Box parent, Box child) {
        return child.x() >= parent.x() && child.y() >= parent.y()
                && child.right() <= parent.right() && child.bottom() <= parent.bottom();
    }
}
