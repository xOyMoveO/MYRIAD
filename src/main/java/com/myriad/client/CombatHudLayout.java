package com.myriad.client;

/** GUI-pixel geometry shared by the combat HUD and the editor. */
public final class CombatHudLayout {
    public static final int GAP = 6;
    public static final int SLOT = 26;
    public static final int SLOT_STEP = 30;
    public static final int COMPACT_WIDTH = 162;
    public static final int FULL_WIDTH = 252;
    private static final int HOTBAR_HALF = 91;
    private static final int HOTBAR_HEIGHT = 23;
    private static final int CHAT_BOTTOM_INSET = 40;
    private static final int SKILL_Y = 44;
    private static final int AFTER_KEYS = 82;

    private CombatHudLayout() {}

    public record Bounds(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    public record Layout(Bounds bounds, float scale, int width, int height, boolean compact,
                         boolean attackRow, int roleTop, int controlTop, int attackTop, int xpTop) {
        public int contentX() { return compact ? GAP : 46; }
        public int skillY() { return SKILL_Y; }
    }

    public static Layout at(int width, int height, float xAnchor, float yAnchor, float scale,
                            int roleRows, int controlRows, boolean charges, Bounds chat) {
        return at(width, height, xAnchor, yAnchor, scale, roleRows, controlRows, charges,
                hotbar(width, height, 0, 0), chat);
    }

    public static Bounds hotbar(int width, int height, int leftExtra, int rightExtra) {
        int left = width / 2 - HOTBAR_HALF - Math.max(1, leftExtra);
        int right = width / 2 + HOTBAR_HALF + Math.max(1, rightExtra);
        return new Bounds(left, height - HOTBAR_HEIGHT, right - left, HOTBAR_HEIGHT);
    }

    public static Bounds focusedChat(int width, int height, int lines, int lineHeight, double scale) {
        // Vanilla measures the chat baseline from the viewport bottom before applying chat scale.
        int chatHeight = CHAT_BOTTOM_INSET + (int)Math.ceil((lines + 1) * lineHeight * scale);
        return new Bounds(0, height - chatHeight, width, chatHeight);
    }

    /** Page-size limit shared by chat rendering, links and the scroll position. */
    public static int chatLineLimit(int width, int height, float xAnchor, float yAnchor, float scale,
                                    int roleRows, int controlRows, boolean charges, Bounds hotbar,
                                    int chatWidth, int lineHeight, double chatScale, int vanillaLines, int historyLines) {
        int normalLines = Math.min(vanillaLines, historyLines);
        if (normalLines <= 1) return vanillaLines;
        float minimum = Math.min(scale, .8F);
        var normal = at(width, height, xAnchor, yAnchor, scale, roleRows, controlRows, charges,
                hotbar, focusedChat(chatWidth, height, normalLines, lineHeight, chatScale));
        if (normal != null && normal.scale() >= minimum) return vanillaLines;

        for (int lines = normalLines - 1; lines > 1; lines--) {
            var candidate = at(width, height, xAnchor, yAnchor, scale, roleRows, controlRows, charges,
                    hotbar, focusedChat(chatWidth, height, lines, lineHeight, chatScale));
            if (candidate != null && candidate.scale() >= minimum) return lines;
        }
        return 1;
    }

    public static Layout at(int width, int height, float xAnchor, float yAnchor, float scale,
                            int roleRows, int controlRows, boolean charges, Bounds hotbar, Bounds chat) {
        int wantedX = Math.max(GAP, Math.round(width * xAnchor));
        int wantedBottom = Math.min(height - GAP, Math.round(height * yAnchor));
        boolean compact = chat != null || width / 2 - HOTBAR_HALF - 1 - 2 * GAP < FULL_WIDTH;
        int rawWidth = compact ? COMPACT_WIDTH : FULL_WIDTH;
        boolean attackRow = compact && chat == null;
        int roleTop = AFTER_KEYS + (charges ? 15 : 0);
        int controlTop = roleTop + 11 * roleRows;
        int attackTop = controlTop + 11 * controlRows;
        int xpTop = attackTop + (attackRow ? 15 : 0);
        int rawHeight = xpTop + 11;

        float fittedScale = fit(scale, width - 2 * GAP, rawWidth);
        int x = Math.clamp(wantedX, GAP, Math.max(GAP, width - GAP - ceiling(rawWidth * fittedScale)));
        int right = x + ceiling(rawWidth * fittedScale);
        int maxBottom = height - GAP;
        if (x < hotbar.right() + GAP && right > hotbar.x() - GAP) maxBottom = hotbar.y() - GAP;
        if (chat != null) {
            maxBottom = Math.min(maxBottom, height - 24); // The chat input spans the bottom of the viewport.
            if (x < chat.right() + GAP && right > chat.x() - GAP && maxBottom > chat.y() - GAP) {
                int availableAbove = chat.y() - 2 * GAP;
                int rightOfChat = chat.right() + GAP;
                if (availableAbove < rawHeight * Math.min(fittedScale, .78F)
                        && width - GAP - rightOfChat >= ceiling(rawWidth * fittedScale)) {
                    x = rightOfChat;
                    maxBottom = Math.min(height - 24, x < hotbar.right() + GAP ? hotbar.y() - GAP : height - GAP);
                } else {
                    maxBottom = Math.min(maxBottom, chat.y() - GAP);
                }
            }
        }

        // The default compact panel can cross the reticle only on the narrowest GUI viewport.
        int reticleLeft = width / 2 - 10;
        if (xAnchor == 0 && x == GAP && right > reticleLeft && maxBottom > height / 2 - 10
                && maxBottom - ceiling(rawHeight * fittedScale) < height / 2 + 10) {
            fittedScale = fit(fittedScale, reticleLeft - x, rawWidth);
        }

        fittedScale = fit(fittedScale, maxBottom - GAP, rawHeight);
        if (fittedScale <= 0) return null;
        int minBottom = Math.min(maxBottom, ceiling(rawHeight * fittedScale) + GAP);
        int bottom = Math.clamp(wantedBottom, minBottom, maxBottom);
        int drawnWidth = ceiling(rawWidth * fittedScale);
        int drawnHeight = ceiling(rawHeight * fittedScale);
        return new Layout(new Bounds(x, bottom - drawnHeight, drawnWidth, drawnHeight),
                fittedScale, rawWidth, rawHeight, compact, attackRow, roleTop, controlTop, attackTop, xpTop);
    }

    private static float fit(float scale, int available, int size) {
        return ceiling(size * scale) <= available ? scale : Math.nextDown(available / (float)size);
    }

    private static int ceiling(float value) {
        return (int)Math.ceil(value);
    }
}
