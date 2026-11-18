package com.myriad.client;

/** Bottom-left dock, shared by actual drawing and the HUD editor. */
public final class SkillBarLayout {
    public static final float DEFAULT_SCALE = 2F;
    public static final int DOCK_VERSION = 2;
    private SkillBarLayout(){}
    public record Layout(float x,float y,float scale,float width,float height){}
    public static float savedScale(int version, float value) {
        return version < DOCK_VERSION && value == 1F ? DEFAULT_SCALE : value;
    }
    public static Layout at(int width,int height,int count,boolean charges,float sizeMultiplier){
        int slots=Math.clamp(count,1,5);
        float fit=Math.min(1F,width/427F),rawWidth=slots*28+(slots-1)*4;
        float right=width*.5F-91-5;
        int below=28+(charges?14:0), fullHeight=below+23;
        float baseline=Math.min(.54F*fit,22F/(below+11));
        float scale=Math.min(baseline*sizeMultiplier,
                Math.min(Math.max(1F,right-4)/rawWidth,Math.max(1F,height-4)/fullHeight));
        float x=Math.max(2,right-rawWidth*scale),y=height-2-below*scale;
        return new Layout(x,y,scale,rawWidth*scale,fullHeight*scale);
    }
}
