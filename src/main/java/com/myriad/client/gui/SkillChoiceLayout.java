package com.myriad.client.gui;

/** Pure geometry shared by rendering, clipping, scrolling, input and regression checks. */
public record SkillChoiceLayout(SkillChoiceLayout.Box panel, SkillChoiceLayout.Box viewport,
        int columnWidth, int cardHeight, int scrollX, int scrollY, int maxX, int maxY) {
    public static final int GAP = 10, GROUP_HEADER = 26, ROW_GAP = 8;
    public record Box(int x,int y,int w,int h) {
        public int right(){return x+w;}
        public int bottom(){return y+h;}
        public boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
        public boolean overlaps(Box b){return x<b.right()&&right()>b.x&&y<b.bottom()&&bottom()>b.y;}
    }
    public static SkillChoiceLayout create(int screenWidth,int screenHeight,int xOffset,int yOffset){
        int w=Math.max(1,Math.min(920,screenWidth-24)),h=Math.max(1,Math.min(452,screenHeight-16));
        var panel=new Box((screenWidth-w)/2,(screenHeight-h)/2,w,h);
        var viewport=new Box(panel.x+16,panel.y+88,Math.max(1,w-40),Math.max(1,h-122));
        int column=Math.max(116,(viewport.w-3*GAP)/4);
        int card=Math.clamp((viewport.h-GROUP_HEADER-ROW_GAP)/2,86,126);
        int maxX=Math.max(0,4*column+3*GAP-viewport.w);
        int maxY=Math.max(0,GROUP_HEADER+2*card+ROW_GAP-viewport.h);
        return new SkillChoiceLayout(panel,viewport,column,card,Math.clamp(xOffset,0,maxX),Math.clamp(yOffset,0,maxY),maxX,maxY);
    }
    public Box column(int group){return new Box(viewport.x+group*(columnWidth+GAP)-scrollX,
            viewport.y-scrollY,columnWidth,GROUP_HEADER);}
    public Box card(int index){var col=column(index/2);return new Box(col.x,col.y+GROUP_HEADER+(index%2)*(cardHeight+ROW_GAP),columnWidth,cardHeight);}
    public Box action(int index){var c=card(index);return new Box(c.x+8,c.bottom()-24,c.w-16,18);}
    public Box tab(int index){int w=(panel.w-40-12)/3;return new Box(panel.x+20+index*(w+6),panel.y+59,w,21);}
    public Box horizontalTrack(){return new Box(viewport.x,panel.bottom()-28,viewport.w,5);}
    public Box verticalTrack(){return new Box(viewport.right()+3,viewport.y,5,viewport.h);}
    public Box horizontalThumb(){var track=horizontalTrack();int w=Math.min(track.w,Math.max(24,track.w*viewport.w/(viewport.w+maxX)));
        return new Box(track.x+(maxX==0?0:(track.w-w)*scrollX/maxX),track.y,w,track.h);}
    public Box verticalThumb(){var track=verticalTrack();int h=Math.min(track.h,Math.max(18,track.h*viewport.h/(viewport.h+maxY)));
        return new Box(track.x,track.y+(maxY==0?0:(track.h-h)*scrollY/maxY),track.w,h);}
    public int scrollToHorizontal(double mouseX){var track=horizontalTrack();var thumb=horizontalThumb();return Math.clamp((int)Math.round((mouseX-track.x-thumb.w/2.0)*maxX/Math.max(1,track.w-thumb.w)),0,maxX);}
    public int scrollToVertical(double mouseY){var track=verticalTrack();var thumb=verticalThumb();return Math.clamp((int)Math.round((mouseY-track.y-thumb.h/2.0)*maxY/Math.max(1,track.h-thumb.h)),0,maxY);}
    public boolean canHit(Box box,double x,double y){return viewport.contains(x,y)&&box.contains(x,y);}
}
