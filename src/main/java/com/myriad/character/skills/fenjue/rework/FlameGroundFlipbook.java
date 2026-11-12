package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** A single authored moving image, tessellated only to follow slabs/terrain; no copied ghost planes. */
public final class FlameGroundFlipbook {
    public static final int FRAMES=8,CELL=512,WIDTH=2048,HEIGHT=1024;
    private FlameGroundFlipbook(){}
    public static int frame(double time){return Math.floorMod((int)Math.floor(time*.5),FRAMES);}
    public static void add(List<Quad> out,Vec3 center,double radius,double time,float alpha,int material,boolean low,int budget){
        if(alpha<=0||radius<=0)return;
        int n=low?4:8,index=frame(time);double half=radius*1.14; // artwork's visible edge, excluding padded cell border
        float u0=(index%4)*.25f+.5f/WIDTH,u1=(index%4+1)*.25f-.5f/WIDTH;
        float v0=(index/4)*.5f+.5f/HEIGHT,v1=(index/4+1)*.5f-.5f/HEIGHT;
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            if(out.size()>=budget)return;
            double xa=-half+2*half*x/n,xb=-half+2*half*(x+1)/n;
            double za=-half+2*half*z/n,zb=-half+2*half*(z+1)/n;
            // Positive Y winding. UVs share exact seams; no per-tile crops or duplicated whole images.
            out.add(new Quad(center.add(xa,0,za),center.add(xa,0,zb),center.add(xb,0,zb),center.add(xb,0,za),
                    0xFFFFFF,Math.clamp(alpha,0,1),material,false,
                    u0+(u1-u0)*(float)z/n,u0+(u1-u0)*(float)(z+1)/n,
                    v0+(v1-v0)*(float)(n-x-1)/n,v0+(v1-v0)*(float)(n-x)/n));
        }
    }
}
