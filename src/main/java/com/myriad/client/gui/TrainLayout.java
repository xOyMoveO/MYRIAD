package com.myriad.client.gui;

import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Scrollable training controls; title and close button never scroll. */
public record TrainLayout(Box panel,Box viewport,int scroll,int maximum){
    public static final int ROWS=17,STEP=36;
    public static TrainLayout create(int width,int height,int requested){
        int w=Math.max(1,Math.min(440,width-24)),h=Math.max(1,Math.min(480,height-(height<112?4:16)));
        var p=new Box((width-w)/2,(height-h)/2,w,h);
        int header=h>=144?49:h>=107?38:21;
        int footer=h>=144?32:29;
        var v=new Box(p.x()+12,p.y()+header,Math.max(1,w-24),Math.max(1,h-header-footer));
        int max=Math.max(0,ROWS*STEP-v.h());
        return new TrainLayout(p,v,Math.clamp(requested,0,max),max);
    }
    public Box row(int index){return new Box(viewport.x(),viewport.y()+index*STEP-scroll,viewport.w(),32);}
    public Box actions(int index){var b=row(index);int w=Math.min(142,b.w()/2);return new Box(b.right()-w-7,b.y()+5,w,22);}
    public Box label(int index){var b=row(index);var a=actions(index);return new Box(b.x()+9,b.y()+11,Math.max(1,a.x()-b.x()-18),10);}
}
