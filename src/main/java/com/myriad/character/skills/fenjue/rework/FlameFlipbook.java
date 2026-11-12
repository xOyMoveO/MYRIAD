package com.myriad.character.skills.fenjue.rework;

/** Six independent eight-frame fire families; shared canvas, safe texels, continuous field phase. */
public final class FlameFlipbook {
    public static final int CELL=256,WIDTH=1024,HEIGHT=512,PADDING=24;
    public record Cell(int frame,float u0,float u1,float v0,float v1){}
    private FlameFlipbook(){}
    public static Cell cell(int frame){int f=Math.clamp(frame,0,7),x=f%4,y=f/4;
        return new Cell(f,(x*CELL+.5f)/WIDTH,((x+1)*CELL-.5f)/WIDTH,(y*CELL+.5f)/HEIGHT,((y+1)*CELL-.5f)/HEIGHT);}
    public static Cell loop(long started,double age,int offset){return cell(Math.floorMod((int)Math.floor((Math.floorMod(started,16)+Math.max(0,age))/2)+offset,8));}
    public static Cell once(double progress,int offset){return cell((int)Math.floor(Math.clamp(progress+offset/24.0,0,1)*8));}
}
