package com.myriad.character.skills.yingxiao.rework;

import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Per-effect sprite phase; repeated short packets share time without restarting the artwork. */
public final class NightFlipbook {
    public static final int FRAMES=8,COLUMNS=4,ROWS=2,CELL=128;
    public record Cell(int frame,float u0,float u1,float v0,float v1) { }
    private NightFlipbook() { }
    public static Cell cell(int frame) {
        int f=Math.clamp(frame,0,7),x=f%4,y=f/4;
        return new Cell(f,(x*128+.5f)/512,((x+1)*128-.5f)/512,(y*128+.5f)/256,((y+1)*128-.5f)/256);
    }
    public static int ticksPerFrame(int material) {
        return material==NIGHT_GRAY_SEAL?3:material==NIGHT_GRAY_SMOKE?2:1;
    }
    public static Cell loop(int material,long started,double age,int phase) {
        int step=ticksPerFrame(material),period=step*8;
        double local=Math.floorMod(started,period)+Math.max(0,age);
        return cell(Math.floorMod((int)Math.floor(local/step)+phase,8));
    }
    public static Cell once(double progress) {return cell((int)Math.floor(Math.clamp(progress,0,1)*8));}
}
