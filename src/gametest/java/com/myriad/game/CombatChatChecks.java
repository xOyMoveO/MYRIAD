package com.myriad.game;

import com.myriad.client.CombatChatLayout;
import com.myriad.client.CombatChatTime;
import com.myriad.client.CombatHudLayout;
import com.myriad.client.CombatHudLayout.Bounds;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.BiConsumer;

final class CombatChatChecks {
    private CombatChatChecks() {}

    static void verify(BiConsumer<Boolean, String> check) {
        for (int[] size : new int[][]{{960, 540}, {427, 240}, {320, 240}, {320, 180}}) {
            int width = size[0];
            int height = size[1];
            Bounds hotbar = CombatHudLayout.hotbar(width, height, 29, 24);
            var panel = CombatHudLayout.at(width, height, 0, 1, 1, 3, 2, true, hotbar);
            Bounds hud = panel.bounds();
            var passive = CombatChatLayout.create(width, height, hud, hotbar, null, false, false, false, 320, 20, 1F, 9);
            var focused = CombatChatLayout.create(width, height, hud, hotbar, null, true, false, false, 320, 20, 1F, 9);
            check.accept(passive != null && passive.lines() >= 1 && passive.lines() <= 5 && passive.inputTop() == -1,
                    "Passive chat shows up to five recent lines without an edit box");
            check.accept(focused != null && focused.lines() >= 1, "Small windows preserve at least one focused line");
            var box = focused.editBox();
            check.accept(box.x() == focused.x() + 8 && box.right() == focused.x() + focused.width() - 8
                            && box.y() == focused.inputTop() + 5 && box.height() == 12,
                    "The native text, cursor, IME and selection share the padded edit-box coordinates");
            check.accept(focused.inputTop() - focused.historyBottom() == 4, "Focused history sits four pixels above input");
            check.accept(panel.equals(CombatHudLayout.at(width, height, 0, 1, 1, 3, 2, true, hotbar)),
                    "Both chat states use the unchanged charged, tall-combat-panel geometry");
            if (width == 960 || width == 320 && height == 240) {
                check.accept(passive.x() == hud.x() && focused.x() == hud.x()
                                && passive.historyBottom() == hud.y() - 6
                                && focused.inputTop() + CombatChatLayout.INPUT_HEIGHT == hud.y() - 6,
                        "Both chat states align to the drawn HUD and retain the six-pixel gap");
            }
            if (width == 427) {
                check.accept(passive.x() == hud.x() && passive.historyBottom() == hud.y() - 6
                                && focused.lines() >= 1 && focused.x() >= hud.right() + 6,
                        "A tall combat HUD leaves room for passive history and sends the focused chat to the available side");
            }
            verifySafe(focused, width, height, hud, hotbar, check);
        }

        int width = 427, height = 240;
        Bounds hotbar = CombatHudLayout.hotbar(width, height, 29, 24);
        Bounds topHud = CombatHudLayout.at(width, height, .55F, .3F, 1F, 0, 0, false, hotbar).bounds();
        var fallback = CombatChatLayout.create(width, height, topHud, hotbar, null, true, true, true, 320, 20, .8F, 13);
        check.accept(fallback != null && fallback.x() < topHud.x() && fallback.lines() >= 1,
                "A top-anchored panel leaves the chat in the safety region on the left");
        check.accept(fallback.inputTop() - fallback.historyBottom() >= 4 + 8 && fallback.suggestionLineLimit() >= 1,
                "The queue notice and at least one command suggestion fit above the input");
        verifySafe(fallback, width, height, topHud, hotbar, check);
        check.accept(CombatChatLayout.create(width, height, null, hotbar, null, true, true, true, 320, 20, 1F, 9) == null,
                "Without a drawn personal HUD the whole chat uses vanilla layout");
        check.accept(CombatChatLayout.create(width, height, topHud, hotbar, null, true, false, false, 320, 20, 0F, 9) == null,
                "Zero-scale chat retains native handling");

        Instant signed = Instant.parse("2026-11-24T23:42:08Z");
        Instant received = Instant.parse("2026-11-24T23:42:20Z");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        var time = CombatChatTime.from(signed, received);
        check.accept(time.sent() && time.time().equals(signed) && time.clock(shanghai, false).equals("07:42:08")
                        && time.clock(losAngeles, true).equals("15:42"),
                "The signed instant survives conversion, seconds elision and switching local time zones");
        check.accept(time.fullTime(shanghai).equals("2026-11-25 07:42:08 +08:00"),
                "The detailed timestamp includes full local date, seconds and offset");
        var fallbackTime = CombatChatTime.from(null, received);
        check.accept(!fallbackTime.sent() && fallbackTime.time().equals(received)
                        && fallbackTime.clock(shanghai, false).equals("07:42:20"),
                "Missing trusted send timestamps retain their first receipt time and source");
    }

    private static void verifySafe(CombatChatLayout chat, int width, int height, Bounds hud, Bounds hotbar,
                                   BiConsumer<Boolean, String> check) {
        var area = new Bounds(chat.x(), chat.top(), chat.width(),
                chat.inputTop() + CombatChatLayout.INPUT_HEIGHT - chat.top());
        var reticle = new Bounds(width / 2 - 10, height / 2 - 10, 20, 20);
        check.accept(area.x() >= 0 && area.y() >= 0 && area.right() <= width && area.bottom() <= height
                        && !overlaps(area, hud) && !overlaps(area, hotbar) && !overlaps(area, reticle),
                "The message history and input stay inside the viewport and clear the panel, hotbar and reticle");
    }

    private static boolean overlaps(Bounds one, Bounds two) {
        return one.x() < two.right() && one.right() > two.x() && one.y() < two.bottom() && one.bottom() > two.y();
    }
}
