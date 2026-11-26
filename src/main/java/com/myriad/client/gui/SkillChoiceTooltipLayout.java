package com.myriad.client.gui;

import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Content-sized popover that prefers the space beside its source, including scrolled text. */
public record SkillChoiceTooltipLayout(Box box, int visibleLines, int scroll, int maximum) {
    public static final int LINE_HEIGHT = 11;
    private static final int MARGIN = 6;
    private static final int GAP = 6;
    private static final int CHROME = 24;

    public static int width(int screenWidth, Box anchor) { return width(screenWidth, anchor, 220); }

    public static int width(int screenWidth, Box anchor, int preferredWidth) {
        int available = Math.max(1, screenWidth - 2 * MARGIN);
        int desired = Math.min(available, Math.clamp(preferredWidth, 48, 240));
        int right = screenWidth - anchor.right() - MARGIN - GAP;
        int left = anchor.x() - MARGIN - GAP;
        int readable = Math.min(desired, 126);
        int side = Math.max(left, right);
        return side >= readable ? Math.min(desired, side) : desired;
    }

    public static SkillChoiceTooltipLayout create(int width, int height, Box anchor, int lines, int requested) {
        return create(width, height, anchor, lines, requested, 220);
    }

    public static SkillChoiceTooltipLayout create(int width, int height, Box anchor, int lines, int requested, int preferred) {
        return create(width, height, anchor, lines, requested, preferred, height - MARGIN);
    }

    public static SkillChoiceTooltipLayout create(int width, int height, Box anchor, int lines, int requested, int preferred, int bottom) {
        int w = width(width, anchor, preferred);
        int limit = Math.clamp(bottom, 1, height);
        int top = Math.min(MARGIN, (limit - 1) / 2);
        int available = limit - top;
        int visible = Math.max(1, Math.min(lines, (available - CHROME) / LINE_HEIGHT));
        int h = Math.min(available, visible * LINE_HEIGHT + CHROME);
        int right = width - anchor.right() - MARGIN - GAP;
        int left = anchor.x() - MARGIN - GAP;
        int x;
        if (right >= w) x = anchor.right() + GAP;
        else if (left >= w) x = anchor.x() - w - GAP;
        else x = width - w - MARGIN;
        int horizontalMargin = Math.min(MARGIN, Math.max(0, (width - w) / 2));
        x = Math.clamp(x, horizontalMargin, Math.max(horizontalMargin, width - w - horizontalMargin));
        int y = Math.clamp(anchor.y(), top, limit - h);
        int maximum = Math.max(0, lines - visible);
        return new SkillChoiceTooltipLayout(new Box(x, y, w, h), visible, Math.clamp(requested, 0, maximum), maximum);
    }

    /** Safe areas in preference order. None includes the visible source icon or the footer. */
    public static java.util.List<Box> skillRegions(int screenWidth, int footerY, Box icon) {
        int x = MARGIN, y = MARGIN;
        int right = Math.max(x + 1, screenWidth - MARGIN), bottom = Math.max(y + 1, footerY - MARGIN);
        int w = right - x, h = bottom - y;
        return java.util.List.of(
                new Box(icon.right() + GAP, y, Math.max(0, right - icon.right() - GAP), h),
                new Box(x, y, Math.max(0, icon.x() - GAP - x), h),
                new Box(x, y, w, Math.max(0, icon.y() - GAP - y)),
                new Box(x, icon.bottom() + GAP, w, Math.max(0, bottom - icon.bottom() - GAP)));
    }

    public boolean keepsOpen(Box anchor, double x, double y) {
        if (box.contains(x, y)) return true;
        if (y < Math.max(anchor.y(), box.y()) || y >= Math.min(anchor.bottom(), box.bottom())) return false;
        if (box.x() >= anchor.right()) return x >= anchor.right() && x < box.x() && box.x() - anchor.right() <= GAP;
        return box.right() <= anchor.x() && x >= box.right() && x < anchor.x() && anchor.x() - box.right() <= GAP;
    }
}
