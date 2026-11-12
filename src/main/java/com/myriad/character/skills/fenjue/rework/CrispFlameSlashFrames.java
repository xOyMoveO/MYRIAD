package com.myriad.character.skills.fenjue.rework;

/** Native 443/444px replacement phases, centered without scaling on safe 512px canvases. */
public final class CrispFlameSlashFrames {
    public static final int CELL=512,WIDTH=2048,HEIGHT=1024;
    // Shared occupied bounds across all phases are Y=184..408. Never recenter individual animation frames.
    public static final double PIVOT_V=296.0/512;
    private CrispFlameSlashFrames(){}
    public static FlameFlipbook.Cell cell(int frame){
        int f=Math.clamp(frame,0,7),x=f%4,y=f/4;
        return new FlameFlipbook.Cell(f,(x*CELL+.5f)/WIDTH,((x+1)*CELL-.5f)/WIDTH,
            (y*CELL+.5f)/HEIGHT,((y+1)*CELL-.5f)/HEIGHT);
    }
}
