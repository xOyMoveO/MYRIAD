package com.myriad.client.gui;

/** Shared geometry for icon rendering, clipping, scrolling and regression checks. */
public record SkillChoiceLayout(Box panel, Box viewport, int labelWidth, int tileWidth,
        int rowHeight, int scrollY, int maxY) {
    private static final int[] DISPLAY_GROUPS = {0, 2, 1, 3};

    public record Box(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public boolean overlaps(Box other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
    }

    public static SkillChoiceLayout create(int width, int height, int requestedY) {
        return create(width, height, requestedY, 32);
    }
    public static SkillChoiceLayout create(int width, int height, int requestedY, int measuredLabelWidth) {
        int h = Math.max(1, Math.min(420, height - 8));
        int w = Math.max(1, Math.min(480, Math.min(width - 8, (int) Math.round(h * 1.15))));
        var panel = new Box((width - w) / 2, (height - h) / 2, w, h);
        int bodyTop = titleOffset(h) + 28;
        int divider = navigationOffset(w);
        var view = new Box(panel.x + divider + 12, panel.y + bodyTop,
                Math.max(1, w - divider - 34), Math.max(1, h - 37 - bodyTop));
        int label = Math.clamp(measuredLabelWidth, 20, 70);
        int row = Math.clamp(view.h / 4, 17, 64);
        int tile = Math.clamp(row + 2, 20, 46);
        label = Math.min(label,Math.max(20,view.w-2*tile-18));
        int maximum = Math.max(0, 4 * row - view.h);
        return new SkillChoiceLayout(panel, view, label, tile, row, Math.clamp(requestedY, 0, maximum), maximum);
    }

    public static int displayRow(int skillIndex) {
        return switch (skillIndex / 2) { case 0 -> 0; case 2 -> 1; case 1 -> 2; default -> 3; };
    }
    public static int firstIndex(int row) { return DISPLAY_GROUPS[row] * 2; }
    private int groupWidth() { return labelWidth + 2 * tileWidth + 18; }
    private int groupStart() { return viewport.x + Math.max(0, (viewport.w - groupWidth()) / 4); }

    public Box category(int group) {
        int row = displayRow(group * 2);
        return new Box(groupStart(), viewport.y + row * rowHeight - scrollY, labelWidth, rowHeight - 1);
    }
    public Box card(int index) {
        return new Box(groupStart() + labelWidth + 8 + (index % 2) * (tileWidth + 10),
                viewport.y + displayRow(index) * rowHeight - scrollY, tileWidth, rowHeight - 1);
    }
    private static int titleOffset(int height) { return (int) Math.ceil(height * .18) + 4; }
    private static int navigationOffset(int width) { return Math.clamp(width / 5, 44, 64); }
    public int titleY() { return panel.y + titleOffset(panel.h); }
    public int statusY() { return titleY() + 11; }
    public int headerDividerY() { return titleY() + 23; }
    public int navigationDividerX() { return panel.x + navigationOffset(panel.w); }
    public int footerDividerY() { return panel.bottom() - 37; }
    public float rankScale() { return rowHeight < 28 ? .8f : 1; }
    public float footerScale() { return panel.h < 200 ? .8f : 1; }
    public Box primary() {
        int width = Math.min(80, Math.max(60, viewport.w / 2));
        return new Box(viewport.right() - width, footerDividerY() + 4, width, panel.h < 200 ? 16 : 18);
    }
    public Box selectedLabel() {
        return new Box(viewport.x, footerDividerY() + 2, Math.max(1, viewport.w - primary().w - 8), 20);
    }
    public Box tab(int index) {
        int size = Math.clamp((viewport.h - 12) / 3 - 4, 16, 24);
        int left = Math.clamp(panel.w / 12, 16, 25);
        return new Box(panel.x + left, viewport.y + 1 + index * (size + 8), size, size);
    }
    public static final int ATTRIBUTE_ROWS=5;
    public int attributeRowHeight(){return Math.clamp((viewport.h-28)/ATTRIBUTE_ROWS,36,52);}
    public Box attributeRow(int index,int requestedScroll){
        return new Box(viewport.x,viewport.y+28+index*attributeRowHeight()-requestedScroll,viewport.w,attributeRowHeight());
    }
    /** Only the left icon is a purchase target. */
    public Box attribute(int index, int requestedScroll) {
        var row=attributeRow(index,requestedScroll);
        int size=Math.clamp(row.h-12,20,32);
        return new Box(row.x+6,row.y+2,size,size);
    }
    public Box attributeDetails(int row,int requestedScroll){
        var icon=attribute(row,requestedScroll);
        int left=icon.right()+10;
        return new Box(left,icon.y(),Math.max(1,viewport.right()-4-left),attributeRowHeight()-4);
    }
    public int attributeMaximum() {
        return Math.max(0,28+ATTRIBUTE_ROWS*attributeRowHeight()-viewport.h);
    }
    public Box supply(int index, int requestedScroll) {
        return new Box(viewport.x, viewport.y + index * 32 - requestedScroll, viewport.w, 30);
    }
    public Box supplyAction(int index, int requestedScroll) {
        var row = supply(index, requestedScroll);
        return new Box(row.right() - 74, row.y + 6, 70, 18);
    }
    public Box verticalTrack() { return new Box(viewport.right() + 4, viewport.y, 5, viewport.h); }
    public Box verticalThumb() { return verticalThumb(maxY, scrollY); }
    public Box verticalThumb(int maximum, int scroll) {
        var track = verticalTrack();
        int h = Math.min(track.h, Math.max(16, track.h * track.h / Math.max(1, track.h + maximum)));
        return new Box(track.x, track.y + (maximum == 0 ? 0 : (track.h - h) * Math.clamp(scroll, 0, maximum) / maximum), track.w, h);
    }
    public int scrollToVertical(double y) { return scrollToVertical(y, maxY); }
    public int scrollToVertical(double y, int maximum) {
        var track = verticalTrack();
        var thumb = verticalThumb(maximum, 0);
        return Math.clamp((int) Math.round((y - track.y - thumb.h / 2.0) * maximum / Math.max(1, track.h - thumb.h)), 0, maximum);
    }
    public boolean canHit(Box box, double x, double y) { return viewport.contains(x, y) && box.contains(x, y); }
}
