package com.myriad.client.gui;

import java.util.Map;
import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Pure card-grid geometry; no rendering or client singleton needed for regression tests. */
public record CharacterChoiceLayout(Box panel,Box grid,Box details,int columns,int rows,int cellWidth,int cellHeight,
        int count,int scrollRow,int maxScroll){
    public static CharacterChoiceLayout create(int width,int height,int count,int requested){
        int w=Math.min(820,width-16),h=Math.min(460,height-12),x=(width-w)/2,y=(height-h)/2;
        int top=y+34,body=h-78,left=(w-32)*58/100;
        var grid=new Box(x+12,top,left-8,body);var details=new Box(x+left+20,top-4,w-left-32,body+8);
        int cols=Math.clamp((grid.w()+6)/62,1,5),cw=(grid.w()-(cols-1)*6)/cols;
        int rows=Math.max(1,Math.min((count+cols-1)/cols,(body+6)/62));
        int ch=Math.min(76,(body-(rows-1)*6)/rows);
        int max=Math.max(0,(count+cols-1)/cols-rows);
        return new CharacterChoiceLayout(new Box(x,y,w,h),grid,details,cols,rows,cw,ch,count,Math.clamp(requested,0,max),max);
    }
    public boolean visible(int index){return index>=scrollRow*columns&&index<Math.min(count,(scrollRow+rows)*columns);}
    public Box card(int index){int visible=index-scrollRow*columns;return new Box(grid.x()+visible%columns*(cellWidth+6),grid.y()+visible/columns*(cellHeight+6),cellWidth,cellHeight);}
    public Box previous(){return new Box(grid.x(),panel.bottom()-32,22,20);}
    public Box next(){return new Box(grid.right()-22,panel.bottom()-32,22,20);}
    public Box guide(){return new Box(details.x(),panel.bottom()-32,(details.w()-6)/2,20);}
    public Box confirm(){return new Box(details.x()+(details.w()+6)/2,panel.bottom()-32,(details.w()-6)/2,20);}
    public Box close(){return new Box(panel.right()-28,panel.y()+7,18,18);}
    public Box skillViewport(){return new Box(details.x()+6,details.y()+44,details.w()-14,Math.max(0,details.h()-50));}
    public static boolean available(String id,String current,Map<String,Boolean> taken){return id!=null&&!id.isBlank()&&(!taken.getOrDefault(id,false)||id.equals(current));}
}
