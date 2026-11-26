package com.myriad.client.gui;

/** GUI-pixel geometry shared by drawing, clipping, hit testing and focus scrolling. */
public record SkillChoiceLayout(Box panel, Box body, Box viewport,
        boolean topTabs, int navigationWidth, int labelWidth, int tileWidth, int cardGap,
        int rowHeight, int scrollY, int maxY) {
    private static final int[] DISPLAY_GROUPS = {0, 2, 1, 3};
    private static final int SUPPLY_ROW_HEIGHT = 34;
    public static final int ATTRIBUTE_ROWS = 5;

    public record Box(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public boolean overlaps(Box other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
        public Box intersect(Box other) {
            int left = Math.max(x, other.x), top = Math.max(y, other.y);
            return new Box(left, top, Math.max(0, Math.min(right(), other.right()) - left),
                    Math.max(0, Math.min(bottom(), other.bottom()) - top));
        }
        public Box inset(int n) {
            int ix = Math.min(n, (w - 1) / 2);
            int iy = Math.min(n, (h - 1) / 2);
            return new Box(x + ix, y + iy, w - 2 * ix, h - 2 * iy);
        }
    }

    public static SkillChoiceLayout create(int width, int height, int requestedY) {
        return create(width, height, requestedY, 52);
    }

    public static SkillChoiceLayout create(int width, int height, int requestedY, int measuredLabelWidth) {
        int horizontalMargin = Math.min(12, width / 12);
        int verticalMargin = Math.min(6, height / 16);
        int w = Math.max(1, Math.min(640, Math.min(width - 2 * horizontalMargin, Math.max(360, Math.round(width * .86f)))));
        int h = Math.max(1, Math.min(440, height - 2 * verticalMargin));
        var panel = new Box((width - w) / 2, (height - h) / 2, w, h);
        boolean topTabs = w < 280;
        int nav = topTabs ? 0 : Math.clamp(w / 8, 52, 64);
        int inset = Math.min(14, Math.max(2, w / 16));
        int header = headerOffset(h);
        int top = Math.min(h - 1, header + (topTabs ? 49 : 31));
        int footer = footerOffset(h);
        var body = new Box(panel.x + inset + nav, panel.y + top,
                Math.max(1, w - 2 * inset - nav), Math.max(1, h - footer - top));
        var view = new Box(body.x, body.y, Math.max(1, body.w - 8), body.h);
        int gap = Math.clamp(view.w / 20, 4, 12);
        int label = Math.min(Math.clamp(measuredLabelWidth + 8, 32, 94), Math.max(12, view.w / 3));
        int tile = Math.max(1, (view.w - label - 2 * gap) / 2);
        int row = Math.clamp(view.h / 4, 34, 64);
        int maximum = Math.max(0, 4 * row - view.h);
        return new SkillChoiceLayout(panel, body, view, topTabs, nav, label, tile, gap,
                row, Math.clamp(requestedY, 0, maximum), maximum);
    }

    public static int displayRow(int skillIndex) {
        return switch (skillIndex / 2) { case 0 -> 0; case 2 -> 1; case 1 -> 2; default -> 3; };
    }

    public static int firstIndex(int row) { return DISPLAY_GROUPS[row] * 2; }

    public Box category(int group) {
        int row = displayRow(group * 2);
        return new Box(viewport.x, rowY(row), labelWidth, rowHeight - 4);
    }

    public Box card(int index) {
        return new Box(viewport.x + labelWidth + cardGap + (index % 2) * (tileWidth + cardGap),
                rowY(displayRow(index)), tileWidth, rowHeight - 4);
    }

    private int rowY(int row) { return viewport.y + row * rowHeight - scrollY; }

    private static int headerOffset(int height) { return Math.min(26, Math.max(0, height / 6)); }
    private static int footerOffset(int height) { return Math.min(31, Math.max(1, height / 6)); }
    public int titleY() { return panel.y + headerOffset(panel.h); }
    public int statusY() { return titleY() + 13; }
    public int headerDividerY() { return titleY() + 23; }
    public int navigationDividerX() { return body.x - 7; }
    public int footerDividerY() { return panel.bottom() - footerOffset(panel.h); }
    public float rankScale() { return 1; }
    public float footerScale() { return 1; }

    public Box headerText() {
        int left = panel.x + Math.min(22, panel.w / 8);
        return new Box(left, titleY(), Math.max(1, close().x - left - 6), 23);
    }

    public Box close() {
        int size = Math.max(1, Math.min(16, panel.w / 8));
        return new Box(panel.right() - Math.min(15, panel.w / 10) - size, titleY(), size, size);
    }

    public Box primary() {
        int width = Math.min(164, Math.max(1, body.w * 7 / 10));
        return new Box(body.right() - width, footerDividerY() + 4, width, Math.min(20, Math.max(1, footerOffset(panel.h) - 6)));
    }

    public Box skillConfirm() {
        var target = primary();
        int width = Math.min(target.w, Math.max(50, body.w * 2 / 5));
        return new Box(target.right() - width, target.y, width, target.h);
    }

    public Box selectedLabel() { return selectedLabel(primary()); }

    public Box selectedLabel(Box target) {
        return new Box(body.x, footerDividerY() + 4, Math.max(1, target.x - body.x - 8), target.h);
    }

    public Box tab(int index) {
        int pad = panel.w > 280 ? 12 : 8;
        if (topTabs) {
            int w = Math.max(1, (panel.w - 2 * pad - 8) / 3);
            return new Box(panel.x + pad + index * (w + 4), headerDividerY() + 5, w, 18);
        }
        int size = Math.clamp((body.h - 10) / 3, 16, 44);
        return new Box(panel.x + pad, body.y + index * (size + 5), navigationWidth - 2, size);
    }

    public int attributeRowHeight() { return Math.clamp((viewport.h - 22) / ATTRIBUTE_ROWS, 43, 63); }

    public Box attributeRow(int index, int requestedScroll) {
        return new Box(viewport.x, viewport.y + 22 + index * attributeRowHeight() - requestedScroll,
                viewport.w, attributeRowHeight() - 4);
    }

    public Box attribute(int index, int requestedScroll) {
        var row = attributeRow(index, requestedScroll);
        int size = Math.max(1, Math.min(30, row.h - 10));
        return new Box(row.x + 6, row.y + (row.h - size) / 2, size, size);
    }

    public Box attributeDetails(int row, int requestedScroll) {
        var icon = attribute(row, requestedScroll);
        var plate = attributeRow(row, requestedScroll);
        int left = icon.right() + 8;
        return new Box(left, plate.y + 3, Math.max(1, plate.right() - 6 - left), plate.h - 6);
    }

    public int attributeMaximum() {
        return Math.max(0, 22 + ATTRIBUTE_ROWS * attributeRowHeight() - viewport.h);
    }

    public int supplyMaximum(int count) { return Math.max(0, count * SUPPLY_ROW_HEIGHT - viewport.h); }

    public Box supply(int index, int requestedScroll) {
        return new Box(viewport.x, viewport.y + index * SUPPLY_ROW_HEIGHT - requestedScroll, viewport.w, SUPPLY_ROW_HEIGHT - 4);
    }

    public Box supplyAction(int index, int requestedScroll) {
        var row = supply(index, requestedScroll);
        int w = Math.min(102, Math.max(1, row.w * 2 / 5));
        return new Box(row.right() - w - 4, row.y + 6, w, 18);
    }

    public Box skillIcon(int index) {
        var card = card(index);
        int size = Math.max(1, Math.min(32, Math.min(card.h - 8, card.w / 3)));
        return new Box(card.x + 5, card.y + (card.h - size) / 2, size, size);
    }

    public Box verticalTrack() { return new Box(viewport.right() + 3, viewport.y, 5, viewport.h); }

    public Box verticalThumb() { return verticalThumb(maxY, scrollY); }

    public Box verticalThumb(int maximum, int scroll) { return thumb(verticalTrack(), maximum, scroll); }

    public static Box thumb(Box track, int maximum, int scroll) {
        int h = Math.min(track.h, Math.max(16, track.h * track.h / Math.max(1, track.h + maximum)));
        int y = maximum == 0 ? track.y : track.y + (track.h - h) * Math.clamp(scroll, 0, maximum) / maximum;
        return new Box(track.x, y, track.w, h);
    }

    public int scrollToVertical(double y) { return scrollToVertical(y, maxY); }

    public int scrollToVertical(double y, int maximum) {
        var track = verticalTrack();
        return scrollTo(track, y, maximum, thumb(track, maximum, 0).h / 2.0);
    }

    public static int scrollTo(Box track, double y, int maximum, double grabOffset) {
        int travel = track.h - thumb(track, maximum, 0).h;
        return Math.clamp((int) Math.round((y - track.y - grabOffset) * maximum / Math.max(1, travel)), 0, maximum);
    }

    public int reveal(Box box, int scroll, int maximum) {
        if (box.y < viewport.y) scroll -= viewport.y - box.y;
        else if (box.bottom() > viewport.bottom()) scroll += box.bottom() - viewport.bottom();
        return Math.clamp(scroll, 0, maximum);
    }

    public boolean canHit(Box box, double x, double y) { return viewport.contains(x, y) && box.contains(x, y); }
}
