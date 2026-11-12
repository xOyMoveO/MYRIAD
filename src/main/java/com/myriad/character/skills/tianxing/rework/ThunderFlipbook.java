package com.myriad.character.skills.tianxing.rework;

/** Six authored eight-frame pixel families; stable phase across short recipe renewals. */
public final class ThunderFlipbook {
    public static final int CELL=256,COLUMNS=4,ROWS=2,WIDTH=CELL*COLUMNS,HEIGHT=CELL*ROWS,PADDING=24;
    public record Cell(int frame,float u0,float u1,float v0,float v1){}
    private ThunderFlipbook(){}
    public static Cell cell(int frame){
        int f=Math.clamp(frame,0,7),x=f%4,y=f/4;
        return new Cell(f,(x*CELL+.5f)/WIDTH,((x+1)*CELL-.5f)/WIDTH,(y*CELL+.5f)/HEIGHT,((y+1)*CELL-.5f)/HEIGHT);
    }
    public static Cell loop(long started,double age,int offset,boolean slow){
        int step=slow?2:1,period=8*step;
        return cell(Math.floorMod((int)Math.floor((Math.floorMod(started,period)+Math.max(0,age))/step)+offset,8));
    }
    public static Cell once(double progress,int offset){return cell((int)Math.floor(Math.clamp(progress+offset/16.0,0,1)*8));}
}
