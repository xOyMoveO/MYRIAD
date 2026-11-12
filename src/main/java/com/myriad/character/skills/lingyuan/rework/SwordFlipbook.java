package com.myriad.character.skills.lingyuan.rework;

import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Per-instance atlas sampling, not a globally animated sprite shared by every impact. */
public final class SwordFlipbook {
    public static final int COLUMNS=4, ROWS=2, FRAMES=8;
    private SwordFlipbook(){}
    public record Cell(int frame,float u0,float u1,float v0,float v1){}
    public static Cell cell(int frame){
        int f=Math.clamp(frame,0,FRAMES-1),x=f%COLUMNS,y=f/COLUMNS;
        return new Cell(f,x/(float)COLUMNS,(x+1)/(float)COLUMNS,y/(float)ROWS,(y+1)/(float)ROWS);
    }
    public static double ticksPerFrame(int material){
        return switch(material){
            case LINGYUAN_PIXEL_SLASH->2;
            case LINGYUAN_PIXEL_STREAM->1.5;
            case LINGYUAN_PIXEL_SEAL->3;
            case LINGYUAN_PIXEL_FILAMENT->2.5;
            case LINGYUAN_PIXEL_MOTES->3.5;
            default->2;
        };
    }
    /** Whole-tick loop periods stay exact at large world times; age remains fractional. */
    public static Cell loop(int material,long started,float age,int phase){
        double step=ticksPerFrame(material);
        long period=Math.round(step*FRAMES);
        double local=Math.floorMod(started,period)+Math.max(0,age);
        return cell(Math.floorMod((int)Math.floor(local/step)+phase,FRAMES));
    }
    public static Cell burst(double progress){
        return cell((int)Math.floor(Math.clamp(progress,0,1)*FRAMES));
    }
}
