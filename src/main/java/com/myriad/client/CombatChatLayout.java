package com.myriad.client;

import com.myriad.client.CombatHudLayout.Bounds;
import java.util.ArrayList;
import java.util.List;

/** GUI coordinates for the native chat history, edit box, and suggestion popup. */
public record CombatChatLayout(int x, int width, int historyBottom, int inputTop,
                               int top, int lines, int messageWidth) {
    public interface SuggestionAnchor {
        boolean myriad$setAnchor(CombatChatLayout layout);
    }
    public static final int INPUT_HEIGHT = 22;
    public static final int INPUT_PADDING = 8;
    private static final int HUD_GAP = 6;
    private static final int HISTORY_GAP = 4;
    private static final int MIN_WIDTH = 72;

    public Bounds editBox() {
        return new Bounds(x + INPUT_PADDING, inputTop + (INPUT_HEIGHT - 12) / 2,
                width - 2 * INPUT_PADDING, 12);
    }

    public int suggestionLineLimit() {
        return Math.clamp((inputTop - HISTORY_GAP - top - 1) / 12, 1, 10);
    }

    public static CombatChatLayout create(int screenWidth, int screenHeight, Bounds hud, Bounds hotbar,
                                          Bounds minimap, boolean focused, boolean queued, boolean restricted,
                                          int vanillaWidth, int vanillaLines, float scale, int lineHeight) {
        if (hud == null || scale <= 0) return null;
        var viewport = new Bounds(HUD_GAP, HUD_GAP, screenWidth - 2 * HUD_GAP, screenHeight - 2 * HUD_GAP);
        var reticle = new Bounds(screenWidth / 2 - 10, screenHeight / 2 - 10, 20, 20);
        List<Bounds> free = subtract(List.of(viewport), outset(hud, HUD_GAP));
        free = subtract(free, hotbar);
        free = subtract(free, reticle);
        var space = new Space(focused, queued, restricted, vanillaWidth, vanillaLines, scale, lineHeight);
        if (minimap != null) {
            CombatChatLayout safe = select(subtract(free, minimap), hud, space);
            if (safe != null) return safe;
        }
        return select(free, hud, space);
    }

    private static CombatChatLayout select(List<Bounds> free, Bounds hud, Space space) {
        CombatChatLayout best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Bounds area : free) {
            int bottom = Math.min(area.bottom(), hud.y() - HUD_GAP);
            CombatChatLayout candidate = space.fit(area, hud.x(), bottom);
            if (candidate == null) continue;
            int score = -4 * (hud.y() - HUD_GAP - bottom) - 4 * Math.abs(hud.x() - candidate.x())
                    + candidate.lines() + candidate.width() / 12;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) return best;

        // The player's HUD anchor takes priority. Try the remaining left-side room first.
        for (Bounds area : free) {
            CombatChatLayout candidate = space.fit(area, HUD_GAP, area.bottom());
            if (candidate == null) continue;
            int score = -2 * candidate.x() + candidate.lines() * 2 + candidate.width() / 12;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private record Space(boolean focused, boolean queued, boolean restricted, int vanillaWidth,
                         int vanillaLines, float scale, int lineHeight) {
        CombatChatLayout fit(Bounds area, int desiredX, int bottom) {
            if (area.width() < MIN_WIDTH) return null;
            int x = Math.clamp(desiredX, area.x(), area.right() - MIN_WIDTH);
            int nativeInset = (int)Math.ceil(13 * scale);
            int width = Math.min(area.right() - x, Math.max(MIN_WIDTH, vanillaWidth + nativeInset));
            int inputTop = focused ? bottom - INPUT_HEIGHT : -1;
            int base = focused ? inputTop : bottom;
            int historyBottom = base - (focused ? HISTORY_GAP : 0) - (queued ? (int)Math.ceil(9 * scale) : 0);
            // Vanilla floors the scaled baseline. Reserve one scale unit and the optional restriction row.
            if (focused && inputTop - HISTORY_GAP - area.y() < 13) return null;
            int capacity = (int)Math.floor((historyBottom - area.y() - scale) / (lineHeight * scale));
            int lines = Math.min(Math.min(capacity - (restricted ? 1 : 0), Math.max(1, vanillaLines)), focused ? 100 : 5);
            if (lines < 1) return null;
            return new CombatChatLayout(x, width, historyBottom, inputTop, area.y(), lines, width - nativeInset);
        }
    }

    private static Bounds outset(Bounds bounds, int gap) {
        return new Bounds(bounds.x() - gap, bounds.y() - gap, bounds.width() + 2 * gap, bounds.height() + 2 * gap);
    }

    private static List<Bounds> subtract(List<Bounds> regions, Bounds obstacle) {
        var result = new ArrayList<Bounds>();
        for (Bounds area : regions) {
            if (area.x() >= obstacle.right() || area.right() <= obstacle.x()
                    || area.y() >= obstacle.bottom() || area.bottom() <= obstacle.y()) {
                result.add(area);
                continue;
            }
            add(result, area.x(), area.y(), obstacle.x(), area.bottom());
            add(result, obstacle.right(), area.y(), area.right(), area.bottom());
            add(result, area.x(), area.y(), area.right(), obstacle.y());
            add(result, area.x(), obstacle.bottom(), area.right(), area.bottom());
        }
        return result;
    }

    private static void add(List<Bounds> areas, int left, int top, int right, int bottom) {
        if (right - left >= MIN_WIDTH && bottom > top) {
            areas.add(new Bounds(left, top, right - left, bottom - top));
        }
    }
}
