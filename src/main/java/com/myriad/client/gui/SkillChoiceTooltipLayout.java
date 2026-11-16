package com.myriad.client.gui;

import com.myriad.client.gui.SkillChoiceLayout.Box;

/** A content-sized, screen-bounded popover. Skill anchors use the row's second icon. */
public record SkillChoiceTooltipLayout(Box box,int visibleLines,int scroll,int maximum) {
    public static int width(int screenWidth,Box anchor) {
        return width(screenWidth,anchor,220);
    }
    public static int width(int screenWidth,Box anchor,int preferredWidth) {
        int desired=Math.clamp(preferredWidth,48,Math.max(48,Math.min(220,screenWidth-12)));
        int right=screenWidth-anchor.right()-12,left=anchor.x()-12;
        int readable=desired>160?120:Math.min(96,desired);
        if(right>=desired)return desired;
        if(right>=readable)return Math.min(desired,right);
        if(left>=desired)return desired;
        if(left>=readable)return Math.min(desired,left);
        return Math.max(1,Math.min(desired,screenWidth-12));
    }
    public static SkillChoiceTooltipLayout create(int width,int height,Box anchor,int lines,int requested) {
        return create(width,height,anchor,lines,requested,220);
    }
    public static SkillChoiceTooltipLayout create(int screenWidth,int screenHeight,Box anchor,
            int lines,int requested,int preferredWidth) {
        int w=width(screenWidth,anchor,preferredWidth);
        int visible=Math.max(1,Math.min(lines,(screenHeight-38)/10));
        int h=Math.min(Math.max(1,screenHeight-12),visible*10+24);
        int rightSpace=screenWidth-anchor.right()-12,leftSpace=anchor.x()-12;
        int x=rightSpace>=w?anchor.right()+6:leftSpace>=w?anchor.x()-w-6:screenWidth-w-6;
        x=Math.clamp(x,Math.min(6,Math.max(0,screenWidth-w)),Math.max(0,screenWidth-w-6));
        int y=Math.clamp(anchor.y(),Math.min(6,Math.max(0,screenHeight-h)),Math.max(0,screenHeight-h-6));
        int maximum=Math.max(0,lines-visible);
        return new SkillChoiceTooltipLayout(new Box(x,y,w,h),visible,Math.clamp(requested,0,maximum),maximum);
    }
}
