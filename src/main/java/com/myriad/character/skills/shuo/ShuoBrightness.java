package com.myriad.character.skills.shuo;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.List;
/** Shuo-only exposure at mesh creation. One application per layer; no duplicate planes or extra lifetime. */
public final class ShuoBrightness {
    private ShuoBrightness(){}
    public static float opacity(float a){
        if(!Float.isFinite(a)||a<=0)return 0;if(a>=1)return 1;
        return (float)(1-Math.pow(1-a,2.4));
    }
    public static int tint(int rgb){
        int result=0;for(int shift:new int[]{16,8,0})result|=Math.min(255,Math.round(((rgb>>shift)&255)*1.14f))<<shift;
        return result;
    }
    public static Quad apply(Quad q){return new Quad(q.a(),q.b(),q.c(),q.d(),tint(q.color()),opacity(q.alpha()),q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV());}
    public static List<Quad> apply(List<Quad> mesh){return mesh.stream().map(ShuoBrightness::apply).toList();}
}
