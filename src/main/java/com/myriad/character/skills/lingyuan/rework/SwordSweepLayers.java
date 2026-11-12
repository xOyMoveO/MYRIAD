package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Painted, curved, three-dimensional swipe bands follow the real sword pivot, not a flat billboard. */
final class SwordSweepLayers {
    private SwordSweepLayers(){}
    static void append(List<Quad> out,AbilityVfxPayload p,float age,boolean low,Vec3 pivot,Vec3 forward,
                       double angle,double progress,float fade){
        if(progress<=0)return;
        double linger=1-Math.clamp((age-HeavenSwordSkill.CHOP_TICKS)/4.0,0,1);
        if(linger<=0)return;
        double tail=Math.max(Math.toRadians(-18),angle-Math.toRadians(55));
        // Each layer has a different material, radius, offset, phase and hue. Never a full opaque fan.
        ribbon(out,p,age,low,pivot,forward,tail,angle,7.5,15.6,0,LINGYUAN_PIXEL_STREAM,0x8DF7FF,.67*fade*linger,0);
        ribbon(out,p,age,low,pivot,forward,tail,angle,13.9,15.85,.055,LINGYUAN_PIXEL_FILAMENT,0xC8B3FF,.90*fade*linger,3);
        if(!low)ribbon(out,p,age,false,pivot,forward,tail,angle,10.5,14.1,-.065,LINGYUAN_PIXEL_MOTES,0xBE89FF,.48*fade*linger,5);
        // A short bright leading edge makes the direction of travel readable.
        ribbon(out,p,age,low,pivot,forward,Math.max(tail,angle-.035),angle,4.8,15.75,.02,
                LINGYUAN_PIXEL_STREAM,0xFFFFFF,.95*fade*linger,2);
    }
    private static void ribbon(List<Quad> out,AbilityVfxPayload p,float age,boolean low,Vec3 pivot,Vec3 f,
                               double from,double to,double inner,double outer,double offset,int material,int color,double alpha,int phase){
        int steps=low?6:12;Vec3 normal=side(f);var cell=SwordFlipbook.loop(material,p.started(),age,phase);
        for(int i=0;i<steps;i++){
            double t=i/(double)steps,u=(i+1.0)/steps;
            Vec3 a=axis(f,from+(to-from)*t),b=axis(f,from+(to-from)*u),center=pivot.add(normal.scale(offset));
            Vec3 v0=center.add(a.scale(inner)),v1=center.add(a.scale(outer)),v2=center.add(b.scale(outer)),v3=center.add(b.scale(inner));
            float opacity=(float)Math.clamp(alpha*(.15+.85*u),0,1);
            // Stream texture U is its long axis: flow continuously around the arc, not across
            // isolated radial wedges (which otherwise look like disconnected blue flags).
            float uStart=(float)(cell.u0()+(cell.u1()-cell.u0())*t),uEnd=(float)(cell.u0()+(cell.u1()-cell.u0())*u);
            if(out.size()+2>4096)return;
            out.add(new Quad(v0,v3,v2,v1,color,opacity,material,false,uStart,uEnd,cell.v0(),cell.v1()));
            out.add(new Quad(v1,v2,v3,v0,color,opacity,material,true,uStart,uEnd,cell.v0(),cell.v1()));
        }
    }
    private static Vec3 axis(Vec3 f,double angle){return UP.scale(Math.cos(angle)).add(f.scale(Math.sin(angle)));}
}
